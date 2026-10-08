package org.pokemmo.gameserver.game.battle;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.pokemmo.gameserver.game.battle.effect.BasePokemonActionEffect;

@Getter @AllArgsConstructor
public class BattlePokemonCauseAction {
   private long actorPokemonId;
   private long targetPokemonId;
   private BasePokemonActionEffect causeActionEffect;
}
