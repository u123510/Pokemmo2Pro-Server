package org.pokemmo.gameserver.game.battle;

public enum BattleFormType {
    NORMAL(0),
    SAFARI(1),
    NATURE_BATTLE(2),
    SETKO_JUDGED(3),
    CONTEST(4),
    OW_LEGENDARY_BATTLE(6),
    RANDOMS(7),
    INVERSE_TYPINGS(8),
    FUSSY_WEATHER(9),
    SHUFFLE(10),
    BOSS_TRIPLE_BATTLE(11),
    ALPHA_MONSTER_BATTLE(12),
    RED_BOSS(13),
    COOP_RAID(14),
    UNK(15);
    public static final BattleFormType[] allTypeArray = values();
    private byte type;
    BattleFormType(int type){
        this.type = (byte) type;
    }
    public static BattleFormType getByType(int type){
       return allTypeArray[type];
    }
    public byte getType(){
        return type;
    }
}
