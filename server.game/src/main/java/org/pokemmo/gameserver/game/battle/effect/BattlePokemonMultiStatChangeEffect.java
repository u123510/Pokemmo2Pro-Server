package org.pokemmo.gameserver.game.battle.effect;

import lombok.Getter;
import org.pokemmo.gameserver.game.battle.BattleActionEffectType;
import org.pokemmo.gameserver.game.pokemon.PokemonStatType;
@Getter
public class BattlePokemonMultiStatChangeEffect extends BasePokemonActionEffect{
    private PokemonStatType[] changeStatTypeArray;
    private byte actualStatChangeLevel;
    private byte[] actualStatChangeValues;
    public BattlePokemonMultiStatChangeEffect(boolean isReloadEffectTargetPokemonId, boolean isReloadEffectActorPokemonId, long effectTargetPokemonId, long effectActorPokemonId,PokemonStatType[] changeStatTypeArray, byte actualStatChangeLevel, byte[] actualStatChangeValues) {
        super(BattleActionEffectType.MULTI_STAT_CHANGE,isReloadEffectTargetPokemonId, isReloadEffectActorPokemonId, effectTargetPokemonId, effectActorPokemonId);
        this.changeStatTypeArray = changeStatTypeArray;
        this.actualStatChangeLevel = actualStatChangeLevel;
        this.actualStatChangeValues = actualStatChangeValues;
    }
    public static class Builder{
        private PokemonStatType[] changeStatTypeArray;
        private byte actualStatChangeLevel;
        private byte[] actualStatChangeValues;
        private boolean isReloadEffectTargetPokemonId;
        private boolean isReloadEffectActorPokemonId;
        private long effectTargetPokemonId;
        private long effectActorPokemonId;
        public Builder setChangeStatTypeArray(PokemonStatType[] changeStatTypeArray){
            this.changeStatTypeArray = changeStatTypeArray;
            return this;
        }
        public Builder setActualStatChangeLevel(byte actualStatChangeLevel) {
            this.actualStatChangeLevel = actualStatChangeLevel;
            return this;
        }
        public Builder setActualStatChangeValues(byte[] actualStatChangeValues) {
            this.actualStatChangeValues = actualStatChangeValues;
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
        public BattlePokemonMultiStatChangeEffect build(){
            return new BattlePokemonMultiStatChangeEffect(isReloadEffectTargetPokemonId, isReloadEffectActorPokemonId, effectTargetPokemonId, effectActorPokemonId,changeStatTypeArray, actualStatChangeLevel, actualStatChangeValues);
        }
    }

}
