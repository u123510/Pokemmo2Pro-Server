package org.pokemmo.gameserver.game.string;

public enum BattleStringType {
    NULL_TYPE(0),
    LOCAL_STRING_TYPE(1),
    UNKNOWN_TYPE(2),
    UNKNOWN_TYPE_2(3),
    UNKNOWN_TYPE_3(4);
    private byte type;
    private static BattleStringType[] allTypeArray ={NULL_TYPE,LOCAL_STRING_TYPE,UNKNOWN_TYPE,UNKNOWN_TYPE_2,UNKNOWN_TYPE_3};
    BattleStringType(int type) {
        this.type = (byte) type;
    }
    public static BattleStringType getType(byte type) {
        return allTypeArray[type];
    }
    public byte getType() {
        return type;
    }
}
