package org.pokemmo.gameserver.game.pokemon;

public enum PokemonNatureFlavorType {
    SPICY(0),
    DRY(1),
    //苦
    BITTER(2),
    SWEET(3),
    SOUR(4);
    static PokemonNatureFlavorType allFlavorTypes[] = {SPICY, DRY, BITTER, SWEET, SOUR};
    private byte type;
    PokemonNatureFlavorType(int type) {
        this.type = (byte) type;
    }
    public byte getType() {
        return type;
    }
    public PokemonNatureFlavorType getByType(byte type) {
        return allFlavorTypes[type];
    }
}
