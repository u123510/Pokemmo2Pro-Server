package org.pokemmo.gameserver.game.pokemon;

public class PokemonLocation {
    private PokemonAppearLoactionType pokemonAppearLoactionType;
    private byte pokemonAppearRegionIndexId;
    private short minLevel;
    private short maxLevel;
    private PokemonRarityLevelType pokemonRarityLevelType;
    public PokemonLocation(PokemonAppearLoactionType pokemonAppearLoactionType, byte pokemonAppearRegionIndexId, short minLevel, short maxLevel, PokemonRarityLevelType pokemonRarityLevelType) {
        this.pokemonAppearLoactionType = pokemonAppearLoactionType;
        this.pokemonAppearRegionIndexId = pokemonAppearRegionIndexId;
        this.minLevel = minLevel;
        this.maxLevel = maxLevel;
        this.pokemonRarityLevelType = pokemonRarityLevelType;
    }
}
