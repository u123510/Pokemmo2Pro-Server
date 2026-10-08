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
final class CharacterMovementService extends CharacterManagerComponent {
    CharacterMovementService(CharacterManagerState context) {
        super(context);
    }

    public boolean IsOverlapWithCharacter(int x, int y){
        return context.characterData.getPlayerEntity().getX() == x && context.characterData.getPlayerEntity().getY() == y;
    }

    public List<SportType> AutoMove(long entityGameId, boolean isRun, byte regionIndexId, byte mapHeaderIdOrGbaMapGroupId, byte gbaMapId, short tarX, short tarY, byte z) {
        boolean tarPosIsInCurrentMap = context.currentMapDatas[MapConnectionType.NOTHING.getType()].equals(regionIndexId,mapHeaderIdOrGbaMapGroupId,gbaMapId);
        short oldX;
        short oldY;
        //目标点在当前的地图
        if (tarPosIsInCurrentMap)
        {
            boolean isSelf = isSelfEntity(entityGameId);
            //说明是自己，使用记录的位置进行处理
            if (isSelf)
            {
                oldX = context.characterData.getPlayerEntity().getX();
                oldY = context.characterData.getPlayerEntity().getY();
                //获取路径
                return AStarPathFind(oldX, oldY , tarX, tarY, isRun,isSelf);
            }
            else
            {
                NpcEntity npcEntity = context.currentMapDatas[MapConnectionType.NOTHING.getType()].getNpcEntityByGameId(entityGameId);
                if(npcEntity != null) {
                    oldX = npcEntity.getX();
                    oldY = npcEntity.getY();
                    npcEntity.setX(tarX);
                    npcEntity.setY(tarY);
                    return AStarPathFind(oldX, oldY , tarX, tarY, isRun,isSelf);
                }
            }
        }
        return null;
    }

    private List<SportType> AStarPathFind(short x, short y,short tarX, short tarY ,boolean isRun, boolean isSelf) {
        //检查起始点和目标点是否可走
        if(!context.currentMapDatas[MapConnectionType.NOTHING.getType()].checkIsWalkable(x,y)|| !context.currentMapDatas[MapConnectionType.NOTHING.getType()].checkIsWalkable(tarX,tarY)) {
            return null;
        }
        // 方向数组：上、下、左、右
        int[][] directions = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}};
        // 开放列表和关闭列表
        PriorityQueue<PathNode> openList = new PriorityQueue<>();
        boolean[][] closedList = new boolean[context.currentMapDatas[MapConnectionType.NOTHING.getType()].getMapWidth()][context.currentMapDatas[MapConnectionType.NOTHING.getType()].getMapHeight()];
        PathNode[][] nodes = new PathNode[context.currentMapDatas[MapConnectionType.NOTHING.getType()].getMapWidth()][context.currentMapDatas[MapConnectionType.NOTHING.getType()].getMapHeight()];
        // 初始化起点节点
        PathNode startNode = new PathNode(x, y, null, 0, PathNode.getManhattanDistance(x, y, tarX, tarY));
        // 设置起始开放列表节点
        nodes[x][y] = startNode;
        openList.add(startNode);
        while (!openList.isEmpty()) {
            // 获取F值最小的节点
            PathNode currentNode = openList.poll();
            // 如果到达终点，重建路径
            if (currentNode.getX() == tarX && currentNode.getY() == tarY) {
                return reconstructPath(currentNode, isRun);
            }
            // 标记当前节点为已访问
            closedList[currentNode.getX()][currentNode.getY()] = true;
            for (int i = 0; i < 4; i++) {
                int newX = currentNode.getX() + directions[i][0];
                int newY = currentNode.getY() + directions[i][1];
                // 检查坐标是否在地图范围内
                if (newX < 0 || newX >= context.currentMapDatas[MapConnectionType.NOTHING.getType()].getMapWidth() || newY < 0 || newY >= context.currentMapDatas[MapConnectionType.NOTHING.getType()].getMapHeight()) {
                    continue;
                }
                // 检查是否已在关闭列表中或不可走
                if (isSelf) {
                    if (closedList[newX][newY] || !context.currentMapDatas[MapConnectionType.NOTHING.getType()].checkIsWalkable(newX, newY)) {
                        continue;
                    }
                }
                else{
                    //当是npc时，检查是否与自身角色重叠
                    if (closedList[newX][newY] || !context.currentMapDatas[MapConnectionType.NOTHING.getType()].checkIsWalkable(newX, newY) || IsOverlapWithCharacter(newX, newY)) {
                        continue;
                    }
                }
                // 计算新节点的G值
                int newG = currentNode.getG() + 1;
                // 如果新节点不在开放列表中，或有更短的路径
                if (nodes[newX][newY] == null || newG < nodes[newX][newY].getG()) {
                    // 计算H值和F值
                    int newH = PathNode.getManhattanDistance(newX, newY, tarX, tarY);
                    // 创建或更新节点
                    PathNode neighborNode = new PathNode((short)newX, (short)newY, currentNode, newG, newH);
                    nodes[newX][newY] = neighborNode;
                    // 添加到开放列表
                    openList.add(neighborNode);
                }
            }
        }
        return null;
    }

    private List<SportType> reconstructPath(PathNode endNode, boolean isRun) {
        List<SportType> path = new ArrayList<>();
        PathNode currentNode = endNode;
        // 从终点回溯到起点
        while (currentNode.getParent() != null) {
            int dx = currentNode.getX() - currentNode.getParent().getX();
            int dy = currentNode.getY() - currentNode.getParent().getY();
            // 确定移动方向
            SportType sportType;
            if (dx == 0) {
                if (dy == 1) {
                    // 向下移动
                    sportType = isRun ? SportType.RUN_DOWN : SportType.WALK_DOWN;
                } else {
                    // 向上移动
                    sportType = isRun ? SportType.RUN_UP : SportType.WALK_UP;
                }
            } else {
                if (dx == 1) {
                    // 向右移动
                    sportType = isRun ? SportType.RUN_RIGHT : SportType.WALK_RIGHT;
                } else {
                    // 向左移动
                    sportType = isRun ? SportType.RUN_LEFT : SportType.WALK_LEFT;
                }
            }
            // 添加到路径中（注意这里是逆序添加，后面需要反转）
            path.add(sportType);
            currentNode = currentNode.getParent();
        }
        // 反转路径，使其从起点到终点
        Collections.reverse(path);
        return path;
    }

    public void computerNewPosition(short x, short y, byte moveToward, byte z, int mapTopEdge, int mapBottomEdge, int mapLeftEdge, int mapRightEdge) throws InterruptedException {
        int mapConnectOffset = 0;
        MapData currentMap = context.currentMapDatas[0];
        if (currentMap instanceof SinnohMapData
                && ((moveToward == 0 && y + 1 > mapBottomEdge - 1)
                || (moveToward == 1 && y - 1 < mapTopEdge)
                || (moveToward == 2 && x - 1 < mapLeftEdge)
                || (moveToward == 3 && x + 1 > mapRightEdge - 1))) {
            return;
        }
        switch (moveToward)
        {
            case 0:
                if (y + 1 > mapBottomEdge-1)
                {
                    //获取连接地图的偏移
                    mapConnectOffset = context.currentMapDatas[0].getMapConnections().get(MapConnectionType.DOWN).getConnectOffset();
                    //当前地图的玩家池删除该session
                    context.currentMapDatas[0].removePlayerSession(context.characterData.getPlayerEntity().getEntityGameId());
                    //重新设置当前地图为下连接
                    currentMap = context.characterSession.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get().getScriptManager().getRegionDatas()[context.characterData.getPlayerEntity().getRegionIndexId()].getConnectMaps(currentMap).get(MapConnectionType.DOWN);
                    //重置当前的地图
                    context.characterSession.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get().reSetCurrentMaps(currentMap);
                    //新地图添加玩家的session
                    currentMap.addPlayerSession(context.characterData.getPlayerEntity().getEntityGameId(),context.characterSession);
                    //重新设置y
                    y = (short) currentMap.getMapTopEdge();
                    //重新计算x
                    x -= mapConnectOffset;
                    //更新连接地图
                    updateAroundMap(currentMap);
                }
                else {
                    y = (short) (y + 1);
                }
                break;
            case 1:
                if (y - 1 < mapTopEdge)
                {
                    //获取连接地图的偏移
                    mapConnectOffset = currentMap.getMapConnections().get(MapConnectionType.UP).getConnectOffset();
                    //当前地图的玩家池删除该session
                    context.currentMapDatas[0].removePlayerSession(context.characterData.getPlayerEntity().getEntityGameId());
                    //重新设置当前地图为上连接
                    currentMap = context.characterSession.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get().getScriptManager().getRegionDatas()[context.characterData.getPlayerEntity().getRegionIndexId()].getConnectMaps(currentMap).get(MapConnectionType.UP);
                    //重置当前的地图
                    context.characterSession.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get().reSetCurrentMaps(currentMap);
                    //新地图添加玩家的session
                    currentMap.addPlayerSession(context.characterData.getPlayerEntity().getEntityGameId(),context.characterSession);
                    //重新设置y
                    y = (short) (currentMap.getMapBottomEdge() - 1);
                    //重新计算x
                    x -= mapConnectOffset;
                    //更新连接地图
                    updateAroundMap(currentMap);
                }
                else{
                    y = (short) (y - 1);
                }
                break;
            case 2:
                if (x - 1 < mapLeftEdge)
                {
                    //获取连接地图的偏移
                    mapConnectOffset = currentMap.getMapConnections().get(MapConnectionType.LEFT).getConnectOffset();
                    //当前地图的玩家池删除该session
                    context.currentMapDatas[0].removePlayerSession(context.characterData.getPlayerEntity().getEntityGameId());
                    //重新设置当前地图为左连接
                    currentMap = context.characterSession.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get().getScriptManager().getRegionDatas()[context.characterData.getPlayerEntity().getRegionIndexId()].getConnectMaps(currentMap).get(MapConnectionType.LEFT);
                    context.characterSession.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get().reSetCurrentMaps(currentMap);
                    //新地图添加玩家的session
                    currentMap.addPlayerSession(context.characterData.getPlayerEntity().getEntityGameId(),context.characterSession);
                    //重新设置x
                    x = (short) (currentMap.getMapRightEdge() - 1);
                    //重新计算y
                    y -= mapConnectOffset;
                    //更新连接地图
                    updateAroundMap(currentMap);
                }
                else{
                    x = (short) (x - 1);
                }
                break;
            case 3:
                if (x + 1 > mapRightEdge-1)
                {
                    //获取连接地图的偏移
                    mapConnectOffset = currentMap.getMapConnections().get(MapConnectionType.RIGHT).getConnectOffset();
                    //当前地图的玩家池删除该session
                    currentMap.removePlayerSession(context.characterData.getPlayerEntity().getEntityGameId());
                    //重新设置当前地图为右连接
                    currentMap = context.characterSession.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get().getScriptManager().getRegionDatas()[context.characterData.getPlayerEntity().getRegionIndexId()].getConnectMaps(currentMap).get(MapConnectionType.RIGHT);
                    context.characterSession.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get().reSetCurrentMaps(currentMap);
                    //新地图添加玩家的session
                    currentMap.addPlayerSession(context.characterData.getPlayerEntity().getEntityGameId(),context.characterSession);
                    //重新设置x
                    x = (short) currentMap.getMapLeftEdge();
                    //重新计算y
                    y -= mapConnectOffset;
                    //更新连接地图
                    updateAroundMap(currentMap);
                }
                else{
                    x = (short) (x + 1);
                }
                break;
        }
        //更新z轴
        z = currentMap.getBeginZ();
        context.characterData.getPlayerEntity().upDatePos(currentMap.getRegionIndexId(),currentMap.getMapHeaderIdOrGBAmapGroupId(),currentMap.getGbaMapId(),x,y,z,moveToward);
    }

    public String moveClose(int deltaX, int deltaY) {
        if (context.characterData == null || context.characterData.getPlayerEntity() == null) {
            return "The character is not ready for movement.";
        }
        if (context.getBattleManager() != null || context.getInteractManager().getInteractType() != InteractType.NONE
                || context.getTradeSession() != null) {
            return "You cannot move while busy.";
        }
        MapData map = context.currentMapDatas[MapConnectionType.NOTHING.getType()];
        if (map == null) {
            return "The current map is not ready.";
        }
        PlayerEntity player = context.characterData.getPlayerEntity();
        int targetX = player.getX() + deltaX;
        int targetY = player.getY() + deltaY;
        if (map.getPlayerSessionPool().values().stream().anyMatch(session -> {
            CharacterManager manager = session.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get();
            return manager != null && manager.getCharacterData() != null
                    && manager.getCharacterData().getPlayerEntity() != null
                    && manager.getCharacterData().getPlayerEntity().getEntityGameId() != player.getEntityGameId()
                    && manager.getCharacterData().getPlayerEntity().getX() == targetX
                    && manager.getCharacterData().getPlayerEntity().getY() == targetY;
        })) {
            return "The target position is occupied.";
        }
        int toward = deltaY > 0 ? 0 : deltaY < 0 ? 1 : deltaX > 0 ? 3 : 2;
        player.upDateX(targetX);
        player.upDateY(targetY);
        player.upDateToward(toward);
        context.broadcastPlayerPosition();
        return null;
    }

    public String moveTo(int regionId, int mapHeaderIdOrGroupId, int gbaMapId, int x, int y) {
        if (context.characterData == null || context.characterData.getPlayerEntity() == null) {
            return "The character is not ready for movement.";
        }
        if (context.getBattleManager() != null || context.getInteractManager().getInteractType() != InteractType.NONE
                || context.getTradeSession() != null) {
            return "You cannot move while busy.";
        }
        RegionType regionType = RegionType.getByType(regionId);
        if (regionType == null || regionId < 0 || regionId >= context.scriptManager.getRegionDatas().length) {
            return "Unknown region: " + regionId;
        }
        RegionData regionData = context.scriptManager.getRegionDatas()[regionId];
        MapData targetMap = regionData == null
                ? null
                : regionData.getMapData((byte) mapHeaderIdOrGroupId, (byte) gbaMapId);
        if (targetMap == null) {
            return "Unknown map: " + regionId + " " + mapHeaderIdOrGroupId + " " + gbaMapId;
        }
        if (x < Short.MIN_VALUE || x > Short.MAX_VALUE || y < Short.MIN_VALUE || y > Short.MAX_VALUE) {
            return "The target coordinates are out of range.";
        }
        PlayerEntity player = context.characterData.getPlayerEntity();
        player.upDateRegionIndexId(regionId)
                .upDateMapHeaderIdOrGbaMapGroupId(mapHeaderIdOrGroupId)
                .upDateGbaMapId(gbaMapId)
                .upDateX(x)
                .upDateY(y)
                .upDateZ(targetMap.getBeginZ());
        context.handleReLoadMap(targetMap);
        return null;
    }

    private void updateAroundMap(MapData currentMap) throws  InterruptedException {
        RegionData regionData = context.characterSession.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get().getScriptManager().getRegionDatas()[context.characterData.getPlayerEntity().getRegionIndexId()];
        switch (regionData.getRegionType()){
            case KANTO:
            case HOENN:
                //获取当前地图的连接地图
                HashMap<MapConnectionType,MapData> connectMaps = regionData.getConnectMaps(currentMap);
                //获取当前加载的连接地图
                MapData[] aroundKantoregionMaps = context.characterSession.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get().getCurrentMapDatas();
                //获取需要添加的地图
                ArrayList<MapData> needAddMaps = getNeedAddMaps(connectMaps,aroundKantoregionMaps);
                //发包添加需要添加的地图
                for(MapData map : needAddMaps) {
                    sendUpdateAroundMapPacket(map);
                }
                //重新设置当前附近的地图
                reSetCurrentMaps(currentMap);
                break;
        }
    }

    public ArrayList<MapData> getNeedAddMaps(HashMap<MapConnectionType,MapData> connectMaps, MapData[] currentAroundKantoregionMaps){
        ArrayList<MapData> needAddMaps = new ArrayList<>(0);
        for(MapData connectMap : connectMaps.values())
        {
            boolean isExist = false;
            for (int i = 0;i<5;i++)
            {
                if(currentAroundKantoregionMaps[i] != null && connectMap.equals(currentAroundKantoregionMaps[i])){
                    isExist = true;
                    break;
                }
            }
            if(isExist){
                needAddMaps.add(connectMap);
            }
        }
        return needAddMaps;
    }

    private void sendUpdateAroundMapPacket(MapData map) throws InterruptedException {
        switch (map.getRegionIndexId()){
            case 0:
            case 1:
                KantoregionMapData kantoregionMapData = (KantoregionMapData)map;
                context.characterSession.send(new SendLoadMapPacket(false, false, map.getRegionIndexId(), map.getMapHeaderIdOrGBAmapGroupId(), map.getGbaMapId(), 0, map.getMapWidth(), map.getMapHeight(), map.getPrimaryTileset(), map.getSecondaryTileset(), map.getBorderWidth(), map.getBorderHeight(),
                        map.getRomMapIndex() , map.getRomMapHeaderIndex() , map.getMapLightType(), map.getWeatherType(), map.getMapZoneType(), kantoregionMapData.getEncounterType(),map.getBorderTiles(), map.getMapConnections().values().stream().collect(Collectors.toList())));
                //加载npc的数据
                NpcVisibilityService.sendMapSnapshot(context.characterSession, map);
                for (Session aroundSession : map.getPlayerSessionPool().values()) {
                    if (aroundSession == null || aroundSession.equals(context.characterSession) || !aroundSession.isActive()) {
                        continue;
                    }
                    CharacterManager aroundManager = aroundSession.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get();
                    if (aroundManager == null || aroundManager.getCharacterData() == null
                            || aroundManager.getCharacterData().getPlayerEntity() == null
                            || aroundManager.getCharacterData().getChannel() != context.characterData.getChannel()) {
                        continue;
                    }
                    PlayerVisibilityService.sendPlayer(aroundManager, context.characterSession);
                }
        }
    }
}

