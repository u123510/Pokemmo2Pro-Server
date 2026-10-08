package org.pokemmo.gameserver.game.character;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.pokemmo.db.jooq.tables.records.ContainerRecord;
import org.pokemmo.db.jooq.tables.records.InventoryRecord;
import org.pokemmo.gameserver.game.account.AccountData;
import org.pokemmo.gameserver.game.badge.BadgeLevelTable;
import org.pokemmo.gameserver.game.battle.BattleManager;
import org.pokemmo.gameserver.game.building.BulidingType;
import org.pokemmo.gameserver.game.entity.NpcEntity;
import org.pokemmo.gameserver.game.entity.NpcVisibilityService;
import org.pokemmo.gameserver.game.entity.PlayerEntity;
import org.pokemmo.gameserver.game.entity.SportType;
import org.pokemmo.gameserver.game.events.*;
import org.pokemmo.gameserver.game.interact.InteractType;
import org.pokemmo.gameserver.game.interact.GameInteractionType;
import org.pokemmo.gameserver.game.map.*;
import org.pokemmo.gameserver.game.player.InteractManager;
import org.pokemmo.gameserver.game.player.ServerHeartBeatThread;
import org.pokemmo.gameserver.game.pokemon.PokemonData;
import org.pokemmo.gameserver.game.pokemon.PokemonManager;
import org.pokemmo.gameserver.game.pool.GameSessionPool;
import org.pokemmo.gameserver.game.region.RegionData;
import org.pokemmo.gameserver.game.region.RegionType;
import org.pokemmo.gameserver.game.script.InteractScript;
import org.pokemmo.gameserver.game.script.Script;
import org.pokemmo.gameserver.game.string.GameLocalFormatString;
import org.pokemmo.gameserver.game.string.GameMassageString;
import org.pokemmo.gameserver.game.trade.TradeManager;
import org.pokemmo.gameserver.game.story.StoryService;
import org.pokemmo.gameserver.protocol.GameProtocol;
import org.pokemmo.gameserver.protocol.packets.s2c.*;
import org.pokemmo.gameserver.script.ScriptManager;
import org.pokemmo.gameserver.services.GameServerService;
import org.pokemmo.gameserver.util.SnowflakeIdGenerator;
import org.server.Session;
import org.server.Packet;
import org.server.services.ServerService;
import org.server.union.chat.ChatMessage;
import org.server.union.chat.ChatType;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
@Slf4j
final class CharacterWorldLoader extends CharacterManagerComponent {
    CharacterWorldLoader(CharacterManagerState context) {
        super(context);
    }

    private void clearCurrentMaps() {
        for(int i = 0;i < context.currentMapDatas.length;i++) {
            context.currentMapDatas[i] = null;
        }
    }

    public void handleLoadGameWorldContext(){
        StoryService.onLogin((CharacterManager) context);
        context.flushOnlineMinutes();
        long characterId = context.characterData.getPlayerEntity().getEntityGameId();
        if(context.partyPokemons[0] != null && context.partyPokemons[0].getCurrentHp()>0){
            //发送队伍外的首位触发的特性
            if(PokemonManager.outBattleAbility.contains(context.partyPokemons[0].getPokemonAbilityIndexId())) {
                context.characterSession.send(new SendCharacterPokemonAbilityPacket(context.partyPokemons[0].getPokemonAbilityIndexId()));
            }
        }
        //发送宝可梦容器信息
        context.characterService.getPokemonContainer().stream()
                .filter(ContainerRecord::getRequired)
                .map(container ->
                        new SendPokemonContainerPacket(container, context.characterService.getCharacterContainerPokemons(characterId, container)))
                .forEach(context.characterSession::send);
        //发送物品容器信息
        InventoryRecord inventory = context.characterService.getInventory();
        context.characterSession.send(new SendInventoryPacket(inventory, context.characterService.getItemsByContainerAndCharacter(characterId, inventory)));
        var mailCounts = context.characterService.getMailCounts(characterId);
        context.characterSession.send(new SendFurntiurePacket(),
                new SendBlackListPacket(),
                new SendFriendListPacket(context.characterService.getFriendList(characterId)),
                new SendGameMailPacket(mailCounts.received(), mailCounts.unread(), mailCounts.sent()),
                new SendLoadDexPacket(context.characterService.getPokemonDexUnlockDataById(characterId))
        );
        //发送关都地区的游戏事件镖旗
        context.characterSession.send(new SendGameSideEventFlagPacket(0,getActiveGameEvents(EventRegionType.KANTO)));
        context.characterSession.send(new SendGameSideEventFlagPacket(1,new ArrayList<>(0)));
        context.characterSession.send(new SendGameSideEventFlagPacket(2,new ArrayList<>(0)));
        context.characterSession.send(new SendGameSideEventFlagPacket(3,new ArrayList<>(0)));
        context.characterSession.send(new SendGameSideEventFlagPacket(4,new ArrayList<>(0)));
        context.characterSession.send(new SendGameSideEventFlagPacket(10,new ArrayList<>(0)));
        context.characterSession.send(new SendGameSideEventFlagPacket(128,new ArrayList<>(0)));
        context.characterSession.send(
                new SendAddGameEventFlagPacket(new short[0])
        );
        context.characterSession.send(
                new SendAcknowledgeCharacterSelectionPacket(context.characterData));// Needs party pokemon container first to render HudGUI
        context.characterSession.send(
                new SendMaxChannelAmountPacket(),
                new SendPvpInfoPacket(),
                new SendBoxInfoPacket(context.characterData.getPcBoxExpansionNumber()),
                new SendChatMessagePacket(ChatMessage.gameNotification("Welcome to PokeMmo! Enjoy your stay"))
        );
        GameMassageString gameMassageString = new GameMassageString();
        GameLocalFormatString notificationFormatString = new GameLocalFormatString(0,context.characterService.getServerNodeName(1));
        gameMassageString.setLocalStringIndexId(6775);
        gameMassageString.setLocalStringList(Collections.singletonList(notificationFormatString));
        gameMassageString.setGameChatString(true);
        gameMassageString.setChatType(ChatType.GAME_NOTIFICATIONS);
        byte[] chatSessionKey = context.serverService.generateSessionKey(context.accountData.getLoginNodeId(),context.accountData.getAccountId(),"chat","login",context.characterSession.getRemoteAddress());
        context.characterSession.send(
                new SendGameMessagePacket(gameMassageString),
                new SendPokemonPvpLevelInfoPacket(),
                new SendInstanceInfoPacket(context.characterService.getInstaceInfo(characterId)),
                new SendUpdateChatServerSessionKeyPacket(chatSessionKey,context.characterService.getOnlineChatNodeServers(context.accountData.getLoginNodeId())),
                new SendLoadBuildingInfoPacket(BulidingType.SECRET_BASE),
                new SendLoadBuildingInfoPacket(BulidingType.HOUSE),
                new SendLegendaryPokemonIsShow(),
                new SendLoadSeasonPacket()
        );
        //获取登录地图数据
        byte regionIndexId = context.characterData.getPlayerEntity().getRegionIndexId();
        byte mapHeaderIdOrGbaMapGroupId = context.characterData.getPlayerEntity().getMapHeaderIdOrGbaMapGroupId();
        byte gbaMapId = context.characterData.getPlayerEntity().getGbaMapId();
        MapData loginMapData = getScriptManager().getRegionDatas()[regionIndexId].getMapData(mapHeaderIdOrGbaMapGroupId,gbaMapId);
        handleReLoadMap(loginMapData);
        context.characterSession.send(
                new SendPvpSeasonPacket(),
                new SendItemBuffPacket()
        );
        //检测交互事件，并对交互事件进行还原
        if(context.interactManager.getInteractType() != InteractType.NONE
                && context.interactManager.getCurrentInteractScript() != null){
            long lastInteractorEntityId = context.interactManager.getLastInteractorEntityId();
            byte interactTimes = context.interactManager.getInteractTimes();
            InteractScript interactScript = context.interactManager.getCurrentInteractScript();
            context.characterSession.send(new SendInteractPacket(lastInteractorEntityId,interactTimes,interactScript));
        }
        if(context.battleManager !=null){
            boolean isSpectate = context.battleManager.isSpectator(context.characterSession);
            context.battleManager.handleBattleBegin(context.characterSession,true,isSpectate);
        }
        // 加载玩家游戏世界上下文
        // START
        // 0x72: SendCharacterPokemonAbilityPacket
        // 0x13: SendGameSideEventFlagPacket
        // 0x40  SendInventoryPacket
        // 0x55: SendFurntiurePacket
        // 0x67: SendBlackListPacket
        // 0x63: SendFriendListPacket
        // 0x98: SendGameMailPacket
        // 0x0A: SendLoadDexPacket
        // 0x29: SendGameSideEventFlagPacket each area
        // 0x1C: SendAddGameEventFlagPacket
        // 0x04: SendAcknowledgeCharacterSelectionPacket
        // 0xF1: SendMaxChannelAmountPacket
        // 0x4A: SendPvpInfoPacket
        // 0x4D: SendPvpSeasonPacket
        // 0x6D: SendBoxInfoPacket
        // 0x09: welcome message IMPLEMENTED
        // 0xF5: SendChatMessagePacket
        // 0x4F: SendPokemonPvpLevelInfoPacket
        // 0xD3: SendInstanceInfoPacket
        // 0xFC: SendIpAddressPacket
        // 0x59: SendLoadBuildingInfoPacket  2 times
        // 0x6E: SendLegendaryPokemonIsShow
        // 0xB9: SendLoadSeasonPacket
        // 0x10: SendLoadMapPacket
        // C2S
        // 0x05 IMPLEMENTED
        // 0x05: IMPLEMENTED
        // 0x90
        // 0x12: 2 times
        // 0xB4 IMPLEMENTED
        // END
    }

    public void reSetCurrentMaps(MapData targetMapData){
        //清空当前地图数据
        clearCurrentMaps();
        //获取当前地图的地区类型
        RegionType regionType = RegionType.getByType(targetMapData.getRegionIndexId());
        //获取当前地图的地区数据
        RegionData regionData = context.scriptManager.getRegionDatas()[regionType.getType()];
        context.currentMapDatas[MapConnectionType.NOTHING.getType()] = targetMapData;
        //对连接的地图进行重新设置
        for(MapConnectionType connectionType : regionData.getConnectMaps(targetMapData).keySet()) {
            context.currentMapDatas[connectionType.getType()] = regionData.getConnectMaps(targetMapData).get(connectionType);
        }
    }

    public CompletableFuture<Boolean> handleReLoadMap(MapData targetMap){
        context.mapLoadFuture = new CompletableFuture<>();
        if (targetMap == null || context.characterData == null || context.characterData.getPlayerEntity() == null) {
            context.mapLoadFuture.complete(false);
            return context.mapLoadFuture;
        }
        MapData[] previousVisibleMaps = context.currentMapDatas.clone();
        MapData previousPrimaryMap = context.currentMapDatas[MapConnectionType.NOTHING.getType()];
        long characterId = context.characterData.getPlayerEntity().getEntityGameId();
        boolean mapChanged = previousPrimaryMap == null || !previousPrimaryMap.equals(targetMap);
        if (mapChanged) {
            for (MapData map : previousVisibleMaps) {
                if (map != null) {
                    map.removePlayerSession(characterId);
                }
            }
        }
        reSetCurrentMaps(targetMap);
        targetMap.addPlayerSession(characterId, context.characterSession);
        if (mapChanged) {
            notifyMapTransition(previousVisibleMaps, previousPrimaryMap);
        }
        //获取当前的地区类型
        RegionType regionType = RegionType.getByType(targetMap.getRegionIndexId());
        //获取当前的地区数据
        RegionData regionData = context.scriptManager.getRegionDatas()[regionType.getType()];
        switch (regionType)
        {
            case KANTO:
            case HOENN:
                KantoregionMapData primaryKantoregionMapData = (KantoregionMapData) targetMap;
                //移除实体确保不重复
                context.characterSession.send(new SendRemoveAroundEntityPacket());
                context.characterSession.send(
                        new SendLoadMapPacket(
                                true, true, primaryKantoregionMapData.getRegionIndexId(),primaryKantoregionMapData.getMapHeaderIdOrGBAmapGroupId(), primaryKantoregionMapData.getGbaMapId(), context.characterData.getChannel(),primaryKantoregionMapData.getMapWidth(),
                                primaryKantoregionMapData.getMapHeight(), primaryKantoregionMapData.getPrimaryTileset(), primaryKantoregionMapData.getSecondaryTileset(), primaryKantoregionMapData.getBorderWidth(), primaryKantoregionMapData.getBorderHeight(),
                                primaryKantoregionMapData.getRomMapIndex(), primaryKantoregionMapData.getRomMapHeaderIndex(), primaryKantoregionMapData.getMapLightType(), primaryKantoregionMapData.getWeatherType(), primaryKantoregionMapData.getMapZoneType(),
                                primaryKantoregionMapData.getEncounterType(),primaryKantoregionMapData.getBorderTiles(), primaryKantoregionMapData.getMapConnections().values().stream().toList())
                );
                for (MapData mapData : regionData.getConnectMaps(primaryKantoregionMapData).values())
                {
                    KantoregionMapData connectMapData = (KantoregionMapData) mapData;
                    context.characterSession.send(new SendLoadMapPacket(
                            false, false, connectMapData.getRegionIndexId(),connectMapData.getMapHeaderIdOrGBAmapGroupId(), connectMapData.getGbaMapId(), context.characterData.getChannel(),connectMapData.getMapWidth(),
                            connectMapData.getMapHeight(), connectMapData.getPrimaryTileset(), connectMapData.getSecondaryTileset(), connectMapData.getBorderWidth(), connectMapData.getBorderHeight(),
                            connectMapData.getRomMapIndex(), connectMapData.getRomMapHeaderIndex(), connectMapData.getMapLightType(), connectMapData.getWeatherType(), connectMapData.getMapZoneType(),
                            connectMapData.getEncounterType(),connectMapData.getBorderTiles(), connectMapData.getMapConnections().values().stream().toList()));
                }
                //发送地图实体信息
                for(MapData mapData : context.currentMapDatas){
                    //初始化NPC实体ID
                    if(mapData != null){
                        mapData.loadArroundEntity(context.characterSession);
                        NpcVisibilityService.sendMapSnapshot(context.characterSession, mapData);
                    }
                }
                break;
            case SINNOH:
                context.characterSession.send(new SendRemoveAroundEntityPacket());
                context.characterSession.send(new SendLoadMapPacket(
                        true,
                        true,
                        targetMap.getRegionIndexId(),
                        targetMap.getMapHeaderIdOrGBAmapGroupId(),
                        targetMap.getGbaMapId(),
                        context.characterData.getChannel(),
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        targetMap.getMapLightType(),
                        targetMap.getWeatherType(),
                        targetMap.getMapZoneType(),
                        MapEncounterType.RANDOM,
                        new Tile2D[0],
                        List.of()
                ));
                targetMap.loadArroundEntity(context.characterSession);
                NpcVisibilityService.sendMapSnapshot(context.characterSession, targetMap);
                break;
        }
        return context.mapLoadFuture.orTimeout(30, TimeUnit.SECONDS) // 添加30秒超时
                .exceptionally(ex -> {
                    // 超时或其他异常处理
                    log.warn("地图加载失败或超时", ex);
                    return false;
                });
    }
}

