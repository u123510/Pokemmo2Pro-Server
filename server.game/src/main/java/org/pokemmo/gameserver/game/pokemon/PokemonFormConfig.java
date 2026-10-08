package org.pokemmo.gameserver.game.pokemon;

public class PokemonFormConfig {
    private short form_id;
    private short id;
    private String name;
    public PokemonForm toPokemonForm(){
        return new PokemonForm(form_id, id);
    }
}
