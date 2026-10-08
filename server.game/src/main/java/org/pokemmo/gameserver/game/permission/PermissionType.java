package org.pokemmo.gameserver.game.permission;

public enum PermissionType {
    BANNED(1),
    MUTED(2),
    NORMAL(3),
    STAFF(4),
    CM(5),
    MOD(6),
    GM(7),
    SGM(8),
    HGM(9),
    ADM(10);
    private static final PermissionType[] allTypeArray = {BANNED, MUTED, NORMAL, STAFF, CM, MOD, GM, SGM, HGM, ADM};
    private final byte type;
    PermissionType(int type) {
        this.type = (byte) type;
    }
    public byte getType() {
        return type;
    }
    public static PermissionType getByType(int type) {
       return allTypeArray[type - 1];
    }
}
