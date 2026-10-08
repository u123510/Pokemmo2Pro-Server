package org.pokemmo.gameserver.game.platform;

public enum CpuBitType {
    _32(0),
    _64(1),
    _128(2);
    private static final CpuBitType[] allTypeArray = {_32, _64, _128};
    private byte type;
    CpuBitType(int type) {
        this.type = (byte) type;
    }
    public byte getType() {
        return type;
    }
    public static CpuBitType getByType(byte type) {
        return allTypeArray[type];
    }
}
