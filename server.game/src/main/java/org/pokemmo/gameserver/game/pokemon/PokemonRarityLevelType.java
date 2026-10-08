package org.pokemmo.gameserver.game.pokemon;

public enum PokemonRarityLevelType {
    VERY_COMMON(0),
    COMMON(1),
    //少见
    UNCOMMON(2),
    RARE(3),
    VERY_RARE(4),
    SPECIAL(5),
    //群怪
    HORDE(6),
    LURE(7);

    private byte type;
    PokemonRarityLevelType(int type){
        this.type = (byte)type;
    }
}
