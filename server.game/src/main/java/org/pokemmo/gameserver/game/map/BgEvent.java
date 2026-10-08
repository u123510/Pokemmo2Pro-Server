package org.pokemmo.gameserver.game.map;

public class BgEvent {
    private short signId;
    private short x;
    private short y;
    private byte z;
    public BgEvent(short signId, short x, short y, byte z) {
        this.signId = signId;
        this.x = x;
        this.y = y;
        this.z = z;
    }
    public short getSignId() {
        return signId;
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
}
