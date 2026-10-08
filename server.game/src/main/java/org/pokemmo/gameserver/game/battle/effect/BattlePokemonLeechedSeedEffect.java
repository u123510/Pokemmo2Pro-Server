package org.pokemmo.gameserver.game.battle.effect;

import lombok.Getter;
import org.pokemmo.gameserver.game.battle.BattleActionEffectType;
@Getter
public class BattlePokemonLeechedSeedEffect extends BasePokemonActionEffect{
    private boolean isInLeechSeed;
    public BattlePokemonLeechedSeedEffect(boolean isReloadEffectTargetPokemonId, boolean isReloadEffectActorPokemonId, long effectTargetPokemonId, long effectActorPokemonId,boolean isInLeechSeed) {
        super(BattleActionEffectType.LEECH_SEEDED, isReloadEffectTargetPokemonId, isReloadEffectActorPokemonId, effectTargetPokemonId, effectActorPokemonId);
        this.isInLeechSeed = isInLeechSeed;
    }
    public static class Builder {
        private boolean isInLeechSeed;
        private boolean isReloadEffectTargetPokemonId;
        private boolean isReloadEffectActorPokemonId;
        private long effectTargetPokemonId;
        private long effectActorPokemonId;
        public Builder setIsInLeechSeed(boolean isInLeechSeed) {
            this.isInLeechSeed = isInLeechSeed;
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
        public BattlePokemonLeechedSeedEffect build() {
            return new BattlePokemonLeechedSeedEffect(isReloadEffectTargetPokemonId, isReloadEffectActorPokemonId, effectTargetPokemonId, effectActorPokemonId, isInLeechSeed);
        }
    }
}
