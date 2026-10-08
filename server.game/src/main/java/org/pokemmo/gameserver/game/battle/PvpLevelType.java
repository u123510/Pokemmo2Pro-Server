package org.pokemmo.gameserver.game.battle;

public enum PvpLevelType {
    BL(0,5750),
    OU(1,5751),
    UU(2,5752),
    NU(3,5753),
    LC(4,5754),
    PU(5,5755),
    RANDOM(6,5757);
    private byte type;
    private short localStringIndex;
    private static PvpLevelType[] allTypeArray = {BL,OU,UU,NU,LC,PU,RANDOM};
    PvpLevelType(int type, int localStringIndex){
        this.type = (byte) type;
        this.localStringIndex = (short) localStringIndex;
    }

    public byte getType() {
        return type;
    }
}
