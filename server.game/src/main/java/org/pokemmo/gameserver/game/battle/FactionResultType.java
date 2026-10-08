package org.pokemmo.gameserver.game.battle;

public enum FactionResultType {
    VICTORY(0),
    DEFEAT(1),
    RUN(2),
    CATCH_POKEMON(3),
    IN_BATTLE(4);
    private byte type;
    private static final FactionResultType[] allTypeArray = {VICTORY,DEFEAT,CATCH_POKEMON,IN_BATTLE};
    public static FactionResultType getType(byte type){
        return allTypeArray[type];
    }
    FactionResultType(int type){
        this.type = (byte) type;
    }
    public byte getType(){
        return type;
    }
}
