package org.pokemmo.gameserver.game.battle.effect;
import lombok.Getter;
import org.pokemmo.gameserver.game.battle.BattleActionEffectType;
import org.pokemmo.gameserver.game.pokemon.PokemonStatType;

@Getter
public class BattleFactionStatChangeEffect extends BasePokemonActionEffect {
    private byte factionIndex;
    private PokemonStatType[] changeStatTypes;
    private byte battleTeamStatChangeTargetLevel;
    private byte[][] factionPokemonsStatChangeValues;//阵营宝可梦的实际能力变化值等级  statChangeFactionAmount statChangeValue
    public BattleFactionStatChangeEffect(boolean isReloadEffectTargetPokemonId,boolean isReloadEffectActorPokemonId,long effectTargetPokemonId,long effectActorPokemonId,byte factionIndex,PokemonStatType[] changeStatTypes, byte battleTeamStatChangeTargetLevel, byte[][] factionPokemonsStatChangeValues){
        super(BattleActionEffectType.FACTION_STAT_CHANGE,isReloadEffectTargetPokemonId, isReloadEffectActorPokemonId, effectTargetPokemonId, effectActorPokemonId);
        this.factionIndex = factionIndex;
        this.changeStatTypes = changeStatTypes;
        this.battleTeamStatChangeTargetLevel = battleTeamStatChangeTargetLevel;
        this.factionPokemonsStatChangeValues = factionPokemonsStatChangeValues;
    }
    public static class Builder{
        private byte factionIndex;
        private PokemonStatType[] changeStatTypes;
        private byte battleTeamStatChangeTargetLevel;
        private byte[][] factionPokemonsStatChangeValues;
        private boolean isReloadEffectTargetPokemonId;
        private boolean isReloadEffectActorPokemonId;
        private long effectTargetPokemonId;
        private long effectActorPokemonId;
        public Builder setFactionIndex(byte factionIndex){
            this.factionIndex = factionIndex;
            return this;
        }
        public Builder setChangeStatTypes(PokemonStatType[] changeStatTypes){
            this.changeStatTypes = changeStatTypes;
            return this;
        }
        public Builder setBattleTeamStatChangeTargetLevel(byte battleTeamStatChangeTargetLevel) {
            this.battleTeamStatChangeTargetLevel = battleTeamStatChangeTargetLevel;
            return this;
        }
        public Builder setFactionPokemonsStatChangeValues(byte[][] factionPokemonsStatChangeValues) {
            this.factionPokemonsStatChangeValues = factionPokemonsStatChangeValues;
            return this;
        }
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
        public BattleFactionStatChangeEffect build(){
            return new BattleFactionStatChangeEffect(isReloadEffectTargetPokemonId, isReloadEffectActorPokemonId, effectTargetPokemonId, effectActorPokemonId, factionIndex, changeStatTypes, battleTeamStatChangeTargetLevel, factionPokemonsStatChangeValues);
        }
    }
}
