package org.pokemmo.gameserver.game.pokemon;

public class PokemonForm {
    private short formIndexId;
    private short pokemonIndexId;
    public PokemonForm(int formIndexId, int pokemonIndexId) {
        this.formIndexId = (short) formIndexId;
        this.pokemonIndexId = (short) pokemonIndexId;
    }
}
