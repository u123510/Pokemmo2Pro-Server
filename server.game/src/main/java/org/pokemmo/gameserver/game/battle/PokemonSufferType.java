package org.pokemmo.gameserver.game.battle;

public enum PokemonSufferType {
    NO_DAMAGE(0),
    MISS(1),
    CRIT(2),
    FALSE(4),
    NO_EFFECT(8),
    NOT_VERY_EFFECT(16),
    SUPER_EFFECTIVE(32),
    STORE_ENERGY(64),
    PROTECT_SELF(128),
    DAMAGE(512);
    private short type;
    PokemonSufferType(int type){
        this.type = (short) type;
    }
    public short getType(){
        return type;
    }
}
