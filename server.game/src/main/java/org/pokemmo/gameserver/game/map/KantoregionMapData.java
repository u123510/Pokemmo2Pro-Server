package org.pokemmo.gameserver.game.map;
import lombok.Getter;
import org.pokemmo.gameserver.game.entity.NpcEntity;
import org.pokemmo.gameserver.protocol.GameProtocol;
import org.server.Session;
@Getter
public class KantoregionMapData extends MapData {
    private long firstnpcId;
    private MapCoordinateData[][] mapCoordinates;
    private boolean canFlyTo;
    private boolean allowCycling;
    private boolean allowEscaping;
    private boolean allowRunning;
    private short floorNumber;
    private MapEncounterType encounterType;
    public void setMapCoordinateData(MapCoordinateData[][] mapCoordinates) {
        this.mapCoordinates = mapCoordinates;
    }
    public  boolean checkIsInWarpEvent(int x,int y,int z) {
        for(WarpEvent warpEvent : getWarpEvents().values()) {
            if(warpEvent.equals(x,y,z)) {
                return true;
            }
        }
        return false;
    }
    public WarpEvent getWarpEvent(int x,int y,int z) {
        for(WarpEvent warpEvent : getWarpEvents().values()) {
            if(warpEvent.equals(x,y,z)) {
                return warpEvent;
            }
        }
        return null;
    }
    public WarpEvent getStandingWarp(int x, int y, int moveToward) {
        KantoMetaTileBehaviorType behavior = getMetatileBehavior(x, y);
        if (!isStandingWarp(behavior, moveToward)) {
            return null;
        }
        return getWarpEvent(x, y, -10);
    }
    public WarpEvent getEnteredWarp(int x, int y, int moveToward) {
        KantoMetaTileBehaviorType behavior = getMetatileBehavior(x, y);
        if (behavior == KantoMetaTileBehaviorType.MB_WARP_DOOR
                && moveToward != 1) {
            return null;
        }
        if (isStandingWarpBehavior(behavior)) {
            return null;
        }
        return getWarpEvent(x, y, -10);
    }
    public KantoMetaTileBehaviorType getMetatileBehavior(int x, int y) {
        if (mapCoordinates == null
                || x < 0 || y < 0
                || x >= getMapWidth() || y >= getMapHeight()) {
            return null;
        }
        return mapCoordinates[x][y].getMetatileBehaviorType();
    }
    private boolean isStandingWarp(KantoMetaTileBehaviorType behavior, int moveToward) {
        return isStandingWarpBehavior(behavior)
                && standingWarpDirection(behavior) == moveToward;
    }
    private boolean isStandingWarpBehavior(KantoMetaTileBehaviorType behavior) {
        return behavior == KantoMetaTileBehaviorType.MB_UP_RIGHT_STAIR_WARP
                || behavior == KantoMetaTileBehaviorType.MB_UP_LEFT_STAIR_WARP
                || behavior == KantoMetaTileBehaviorType.MB_NORTH_ARROW_WARP
                || behavior == KantoMetaTileBehaviorType.MB_SOUTH_ARROW_WARP
                || behavior == KantoMetaTileBehaviorType.MB_EAST_ARROW_WARP
                || behavior == KantoMetaTileBehaviorType.MB_WEST_ARROW_WARP;
    }
    private int standingWarpDirection(KantoMetaTileBehaviorType behavior) {
        return switch (behavior) {
            case MB_UP_RIGHT_STAIR_WARP, MB_EAST_ARROW_WARP -> 3;
            case MB_UP_LEFT_STAIR_WARP, MB_WEST_ARROW_WARP -> 2;
            case MB_NORTH_ARROW_WARP -> 1;
            case MB_SOUTH_ARROW_WARP -> 0;
            default -> -1;
        };
    }
    public String getNpcNameByEntityGameId(long entityGameId){
        for(NpcEntity npcEntity : getNpcEntityHashMap().values()){
            if(npcEntity.getEntityGameId() == entityGameId){
                return npcEntity.getEntityName();
            }
        }
        return null;
    }
    @Override
    public boolean checkIsWalkable(int x,int y) {
        return mapCoordinates != null
                && x >= 0 && y >= 0
                && x < getMapWidth() && y < getMapHeight()
                && mapCoordinates[x][y].isWalkable();
    }
    //初始化2D瓦片信息
    public KantoregionMapData(int regionIndexId, int mapHeaderIdOrGBAmapGroupId, int gbaMapId, int beginX, int beginY, int beginZ, int mapWidth, int mapHeight, int primaryTileset, int secondaryTileset, int borderWidth, int borderHeight, int romMapIndex, int romMapHeaderIndex, MapLightingType mapLightType, MapWeatherType weatherType, MapZoneType mapZoneType, Tile2D[] borderTiles, boolean canFlyTo, boolean allowCycling, boolean allowEscaping, boolean allowRunning, int floorNumber, MapEncounterType encounterType) {
        super(regionIndexId, mapHeaderIdOrGBAmapGroupId, gbaMapId, beginX, beginY, beginZ,mapWidth, mapHeight, primaryTileset, secondaryTileset, borderWidth, borderHeight, romMapIndex, romMapHeaderIndex, mapLightType, weatherType, mapZoneType);
        super.setBorderTiles(borderTiles);
        this.canFlyTo = canFlyTo;
        this.allowCycling = allowCycling;
        this.allowEscaping = allowEscaping;
        this.allowRunning = allowRunning;
        this.floorNumber = (short) floorNumber;
        this.encounterType = encounterType;
    }
    //对地图npc进行初始化
    @Override
    public synchronized void loadArroundEntity(Session characterSession) {
        for (NpcEntity npcEntity : getNpcEntityHashMap().values()) {
            if (npcEntity.getEntityGameId() <= 0) {
                npcEntity.setEntityGameId(generateNpcId(characterSession));
            }
        }
    }
    public long generateNpcId(Session session) {
        firstnpcId = session.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get().getSnowflakeIdGenerator().nextId();
        return firstnpcId;
    }
    public long nextNpcId() {
        return ++firstnpcId;
    }
}
