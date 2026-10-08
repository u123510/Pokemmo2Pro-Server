package org.pokemmo.gameserver.game.script;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class UpdateDexScript extends GameScript{
    private byte dexLevel;
    private short pokemonIndexId;
    private byte mask;
    public UpdateDexScript(byte dexLevel, short pokemonIndexId, byte mask) {
        super(ScriptActionType.UPDATE_DEX);
        this.dexLevel = dexLevel;
        this.pokemonIndexId = pokemonIndexId;
        this.mask = mask;
    }
}
