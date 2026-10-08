package org.pokemmo.gameserver.game.battle.effect;

import org.pokemmo.gameserver.game.battle.BattleActionEffectType;
import org.pokemmo.gameserver.game.battle.PokemonStatChangeType;
import org.pokemmo.gameserver.game.pokemon.PokemonStatType;
import lombok.Getter;

@Getter
public class BattlePokemonStatChangeEffect extends BasePokemonActionEffect {
    private PokemonStatChangeType statChangeType;
    private PokemonStatType statType;
    private byte targetChangeValue;
    private byte actualChangeValue;
    public BattlePokemonStatChangeEffect(boolean isReloadEffectTargetPokemonId,boolean isReloadEffectActorPokemonId,long effectTargetPokemonId,long effectActorPokemonId,PokemonStatChangeType statChangeType,PokemonStatType statType, byte targetChangeValue, byte actualChangeValue){
        super(BattleActionEffectType.STAT_CHANGE, isReloadEffectTargetPokemonId,isReloadEffectActorPokemonId,effectTargetPokemonId,effectActorPokemonId);
        this.statChangeType = statChangeType;
        this.statType = statType;
        this.targetChangeValue = targetChangeValue;
        this.actualChangeValue = actualChangeValue;
    }
    public static class Builder {
        private PokemonStatChangeType statChangeType;
        private PokemonStatType statType;
        private byte targetChangeValue;
        private byte actualChangeValue;
        private boolean isReloadEffectTargetPokemonId;
        private boolean isReloadEffectActorPokemonId;
        private long effectTargetPokemonId;
        private long effectActorPokemonId;
        public Builder setStatChangeType(PokemonStatChangeType statChangeType){
            this.statChangeType = statChangeType;
            return this;
        }
        public Builder setStatType(PokemonStatType statType){
            this.statType = statType;
            return this;
        }
        public Builder setTargetChangeValue(int targetChangeValue){
            this.targetChangeValue = (byte) targetChangeValue;
            return this;
        }
        public Builder setActualChangeValue(int actualChangeValue){
            this.actualChangeValue = (byte) actualChangeValue;
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
        public BattlePokemonStatChangeEffect build(){
            return new BattlePokemonStatChangeEffect(isReloadEffectTargetPokemonId,isReloadEffectActorPokemonId,effectTargetPokemonId,effectActorPokemonId,statChangeType,statType,targetChangeValue,actualChangeValue);
        }
    }
}
