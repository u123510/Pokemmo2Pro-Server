package org.pokemmo.gameserver.game.string;

public enum LoacalStringType {
    MESSAGE(0),
    STORYLINE(1);
    private byte type;
    private static LoacalStringType[] allTypeArray ={MESSAGE,STORYLINE};
    LoacalStringType(int type) {
        this.type = (byte) type;
    }
    public static LoacalStringType getType(byte type) {
        return allTypeArray[type];
    }
    public byte getType() {
        return type;
    }
}
