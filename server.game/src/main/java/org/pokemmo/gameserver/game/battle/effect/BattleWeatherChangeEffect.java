package org.pokemmo.gameserver.game.battle.effect;

import lombok.Getter;
import org.pokemmo.gameserver.game.battle.BattleActionEffectType;
import org.pokemmo.gameserver.game.battle.BattleWeatherType;
@Getter
public class BattleWeatherChangeEffect extends BasePokemonActionEffect {
    private BattleWeatherType battleWeatherType;

    public BattleWeatherChangeEffect(boolean isReloadEffectTargetPokemonId, boolean isReloadEffectActorPokemonId, long effectTargetPokemonId, long effectActorPokemonId, BattleWeatherType battleWeatherType) {
        super(BattleActionEffectType.WEATHER, isReloadEffectTargetPokemonId, isReloadEffectActorPokemonId, effectTargetPokemonId, effectActorPokemonId);
        this.battleWeatherType = battleWeatherType;
    }

    public static class Builder {
        private boolean isReloadEffectTargetPokemonId;
        private boolean isReloadEffectActorPokemonId;
        private long effectTargetPokemonId;
        private long effectActorPokemonId;
        private BattleWeatherType battleWeatherType;
        public Builder setIsReloadEffectTargetPokemonId(boolean isReloadEffectTargetPokemonId){
            this.isReloadEffectTargetPokemonId = isReloadEffectTargetPokemonId;
            return this;
        }
        public Builder setIsReloadEffectActorPokemonId(boolean isReloadEffectActorPokemonId){
            this.isReloadEffectActorPokemonId = isReloadEffectActorPokemonId;
            return this;
        }
        public Builder setEffectTargetPokemonId(long effectTargetPokemonId){
            this.effectTargetPokemonId = effectTargetPokemonId;
            return this;
        }
        public Builder setEffectActorPokemonId(long effectActorPokemonId){
            this.effectActorPokemonId = effectActorPokemonId;
            return this;
        }
        public Builder setBattleWeatherType(BattleWeatherType battleWeatherType){
            this.battleWeatherType = battleWeatherType;
            return this;
        }
        public BattleWeatherChangeEffect build(){
            return new BattleWeatherChangeEffect(isReloadEffectTargetPokemonId, isReloadEffectActorPokemonId, effectTargetPokemonId, effectActorPokemonId, battleWeatherType);
        }
    }
}
