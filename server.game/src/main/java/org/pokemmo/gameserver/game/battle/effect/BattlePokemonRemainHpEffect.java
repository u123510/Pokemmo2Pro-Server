package org.pokemmo.gameserver.game.battle.effect;

import lombok.Getter;
import org.pokemmo.gameserver.game.battle.BattleActionEffectType;

@Getter
public class BattlePokemonRemainHpEffect extends BasePokemonActionEffect {
    private short remainHp;
    public BattlePokemonRemainHpEffect(boolean isReloadEffectTargetPokemonId,boolean isReloadEffectActorPokemonId,long effectTargetPokemonId,long effectActorPokemonId,short remainHp){
        super(BattleActionEffectType.DAMAGE,isReloadEffectTargetPokemonId,isReloadEffectActorPokemonId,effectTargetPokemonId,effectActorPokemonId);
        this.remainHp = remainHp;
    }
    public static class Builder{
        private short remainHp;
        private boolean isReloadEffectTargetPokemonId;
        private boolean isReloadEffectActorPokemonId;
        private long effectTargetPokemonId;
        private long effectActorPokemonId;
        public BattlePokemonRemainHpEffect.Builder setRemainHp(short remainHp) {
            this.remainHp = remainHp;
            return this;
        }
        public BattlePokemonRemainHpEffect.Builder setReloadEffectTargetPokemonId(long effectTargetPokemonId) {
            this.isReloadEffectTargetPokemonId = true;
            this.effectTargetPokemonId = effectTargetPokemonId;
            return this;
        }
        public BattlePokemonRemainHpEffect.Builder setReloadEffectActorPokemonId(long effectActorPokemonId) {
            this.isReloadEffectActorPokemonId = true;
            this.effectActorPokemonId = effectActorPokemonId;
            return this;
        }
        public BattlePokemonRemainHpEffect build(){
            return new BattlePokemonRemainHpEffect(isReloadEffectTargetPokemonId,isReloadEffectActorPokemonId,effectTargetPokemonId,effectActorPokemonId,remainHp);
        }
    }
}
