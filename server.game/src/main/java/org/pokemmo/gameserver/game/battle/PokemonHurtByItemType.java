package org.pokemmo.gameserver.game.battle;

public enum PokemonHurtByItemType {
    HURT_BY_OTHER_ITEM(0),
    HURT_BY_SELF_ITEM(1);
    private byte type;
    private static final PokemonHurtByItemType[] allTypeArray = values();
    PokemonHurtByItemType(int type) {
        this.type = (byte) type;
    }
    public byte getType() {
        return type;
    }
    public static PokemonHurtByItemType getByType(int type) {
        return allTypeArray[type];
    }
}
