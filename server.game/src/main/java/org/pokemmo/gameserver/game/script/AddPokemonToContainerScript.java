package org.pokemmo.gameserver.game.script;

import org.pokemmo.db.jooq.tables.records.PokemonRecord;

public class AddPokemonToContainerScript {
    private PokemonRecord pokemon;
    public AddPokemonToContainerScript(PokemonRecord pokemon) {
        this.pokemon = pokemon;
    }
    public PokemonRecord getPokemon() {
        return pokemon;
    }
}
