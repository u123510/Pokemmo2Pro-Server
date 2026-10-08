package org.pokemmo.gameserver.game.pokemon;

public enum PokemonStatType {
    HP(0, 100, 10, false),
    ATTACK(1, 0, 5, false),
    DEFENSE(2, 0, 5, false),
    SPEED(3, 0, 5, false),
    SPECIAL_ATTACK(4, 0, 5, false),
    SPECIAL_DEFENSE(5, 0, 5, false),
    ACCURACY(6, 0, 0, true),
    EVASION(7, 0, 0, true);
    private byte type;
    private int levelCorrectedParameter;
    private int correctedParameter;
    private boolean isBattleOnly;
    private static PokemonStatType[] allTypeArray = {HP, ATTACK, DEFENSE, SPEED, SPECIAL_ATTACK, SPECIAL_DEFENSE, ACCURACY, EVASION};
    private static PokemonStatType[] displayTypeArray = {HP, ATTACK, DEFENSE, SPECIAL_ATTACK, SPECIAL_DEFENSE, SPEED, ACCURACY, EVASION};
    PokemonStatType(int type, int levelCorrectedParameter, int correctedParameter, boolean isBattleOnly) {
        this.type = (byte) type;
        this.levelCorrectedParameter= levelCorrectedParameter;
        this.correctedParameter = correctedParameter;
        this.isBattleOnly = isBattleOnly;
    }
    public byte getType() {
        return type;
    }
    public int getCorrectedParameter() {
        return correctedParameter;
    }
    public int getLevelCorrectedParameter() {
        return levelCorrectedParameter;
    }
    public static PokemonStatType getByType(int type){
        return allTypeArray[type];
    }
    public static PokemonStatType getDisplayByType(int type){
        return displayTypeArray[type];
    }
}
