package org.pokemmo.gameserver.game.battle;

public enum SmogonType {

    EvasionSmogon(0, 1, true, true),
    SleepSmogon(1, 1, true, false),
    OHKOSmogon(2, 2, true, false),
    UniqueSpeciesSmogon(3, 3, true, false),
    UniqueItemSmogon(4, 4, false, false),
    UniqueSpecieTreeSmogon(5, 5, false, false),
    SummonPokemonSmogon(6, 6, true, false),
    OriginalCatchSmogon(7, 7, false, true),
    UniqueSummonSmogon(8, 8, false, false),
    NeedPokemonLimitSmogon(9, 9, false, true),
    MinLevelLimitSmogon(10, 10, false, true),
    MaxLevelLimitSmogon(11, 11, false, true);
    public static final SmogonType[] allTypeArray = {EvasionSmogon, SleepSmogon, OHKOSmogon, UniqueSpeciesSmogon, UniqueItemSmogon, UniqueSpecieTreeSmogon, SummonPokemonSmogon, OriginalCatchSmogon, UniqueSummonSmogon, NeedPokemonLimitSmogon,MinLevelLimitSmogon,MaxLevelLimitSmogon};

    private byte type;
    private byte ruleId;
    private boolean isStandardRule;
    private boolean isAffectsDungeons;
    SmogonType(int type, int ruleId, boolean isStandardRule, boolean affectsDungeons) {
        this.type = (byte) type;
        this.ruleId = (byte) ruleId;
        this.isAffectsDungeons = affectsDungeons;
        this.isStandardRule = isStandardRule;
    }
    public byte getType() {
        return type;
    }
    public byte getRuleId() {
        return ruleId;
    }
    public static SmogonType getSmogon(int type){
        return allTypeArray[type];
    }
    public boolean getIsStandardRule() {
        return isStandardRule;
    }

    public boolean getIsAffectsDungeons() {
        return isAffectsDungeons;
    }
}
