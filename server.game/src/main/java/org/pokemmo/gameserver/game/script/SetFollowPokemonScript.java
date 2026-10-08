package org.pokemmo.gameserver.game.script;
import lombok.Getter;
@Getter
public class SetFollowPokemonScript extends GameScript {
     private String interactor;
     private short pokemonIndexId;
     private byte pokemonSex;
     private boolean isShiny;
     private boolean isAlpha;
     private boolean isIngoreFllowError;
     public SetFollowPokemonScript(String interactor, short pokemonIndexId, byte pokemonSex, boolean isShiny, boolean isAlpha, boolean isIngoreFllowError) {
         super(ScriptActionType.SET_FOLLOW_POKEMON);
         this.interactor = interactor;
         this.pokemonIndexId = pokemonIndexId;
         this.pokemonSex = pokemonSex;
         this.isShiny = isShiny;
         this.isAlpha = isAlpha;
         this.isIngoreFllowError = isIngoreFllowError;
     }
}
