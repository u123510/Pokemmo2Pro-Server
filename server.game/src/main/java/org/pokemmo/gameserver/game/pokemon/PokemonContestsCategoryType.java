package org.pokemmo.gameserver.game.pokemon;

public enum PokemonContestsCategoryType {
    HANDSOME(0),
    BEAUTY(1),
    CUTE(2),
    CLEVER(3),
    STRONG(4);
    private byte type;
    private static PokemonContestsCategoryType[] allTypeArray = {HANDSOME, BEAUTY, CUTE, CLEVER, STRONG};
    PokemonContestsCategoryType(int type) {
        this.type = (byte) type;
    }
    public byte getType() {
        return type;
    }
    public static PokemonContestsCategoryType getType(int type) {
        return allTypeArray[type];
    }
}
