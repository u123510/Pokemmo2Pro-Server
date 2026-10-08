package org.pokemmo.gameserver.game.map;

import lombok.Getter;
import lombok.Setter;
import org.pokemmo.gameserver.game.entity.NpcEntity;
import org.server.Session;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
@Getter @Setter
public abstract class MapData {
    private byte regionIndexId;
    private byte mapHeaderIdOrGBAmapGroupId;
    private byte gbaMapId;
    private String mapKey;
    private short beginX;
    private short beginY;
    private byte beginZ;
    private int  mapWidth;
    private int  mapHeight;
    private int primaryTileset;
    private int secondaryTileset;
    private byte borderWidth;
    private byte borderHeight;
    private short romMapIndex;
    private byte romMapHeaderIndex;
    private MapLightingType mapLightType;
    private MapWeatherType weatherType;
    private MapZoneType mapZoneType;
    private HashMap<MapConnectionType,MapConnection> mapConnections = new HashMap<>(0);
    private HashMap<String,WarpEvent> warpEvents = new HashMap<>(0);
    private List<CoordinateEvent> coordinateEvents = new ArrayList<>(0);
    private volatile HashMap<String, NpcEntity> npcEntityHashMap = new HashMap<>(0);
    private HashMap<Integer, BgEvent> bgEvents = new HashMap<>(0);
    private Tile2D[] borderTiles;
    //当前地图中的玩家session
    private final ConcurrentHashMap<Long, Session> playerSessionPool = new ConcurrentHashMap<>();
    public MapData(int regionIndexId, int mapHeaderIdOrGBAmapGroupId, int gbaMapId, int beginX, int beginY, int beginZ, int mapWidth, int mapHeight, int primaryTileset, int secondaryTileset, int borderWidth, int borderHeight, int romMapIndex, int romMapHeaderIndex, MapLightingType mapLightType, MapWeatherType weatherType, MapZoneType mapZoneType) {
        this.regionIndexId = (byte) regionIndexId;
        this.mapHeaderIdOrGBAmapGroupId = (byte) mapHeaderIdOrGBAmapGroupId;
        this.gbaMapId = (byte) gbaMapId;
        this.beginX = (short) beginX;
        this.beginY = (short) beginY;
        this.beginZ = (byte) beginZ;
        this.mapWidth = mapWidth;
        this.mapHeight = mapHeight;
        this.primaryTileset = primaryTileset;
        this.secondaryTileset = secondaryTileset;
        this.borderWidth = (byte) borderWidth;
        this.borderHeight = (byte) borderHeight;
        this.romMapIndex = (short) romMapIndex;
        this.romMapHeaderIndex = (byte) romMapHeaderIndex;
        this.mapLightType = mapLightType;
        this.weatherType = weatherType;
        this.mapZoneType = mapZoneType;
    }
    public abstract boolean checkIsWalkable(int x,int y);
    public abstract void loadArroundEntity(Session characterSession);
    public boolean equals(byte regionIndexId,byte mapHeaderIdOrGbaMapGroupId,byte gbaMapId) {
        return this.regionIndexId == regionIndexId && this.mapHeaderIdOrGBAmapGroupId == mapHeaderIdOrGbaMapGroupId && this.gbaMapId == gbaMapId;
    }
    public NpcEntity getNpcEntityByGameId(long npcGameId) {
        for (NpcEntity npcEntity : npcEntityHashMap.values()) {
            if (npcEntity.getEntityGameId() == npcGameId) return npcEntity;
        }
        return null;
    }
    public boolean containsNpcEntity(long npcGameId) {
        return npcEntityHashMap.values().stream().anyMatch(npcEntity -> npcEntity.getEntityGameId() == npcGameId);
    }
    public int getMapLeftEdge() {
        return beginX;
    }
    public int getMapRightEdge() {
        return beginX + mapWidth;
    }
    public int getMapTopEdge() {
        return beginY;
    }
    public int getMapBottomEdge() {
        return beginY + mapHeight;
    }
    public void addMapConnection(MapConnectionType mapConnectionType,MapConnection mapConnection) {
        mapConnections.put(mapConnectionType,mapConnection);
    }
    public boolean equals(MapData mapData) {
        return this.regionIndexId == mapData.getRegionIndexId() && this.mapHeaderIdOrGBAmapGroupId == mapData.getMapHeaderIdOrGBAmapGroupId() && this.gbaMapId == mapData.getGbaMapId();
    }
    public void addWarpEvent(WarpEvent warpEvent) {
        warpEvents.put(warpEvent.getWarpName(), warpEvent);
    }
    public void addCoordinateEvent(CoordinateEvent coordinateEvent) {
        coordinateEvents.add(coordinateEvent);
    }
    public synchronized void addEntity(NpcEntity entity) {
        // Readers may be sending a map snapshot while a GM adds a new NPC.
        HashMap<String, NpcEntity> updated = new HashMap<>(npcEntityHashMap);
        updated.put(entity.getNpcName(), entity);
        npcEntityHashMap = updated;
    }

    public synchronized boolean removeEntity(NpcEntity expected) {
        if (expected == null || npcEntityHashMap.get(expected.getNpcName()) != expected) return false;
        HashMap<String, NpcEntity> updated = new HashMap<>(npcEntityHashMap);
        updated.remove(expected.getNpcName());
        npcEntityHashMap = updated;
        return true;
    }
    public void addBgEvent(BgEvent bgEvent) {
        bgEvents.put((int)bgEvent.getSignId(), bgEvent);
    }
    public Session getPlayerSession(long characterId) {
        return playerSessionPool.get(characterId);
    }
    public ConcurrentHashMap<Long, Session> getPlayerSessionPool(){
        return playerSessionPool;
    }
    public void addPlayerSession(long characterId, Session session) {
        playerSessionPool.put(characterId, session);
    }
    public void removePlayerSession(long characterId) {
        playerSessionPool.remove(characterId);
    }
}
