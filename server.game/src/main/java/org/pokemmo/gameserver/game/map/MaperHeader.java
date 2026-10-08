package org.pokemmo.gameserver.game.map;

public class MaperHeader {
    private byte regionIndexId;
    private byte mapHeaderIdOrGbaMapGroupId;
    private byte gbaMapId;
    public MaperHeader(int regionIndexId, int mapHeaderIdOrGbaMapGroupId, int gbaMapId) {
        this.regionIndexId = (byte) regionIndexId;
        this.mapHeaderIdOrGbaMapGroupId = (byte) mapHeaderIdOrGbaMapGroupId;
        this.gbaMapId = (byte) gbaMapId;
    }
    public byte getRegionIndexId() {
        return regionIndexId;
    }
    public byte getMapHeaderIdOrGbaMapGroupId() {
        return mapHeaderIdOrGbaMapGroupId;
    }
    public byte getGbaMapId() {
        return gbaMapId;
    }
}
