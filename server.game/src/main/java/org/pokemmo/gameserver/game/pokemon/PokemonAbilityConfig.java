package org.pokemmo.gameserver.game.pokemon;

public class PokemonAbilityConfig {
    private short id;
    private String name;
    public PokemonAbility toPokemonAbility() {
        return new PokemonAbility(id);
    }
}
