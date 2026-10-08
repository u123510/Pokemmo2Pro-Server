package org.pokemmo.gameserver.game.battle.effect;

import lombok.Getter;
import lombok.Setter;
import org.pokemmo.gameserver.game.battle.BattleActionEffectType;

@Getter @Setter
public abstract class BasePokemonActionEffect {
    private BattleActionEffectType effectType;
    private boolean isReloadEffectTargetPokemonId;
    private boolean isReloadEffectActorPokemonId;
    private long effectTargetPokemonId;
    private long effectActorPokemonId;
    public BasePokemonActionEffect(BattleActionEffectType effectType, boolean isReloadEffectTargetPokemonId, boolean isReloadEffectActorPokemonId, long effectTargetPokemonId, long effectActorPokemonId){
        this.effectType = effectType;
        this.isReloadEffectTargetPokemonId = isReloadEffectTargetPokemonId;
        this.isReloadEffectActorPokemonId = isReloadEffectActorPokemonId;
        this.effectTargetPokemonId =effectTargetPokemonId;
        this.effectActorPokemonId = effectActorPokemonId;
    }
}
