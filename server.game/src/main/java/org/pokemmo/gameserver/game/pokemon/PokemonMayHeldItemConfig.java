package org.pokemmo.gameserver.game.pokemon;

public class PokemonMayHeldItemConfig {
    private short id;
    private String name;
    public PokemonMayHeldItem toItemInfo() {
        return  new PokemonMayHeldItem(id);
    }
}
