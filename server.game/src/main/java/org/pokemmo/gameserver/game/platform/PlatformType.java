package org.pokemmo.gameserver.game.platform;

public enum PlatformType {
    WINDOWS(0),
    LINUX(1),
    MAC(2),
    ANDROID(3),
    IOS(4),
    UNK(5);
    private static final PlatformType[] allTypeArray = {WINDOWS, LINUX, MAC, ANDROID, IOS, UNK};
    private byte type;
    PlatformType(int type) {
        this.type = (byte) type;
    }
    public byte getType() {
        return type;
    }
    public static PlatformType getByType(byte type) {
        return allTypeArray[type];
    }
}
