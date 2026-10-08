package org.pokemmo.gameserver.game.pokemon;

public class PokemonEvolution {
    private short evolutionPokemonIndexId;
    private PokemonEvolutionConditionType conditionType;
    private short val;
    public PokemonEvolution(short evolutionPokemonIndexId, PokemonEvolutionConditionType conditionType, short val){
        this.evolutionPokemonIndexId = evolutionPokemonIndexId;
        this.conditionType = conditionType;
        this.val = val;
    }

    public short getEvolutionPokemonIndexId() {
        return evolutionPokemonIndexId;
    }

    public PokemonEvolutionConditionType getConditionType() {
        return conditionType;
    }

    public short getVal() {
        return val;
    }
}
