package org.pokemmo.gameserver.game.battle.effect;

import lombok.Getter;
import org.pokemmo.gameserver.game.battle.BattleActionEffectType;
@Getter
public class BattlePokemonForeWarnEffect extends BasePokemonActionEffect{
    private short moveIndexId;
    public BattlePokemonForeWarnEffect(boolean isReloadEffectTargetPokemonId, boolean isReloadEffectActorPokemonId, long effectTargetPokemonId, long effectActorPokemonId, short moveIndexId) {
        super(BattleActionEffectType.FORE_WARN, isReloadEffectTargetPokemonId, isReloadEffectActorPokemonId, effectTargetPokemonId, effectActorPokemonId);
        this.moveIndexId =moveIndexId;
    }
    public static class Builder{
        private short moveIndexId;
        private boolean isReloadEffectTargetPokemonId;
        private boolean isReloadEffectActorPokemonId;
        private long effectTargetPokemonId;
        private long effectActorPokemonId;
        public Builder setForeWarnMoveIndexId(short moveIndexId) {
            this.moveIndexId = moveIndexId;
            return this;
        }
        public Builder setReloadEffectTargetPokemonId(long effectTargetPokemonId) {
            this.isReloadEffectTargetPokemonId = true;
            this.effectTargetPokemonId = effectTargetPokemonId;
            return this;
        }
        public Builder setReloadEffectActorPokemonId(long effectActorPokemonId) {
            this.isReloadEffectActorPokemonId = true;
            this.effectActorPokemonId = effectActorPokemonId;
            return this;
        }
        public BattlePokemonForeWarnEffect build(){
            return new BattlePokemonForeWarnEffect(isReloadEffectTargetPokemonId, isReloadEffectActorPokemonId, effectTargetPokemonId, effectActorPokemonId, moveIndexId);
        }
    }

}
