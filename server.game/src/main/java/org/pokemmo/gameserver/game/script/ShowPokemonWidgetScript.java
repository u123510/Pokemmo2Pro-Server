package org.pokemmo.gameserver.game.script;

import lombok.Getter;

@Getter
public class ShowPokemonWidgetScript extends GameScript {
    private short pokemonIndexId;
    private byte pokemonFormType;
    private boolean isShiny;
    public ShowPokemonWidgetScript(int pokemonIndexId, int pokemonFormType, boolean isShiny) {
        super(ScriptActionType.SHOW_POKEMON_WIDGET);
        this.pokemonIndexId = (short) pokemonIndexId;
        this.pokemonFormType = (byte) pokemonFormType;
        this.isShiny = isShiny;
    }
    public ShowPokemonWidgetScript(int pokemonIndexId) {
        super(ScriptActionType.SHOW_POKEMON_WIDGET);
        this.pokemonIndexId = (short) pokemonIndexId;
    }
}
