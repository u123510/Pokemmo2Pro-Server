package org.pokemmo.gameserver.game.script;

public class UpDateEntityPositionScript {
    private long entityId;
    private byte regionIndexId;
    private byte GBAmapId;
    private byte mapHeaderIdOrGBAmapGroupId;
    private short x;
    private short y;
    private byte z;
    private byte toward;
    public UpDateEntityPositionScript(long entityId, int regionIndexId, int GBAmapId, int mapHeaderIdOrGBAmapGroupId, int x, int y, int z, int toward) {
        this.entityId = entityId;
        this.regionIndexId = (byte) regionIndexId;
        this.GBAmapId = (byte) GBAmapId;
        this.mapHeaderIdOrGBAmapGroupId = (byte) mapHeaderIdOrGBAmapGroupId;
        this.x = (short) x;
        this.y = (short) y;
        this.z = (byte) z;
        this.toward = (byte) toward;
    }
    public long getEntityId() {
        return entityId;
    }
    public byte getRegionIndexId() {
        return regionIndexId;
    }
    public byte getGBAmapId() {
        return GBAmapId;
    }
    public byte getMapHeaderIdOrGBAmapGroupId() {
        return mapHeaderIdOrGBAmapGroupId;
    }
    public short getX() {
        return x;
    }
    public short getY() {
        return y;
    }
    public byte getZ() {
        return z;
    }
    public byte getToward() {
        return toward;
    }
}
