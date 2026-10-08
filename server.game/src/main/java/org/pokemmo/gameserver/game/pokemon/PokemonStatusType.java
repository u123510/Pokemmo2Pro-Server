package org.pokemmo.gameserver.game.pokemon;

public enum PokemonStatusType {
    NORMAL(0),
    SLEEP(7),
    POISON(8),
    BURN(16),
    FREEZE(32),
    PARALYSIS(64),
    BAD_POISON(-128);
    private byte type;
    private static final PokemonStatusType[] allTypeArray = values();
    PokemonStatusType(int type) {
        this.type = (byte) type;
    }
    public byte getType() {
        return type;
    }
    public static PokemonStatusType getByType(int type) {
        for (PokemonStatusType item : allTypeArray) {
            if (item.type == type) {
                return item;
            }
        }
        return null;
    }
}
