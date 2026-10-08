package org.pokemmo.gameserver.game.battle;

public enum PvpRankLevelType {
    UNRANKED(0,5780),
    BRONZE(1,5781),
    SILVER(2,5782),
    GOLD(3,5783),
    DIAMOND(4,5784),
    TOP_100(5,5785),
    TOP_1(6,5786);
    public static final PvpRankLevelType[] allTypeArray = {UNRANKED,BRONZE,SILVER,GOLD,DIAMOND,TOP_100,TOP_1};
    private byte type;
    private short localStringIndex;
    PvpRankLevelType(int type,int localStringIndex){
        this.type = (byte)type;
        this.localStringIndex = (short) localStringIndex;
    }
    public byte getType(){
        return type;
    }
    public short getLocalStringIndex(){
        return localStringIndex;
    }
    public static PvpRankLevelType getPvpRankLevel(byte type){
        return allTypeArray[type];
    }
    public boolean equals(PvpRankLevelType pvpRankLevel){
        return this.type == pvpRankLevel.type;
    }

}
