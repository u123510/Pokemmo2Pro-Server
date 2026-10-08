package org.pokemmo.gameserver.protocol.packets.c2s;
import org.pokemmo.gameserver.game.character.CharacterData;
import org.pokemmo.gameserver.game.character.CharacterManager;
import org.pokemmo.gameserver.game.entity.PlayerEntity;
import org.pokemmo.gameserver.game.interact.InteractType;
import org.pokemmo.gameserver.game.trade.TradeManager;
import org.pokemmo.gameserver.game.shop.ShopSessions;
import org.pokemmo.gameserver.game.interact.TrainerInteractionService;
import org.pokemmo.gameserver.game.map.*;
import org.pokemmo.gameserver.game.region.RegionType;
import org.pokemmo.gameserver.game.region.RegionData;
import org.pokemmo.gameserver.protocol.GameProtocol;
import org.server.IncomingPacket;
import org.server.Session;
import org.server.bytes.ByteBufEx;
import org.pokemmo.gameserver.protocol.packets.s2c.SendSetEntityPosPacket;

public class MovePacket extends IncomingPacket {
    private CharacterManager characterManager;
    private CharacterData characterData;
    private RegionType region;
    private MapData currentMap;
    private byte regionIndexId;
    private byte mapHeaderIdOrGbaMapGroupId;
    private byte gbaMapId;
    private short x;
    private short y;
    private byte z;
    private int mapLeftEdge;
    private int mapRightEdge;
    private int mapTopEdge;
    private int mapBottomEdge;
    private int moveToward;
    private boolean isBikeJump;//丰原跳跃自行车
    private boolean isRun;
    private boolean isWarped;
    @Override
    public void decode(ByteBufEx buffer) {
        x = buffer.readShortLE();
        y = buffer.readShortLE();
        moveToward = buffer.readByte() & 0xFF;
        if((moveToward & 0x80) != 0) {
            isRun = true;
        }
        if((moveToward & 0x40) != 0) {
            isBikeJump = true;
        }
        moveToward = moveToward & 3;
    }
    @Override
    public void handle(Session session) throws Exception {
        this.characterManager = session.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get();
        ShopSessions.close(session, "角色移动，商店已关闭", true);
        this.characterData = characterManager.getCharacterData();
        PlayerEntity entity = characterData.getPlayerEntity();
        int regionIndex = Byte.toUnsignedInt(entity.getRegionIndexId());
        boolean[] runningShoeFlags = characterData.getRunningShoeFlag();
        boolean isCharacterCanRun = runningShoeFlags != null
                && regionIndex < runningShoeFlags.length
                && runningShoeFlags[regionIndex];
        if(!isCharacterCanRun){
            if(isRun){
                //非法行动进行回拉
                session.send(new SendSetEntityPosPacket(entity));
                return;
            }
        }
        //检查开始坐标是否可走
        if (characterManager.getCurrentMapDatas()[MapConnectionType.NOTHING.getType()] == null) {
            session.send(new SendSetEntityPosPacket(characterData.getPlayerEntity()));
            return;
        }
        boolean isBeginCoordinateWalkable = characterManager.getCurrentMapDatas()[0].checkIsWalkable(x,y);
        if(!isBeginCoordinateWalkable){
            //回拉处理
            session.send(new SendSetEntityPosPacket(characterData.getPlayerEntity()));
            return;
        }
        //如果是对话状态或事件状态,说明需要进行回拉处理
        if(characterManager.getInteractManager().getInteractType() != InteractType.NONE
                || TradeManager.isInTrade(characterManager)
                || characterManager.getBattleManager() != null){
            //回拉处理
            session.send(new SendSetEntityPosPacket(characterData.getPlayerEntity()));
            return;
        }
        //获取当前的Z轴坐标
        z = characterData.getPlayerEntity().getZ();
        //获取当前地图的索引id
        regionIndexId = characterData.getPlayerEntity().getRegionIndexId();
        //获取玩家当前所在的区域
        region = RegionType.getByType(regionIndexId);
        //获取当前地图的地图头id或gba地图组id
        mapHeaderIdOrGbaMapGroupId  = characterData.getPlayerEntity().getMapHeaderIdOrGbaMapGroupId();
        //获取当前地图的gba地图id
        gbaMapId = characterData.getPlayerEntity().getGbaMapId();
        MapData[] previousVisibleMaps = characterManager.getCurrentMapDatas().clone();
        MapData previousPrimaryMap = previousVisibleMaps[MapConnectionType.NOTHING.getType()];
        isWarped = false;
        switch (region){
            case KANTO:
            case HOENN:
                //获取当前地图的地图数据
                currentMap = characterManager.getCurrentMapDatas()[0];
                if (currentMap instanceof KantoregionMapData kantoMap
                        && kantoMap.getStandingWarp(x, y, moveToward) != null) {
                    isWarped = characterManager.handleWarpEvent(x, y, z);
                    if (isWarped) {
                        characterManager.getInteractManager().setMailWidgetOpen(false);
                        return;
                    }
                }
                //获取地图的边界值
                mapLeftEdge = currentMap.getMapLeftEdge();
                mapRightEdge = currentMap.getMapRightEdge();
                mapTopEdge = currentMap.getMapTopEdge();
                mapBottomEdge = currentMap.getMapBottomEdge();
                //计算新的位置
                characterManager.computerNewPosition(x,y,(byte)moveToward,z,mapTopEdge,mapBottomEdge,mapLeftEdge,mapRightEdge);
                break;
            case SINNOH:
                currentMap = characterManager.getCurrentMapDatas()[0];
                if (!(currentMap instanceof NdsMapData ndsMap)) {
                    return;
                }
                int nextX = x + (moveToward == 3 ? 1 : moveToward == 2 ? -1 : 0);
                int nextY = y + (moveToward == 0 ? 1 : moveToward == 1 ? -1 : 0);
                if (!ndsMap.checkIsWalkable(nextX, nextY)) {
                    session.send(new SendSetEntityPosPacket(entity));
                    return;
                }
                entity.upDateX(nextX)
                        .upDateY(nextY)
                        .upDateToward(moveToward);
                break;
            //处理其他地区
        }
        //同步当前类的坐标信息
        x = characterData.getPlayerEntity().getX();
        y = characterData.getPlayerEntity().getY();
        z = characterData.getPlayerEntity().getZ();
        //重新获取当前的地图信息
        currentMap = characterManager.getCurrentMapDatas()[MapConnectionType.NOTHING.getType()];
        if (region == RegionType.SINNOH && currentMap instanceof SinnohMapData sinnohMap) {
            int header = sinnohMap.getHeaderAt(x, y);
            if (header >= 0) {
                RegionData sinnoh = characterManager.getScriptManager()
                        .getRegionDatas()[RegionType.SINNOH.getType()];
                MapData targetMap = sinnoh == null
                        ? null
                        : sinnoh.getMapData((byte) (header >>> 8), (byte) header);
                if (targetMap != null && !targetMap.equals(currentMap)) {
                    characterData.getPlayerEntity()
                            .upDateRegionIndexId(RegionType.SINNOH.getType())
                            .upDateMapHeaderIdOrGbaMapGroupId(targetMap.getMapHeaderIdOrGBAmapGroupId())
                            .upDateGbaMapId(targetMap.getGbaMapId());
                    characterManager.handleReLoadMap(targetMap);
                    currentMap = targetMap;
                }
            }
        }
        //处理传送事件
        if (currentMap instanceof KantoregionMapData kantoMap) {
            isWarped = kantoMap.getEnteredWarp(x, y, moveToward) != null
                    && characterManager.handleWarpEvent(x, y, z);
        } else {
            isWarped = characterManager.handleWarpEvent(x, y, z);
        }
        if (!isWarped) {
            // computerNewPosition may cross a map connection; use the entity's new map identifiers.
            regionIndexId = characterData.getPlayerEntity().getRegionIndexId();
            mapHeaderIdOrGbaMapGroupId = characterData.getPlayerEntity().getMapHeaderIdOrGbaMapGroupId();
            gbaMapId = characterData.getPlayerEntity().getGbaMapId();
            boolean isWalkable = false;
            switch (region) {
                case KANTO:
                case HOENN:
                case SINNOH:
                    isWalkable = currentMap.checkIsWalkable(x,y);
                    break;
                //处理其他地区
            }
            //如果点位无法行走，就不进行任何调整
            if (!isWalkable) {
                return;
            }
            //更新角色位置
            characterData.getPlayerEntity()
                    .upDateRegionIndexId(regionIndexId)
                    .upDateMapHeaderIdOrGbaMapGroupId(mapHeaderIdOrGbaMapGroupId)
                    .upDateGbaMapId(gbaMapId)
                    .upDateX(x)
                    .upDateY(y)
                    .upDateZ(z)
                    .upDateToward(moveToward);
            MapData newPrimaryMap = characterManager.getCurrentMapDatas()[MapConnectionType.NOTHING.getType()];
            if (previousPrimaryMap == null || newPrimaryMap == null || !previousPrimaryMap.equals(newPrimaryMap)) {
                characterManager.notifyMapTransition(previousVisibleMaps, previousPrimaryMap);
            } else {
                characterManager.broadcastPlayerMovement(isRun);
            }
        }
        // A mailbox opened from a PC is no longer valid after a successful movement.
        characterManager.getInteractManager().setMailWidgetOpen(false);
        //事件优先于野外遭遇；剧情格和传送落点不应同时启动野生战斗。
        if (!isWarped) {
            boolean eventTriggered = characterManager.handleEvent(x, y, z);
            if (!eventTriggered) {
                eventTriggered = TrainerInteractionService.onPlayerStep(characterManager);
            }
            if (!eventTriggered) {
                characterManager.handleWildEncounter(previousPrimaryMap, currentMap, x, y, z);
            }
        }
    }
}
