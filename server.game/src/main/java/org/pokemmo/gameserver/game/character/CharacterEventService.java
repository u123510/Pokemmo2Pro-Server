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
import org.pokemmo.gameserver.game.story.StoryService;
import org.pokemmo.gameserver.game.string.GameLocalFormatString;
import org.pokemmo.gameserver.game.string.GameMassageString;
import org.pokemmo.gameserver.game.trade.TradeManager;
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
final class CharacterEventService extends CharacterManagerComponent {
    CharacterEventService(CharacterManagerState context) {
        super(context);
    }

    public void handleTradeRequest(String targetPlayerName) {
        if (context.characterData == null || context.characterData.getPlayerEntity() == null
                || context.characterSession == null || !context.characterSession.isActive()) {
            log.warn("交易请求缺少有效的角色会话: {}", targetPlayerName);
            return;
        }
        String requesterName = context.characterData.getPlayerEntity().getPlayerName();
        if (targetPlayerName == null || targetPlayerName.isBlank()
                || targetPlayerName.length() > 20
                || targetPlayerName.chars().anyMatch(Character::isISOControl)) {
            log.warn("角色 {} 发起交易请求时目标名称非法: {}", requesterName, targetPlayerName);
            return;
        }
        if (requesterName != null && requesterName.equalsIgnoreCase(targetPlayerName)) {
            log.warn("角色 {} 不能向自己发起交易请求", requesterName);
            return;
        }
        if (context.interactManager.getInteractType() != InteractType.NONE || context.battleManager != null) {
            log.warn("角色 {} 当前正忙，忽略交易请求", requesterName);
            return;
        }

        MapData currentMap = context.currentMapDatas[MapConnectionType.NOTHING.getType()];
        if (currentMap == null) {
            log.warn("角色 {} 尚未进入地图，忽略交易请求", requesterName);
            return;
        }

        Session targetSession = null;
        CharacterManager targetCharacterManager = null;
        for (Session candidateSession : currentMap.getPlayerSessionPool().values()) {
            if (candidateSession == null || candidateSession == context.characterSession || !candidateSession.isActive()) {
                continue;
            }
            CharacterManager candidateManager = candidateSession.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get();
            if (candidateManager == null || candidateManager.getCharacterData() == null
                    || candidateManager.getCharacterData().getPlayerEntity() == null) {
                continue;
            }
            CharacterData candidateData = candidateManager.getCharacterData();
            String candidateName = candidateData.getPlayerEntity().getPlayerName();
            if (candidateName == null || !candidateName.equalsIgnoreCase(targetPlayerName)
                    || candidateData.getChannel() != context.characterData.getChannel()
                    || candidateData.getPlayerEntity().getRegionIndexId() != context.characterData.getPlayerEntity().getRegionIndexId()
                    || candidateData.getPlayerEntity().getMapHeaderIdOrGbaMapGroupId() != context.characterData.getPlayerEntity().getMapHeaderIdOrGbaMapGroupId()
                    || candidateData.getPlayerEntity().getGbaMapId() != context.characterData.getPlayerEntity().getGbaMapId()) {
                continue;
            }
            targetSession = candidateSession;
            targetCharacterManager = candidateManager;
            break;
        }
        if (targetSession == null || targetCharacterManager == null) {
            log.warn("角色 {} 未在当前地图找到交易目标 {}", requesterName, targetPlayerName);
            return;
        }
        if (targetCharacterManager.getInteractManager().getInteractType() != InteractType.NONE
                || targetCharacterManager.getBattleManager() != null) {
            log.warn("交易目标 {} 当前正忙", targetPlayerName);
            return;
        }

        if (!TradeManager.registerRequest((CharacterManager) context, targetCharacterManager)) {
            log.warn("角色 {} 与 {} 已存在交易请求或交易会话", requesterName, targetPlayerName);
            return;
        }
        InteractScript tradeRequest = new InteractScript(
                "Scene",
                GameInteractionType.REQUEST_TRADE,
                0,
                0,
                0
        );
        tradeRequest.setInteractPlayerName(requesterName);
        targetSession.send(new SendInteractPacket(
                -1,
                targetCharacterManager.getInteractManager().getInteractTimes(),
                tradeRequest
        ));
    }

    public boolean handleWarpEvent(short currentX,short currentY,byte currentZ){
        MapData currentMap = context.currentMapDatas[MapConnectionType.NOTHING.getType()];
        if (currentMap == null) {
            return false;
        }
        WarpEvent matchedWarp = null;
        MapData destinationMap = null;
        for (WarpEvent warpEvent : currentMap.getWarpEvents().values()){
            //当检测到换地图事件的坐标与当前坐标相等时
            // GBA warp elevation is not the same as the runtime movement Z layer.
            if(warpEvent.equals(currentX,currentY,-10)){
                RegionType destinationRegionType = RegionType.getByType(
                        Byte.toUnsignedInt(warpEvent.getDestRegionIndexId())
                );
                if (destinationRegionType == null
                        || destinationRegionType.getType() >= context.scriptManager.getRegionDatas().length) {
                    log.warn("传送目标地区无效: region={}, bank={}, map={}",
                            Byte.toUnsignedInt(warpEvent.getDestRegionIndexId()),
                            Byte.toUnsignedInt(warpEvent.getDestMapHeaderIdOrGbaMapGroupId()),
                            Byte.toUnsignedInt(warpEvent.getDestGbaMapId()));
                    return false;
                }
                RegionData regionData = context.scriptManager.getRegionDatas()[destinationRegionType.getType()];
                destinationMap = regionData == null
                        ? null
                        : regionData.getMapData(
                                warpEvent.getDestMapHeaderIdOrGbaMapGroupId(),
                                warpEvent.getDestGbaMapId()
                        );
                if (destinationMap == null) {
                    log.warn("传送目标地图不存在: region={}, bank={}, map={}",
                            Byte.toUnsignedInt(warpEvent.getDestRegionIndexId()),
                            Byte.toUnsignedInt(warpEvent.getDestMapHeaderIdOrGbaMapGroupId()),
                            Byte.toUnsignedInt(warpEvent.getDestGbaMapId()));
                    return false;
                }
                matchedWarp = warpEvent;
                break;
            }
        }
        if (matchedWarp == null) {
            return false;
        }

        int targetX = matchedWarp.getTargetX();
        int targetY = matchedWarp.getTargetY();
        int targetToward = matchedWarp.getTargetToward();
        if (destinationMap instanceof KantoregionMapData destinationKantoMap
                && destinationKantoMap.getMetatileBehavior(targetX, targetY)
                == KantoMetaTileBehaviorType.MB_WARP_DOOR
                && destinationKantoMap.checkIsWalkable(targetX, targetY + 1)) {
            targetY++;
            targetToward = 0;
        }

        //确认目标地图存在后再更新角色状态，避免断链传送把角色留在无效地图。
        context.characterData.getPlayerEntity().upDateRegionIndexId(matchedWarp.getDestRegionIndexId())
                .upDateMapHeaderIdOrGbaMapGroupId(matchedWarp.getDestMapHeaderIdOrGbaMapGroupId())
                .upDateGbaMapId(matchedWarp.getDestGbaMapId())
                .upDateX(targetX)
                .upDateY(targetY)
                .upDateZ(matchedWarp.getTargetZ());
        if(targetToward != -1){
            context.characterData.getPlayerEntity().upDateToward(targetToward);
        }
        //由 handleReLoadMap 统一维护旧地图移除、新地图加入和玩家广播。
        handleReLoadMap(destinationMap);
        return true;
    }

    public boolean handleEvent(short x,short y,byte z) throws InterruptedException {
        return StoryService.onStep((CharacterManager) context, x, y);
    }







    public List<GameEvent> getActiveGameEvents(EventRegionType eventRegionType){
        List<GameEvent> activeGameEvents = new ArrayList<>();
        switch (eventRegionType) {
            case KANTO:
                short badgeFlag = context.characterData.getBadgeFlag()[eventRegionType.getType()];
                for(GameEventType gameEventType : GameEventType.kantoBadgeArray){
                    if((badgeFlag & (1 << gameEventType.getIndex())) != 0){
                        activeGameEvents.add(new GameEvent(gameEventType,1));
                    }
                }
                long cityCanFlyFlag = context.characterData.getCityCanFlyFlag()[eventRegionType.getType()];
                for(GameEventType gameEventType : GameEventType.kantoCityCanFlyArray){
                    if((cityCanFlyFlag & (1L << gameEventType.getIndex())) != 0){
                        activeGameEvents.add(new GameEvent(gameEventType,1));
                    }
                }
                boolean runningShoeFlag = context.characterData.getRunningShoeFlag()[eventRegionType.getType()];
                if(runningShoeFlag){
                    activeGameEvents.add(new GameEvent(GameEventType.running_shoe_status,1));
                }
                short storyLineFlag = context.characterData.getStoryLineFlag()[eventRegionType.getType()];
                for(GameEventType gameEventType : GameEventType.kantoStoryLineArray) {
                    if ((storyLineFlag & (1 << gameEventType.getIndex())) != 0) {
                        activeGameEvents.add(new GameEvent(gameEventType, 1));
                    }
                }
                boolean championFlag = context.characterData.getChampionFlag()[eventRegionType.getType()];
                if(championFlag){
                    activeGameEvents.add(new GameEvent(GameEventType.the_hall_of_fame_status,1));
                }
                break;
            case HOENN:
            case UNOVA:
            case SINNOH:

                break;
        }
        return activeGameEvents;
    }


}

