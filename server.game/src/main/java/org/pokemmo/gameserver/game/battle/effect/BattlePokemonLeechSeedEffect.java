package org.pokemmo.gameserver.game.battle.effect;

import lombok.Getter;
import org.pokemmo.gameserver.game.battle.BattleActionEffectType;
@Getter
//此效果无目标，行动宝可梦与目标宝可梦都应为被寄生的宝可梦
public class BattlePokemonLeechSeedEffect extends BasePokemonActionEffect {
    private short leechedPokemonReaminHp;
    private byte leecherPokemonDebutSelector;
    private short leecherPokemonRemianHp;
    public BattlePokemonLeechSeedEffect(BattleActionEffectType effectType, boolean isReloadEffectTargetPokemonId, boolean isReloadEffectActorPokemonId, long effectTargetPokemonId, long effectActorPokemonId) {
        super(effectType, isReloadEffectTargetPokemonId, isReloadEffectActorPokemonId, effectTargetPokemonId, effectActorPokemonId);
    }
    public static class Builder {
        private short leechedPokemonReaminHp;
        private byte leecherPokemonDebutSelector;
        private short leecherPokemonRemianHp;
        private boolean isReloadEffectTargetPokemonId;
        private boolean isReloadEffectActorPokemonId;
        private long effectTargetPokemonId;
        private long effectActorPokemonId;
        public Builder setLeechedPokemonRemainHp(short leechedPokemonReaminHp) {
            this.leechedPokemonReaminHp = leechedPokemonReaminHp;
            return this;
        }
        public Builder setLeecherPokemonDebutSelector(byte leecherPokemonDebutSelector) {
            this.leecherPokemonDebutSelector = leecherPokemonDebutSelector;
            return this;
        }
        public Builder setLeecherPokemonRemainHp(short leecherPokemonRemianHp){
            this.leecherPokemonRemianHp = leecherPokemonRemianHp;
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
    }

}
