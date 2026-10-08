package org.pokemmo.gameserver.game.map;

public class MapHandler {
    private MapData currentMap;
    private byte regionIndexId;
    private byte mapHeaderIdOrGbaMapGroupId;
    private byte gbaMapId;
    private short x;
    private short y;
    private byte z;
    private byte moveToward;
    public MapHandler(MapData currentMap, int regionIndexId,int mapHeaderIdOrGbaMapGroupId, int gbaMapId, int x,int y, int z, int moveToward) {
        this.currentMap = currentMap;
        this.regionIndexId = (byte) regionIndexId;
        this.mapHeaderIdOrGbaMapGroupId = (byte) mapHeaderIdOrGbaMapGroupId;
        this.gbaMapId = (byte) gbaMapId;
        this.x = (short) x;
        this.y = (short) y;
        this.z = (byte) z;
        this.moveToward = (byte) moveToward;
    }
}
