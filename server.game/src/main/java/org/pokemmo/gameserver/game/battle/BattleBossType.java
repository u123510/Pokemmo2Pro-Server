package org.pokemmo.gameserver.game.battle;

public enum BattleBossType {

    NONE(0,false),
    RAID_BOSS( 1,false),
    MINIBOSS( 2,false),
    TRIPLE_BATTLE_BOSS( 3,false),
    ALPHA_MINIBOSS( 4,true),
    UNK( 5,true),
    UNK2( 6,true),
    UNK3( 7,true),
    UNK4( 8,true),
    UNK5( 9,true),
    UNK6( 10,true);
    byte type;
    boolean isAlpha;
    public static final BattleBossType[] allTypeArray = values();
    BattleBossType(int type, boolean isAlpha) {
        this.type = (byte) type;
        this.isAlpha = isAlpha;
    }
    public byte getType(){
        return type;
    }
    public boolean isAlpha(){
        return isAlpha;
    }
    public static BattleBossType getBattleBossType(byte type){
        return allTypeArray[type];
    }
}
