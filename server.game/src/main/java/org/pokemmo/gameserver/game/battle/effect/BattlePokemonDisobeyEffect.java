package org.pokemmo.gameserver.game.battle.effect;

import lombok.Getter;
import org.pokemmo.gameserver.game.battle.BattleActionEffectType;
import org.pokemmo.gameserver.game.battle.PokemonDisobeyType;
@Getter
public class BattlePokemonDisobeyEffect extends BasePokemonActionEffect{
    private PokemonDisobeyType disobeyType;
    public BattlePokemonDisobeyEffect(boolean isReloadEffectTargetPokemonId, boolean isReloadEffectActorPokemonId, long effectTargetPokemonId, long effectActorPokemonId,PokemonDisobeyType disobeyType){
        super(BattleActionEffectType.DISOBEY, isReloadEffectTargetPokemonId, isReloadEffectActorPokemonId, effectTargetPokemonId, effectActorPokemonId);
        this.disobeyType = disobeyType;
    }
    public static class Builder{
        private PokemonDisobeyType disobeyType;
        private boolean isReloadEffectTargetPokemonId;
        private boolean isReloadEffectActorPokemonId;
        private long effectTargetPokemonId;
        private long effectActorPokemonId;
        public Builder setDisobeyType(PokemonDisobeyType disobeyType){
            this.disobeyType = disobeyType;
            return this;
        }
        public Builder setReloadEffectTargetPokemonId(long id) {
            this.isReloadEffectTargetPokemonId = true;
            this.effectTargetPokemonId = id;
            return this;
        }
        public Builder setReloadEffectActorPokemonId(long id) {
            this.isReloadEffectActorPokemonId = true;
            this.effectActorPokemonId = id;
            return this;
        }
        public BattlePokemonDisobeyEffect build()  {
            return new BattlePokemonDisobeyEffect(isReloadEffectTargetPokemonId, isReloadEffectActorPokemonId, effectTargetPokemonId, effectActorPokemonId,disobeyType);
        }
    }

}
