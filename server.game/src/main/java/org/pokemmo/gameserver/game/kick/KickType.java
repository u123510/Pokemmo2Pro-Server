package org.pokemmo.gameserver.game.kick;

public enum KickType {
    NONE(0),
    DOUBLE_LOGIN(1),
    AFK_KICK(2),
    OUTDATED_CLIENT(3),
    OTHER_ERROR(4),
    MISSING_REGION(5),
    SERVER_SHUTDOWN(6),
    FORCE(7),
    RECONNECT_INVALID(8),
    TAMPERING(9),
    PLATFORM(10),
    VM(11);
    private byte type;
    private static final KickType[] allTypeArray = values();
    KickType(int type) {
        this.type = (byte) type;
    }
    public byte getType() {
        return type;
    }
    public static KickType getByType(int type) {
        return allTypeArray[type];
    }
}
