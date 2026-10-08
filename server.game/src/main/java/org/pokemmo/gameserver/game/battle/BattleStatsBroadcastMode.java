package org.pokemmo.gameserver.game.battle;

public enum BattleStatsBroadcastMode {
    BASE_STAT_AND_CURRENT_BATTLE_STAT(0,2),
    BASE_STATS_WITH_BATTLE_MOD(1,1),
    RANDOMS_SPEED_MINMAX(2,3);
    private static final BattleStatsBroadcastMode[] allTypeArray = new BattleStatsBroadcastMode[]{BASE_STAT_AND_CURRENT_BATTLE_STAT, BASE_STATS_WITH_BATTLE_MOD, RANDOMS_SPEED_MINMAX};
    private byte type;
    private byte mappingValue;

    BattleStatsBroadcastMode(int type, int mappingValue) {
        this.type = (byte) type;
        this.mappingValue = (byte) mappingValue;
    }
    public static BattleStatsBroadcastMode getBattleStatsBroadcastMode(byte index){
        return allTypeArray[index];
    }
    public int getMappingValue(){
        return mappingValue;
    }
    public byte getType() {
        return type;
    }
}
