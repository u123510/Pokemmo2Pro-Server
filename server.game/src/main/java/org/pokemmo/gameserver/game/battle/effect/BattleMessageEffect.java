package org.pokemmo.gameserver.game.battle.effect;

import lombok.Getter;
import org.pokemmo.gameserver.game.battle.BattleActionEffectType;

@Getter
public class BattleMessageEffect extends BasePokemonActionEffect{
    private byte stringGroup;
    private byte stringBlockId;
    private short stringTableId;
    private short stringEntryId;
    private int stringIndexId;
    public BattleMessageEffect(boolean isReloadEffectTargetPokemonId, boolean isReloadEffectActorPokemonId, long effectTargetPokemonId, long effectActorPokemonId, byte stringGroup, byte stringBlockId, short stringTableId, short stringEntryId, int stringIndexId){
        super(BattleActionEffectType.BATTLE_MESSAGE, isReloadEffectTargetPokemonId, isReloadEffectActorPokemonId, effectTargetPokemonId, effectActorPokemonId);
        this.stringGroup = stringGroup;
        this.stringBlockId = stringBlockId;
        this.stringTableId = stringTableId;
        this.stringEntryId = stringEntryId;
        this.stringIndexId = stringIndexId;
    }
    public static class Builder{
        private byte stringGroup;
        private byte stringBlockId;
        private short stringTableId;
        private short stringEntryId;
        private int stringIndexId;
        private boolean isReloadEffectTargetPokemonId;
        private boolean isReloadEffectActorPokemonId;
        private long effectTargetPokemonId;
        private long effectActorPokemonId;
        public Builder setStringGroup(byte stringGroup){
            this.stringGroup = stringGroup;
            return this;
        }
        public Builder setStringBlockId(byte stringBlockId){
            this.stringBlockId = stringBlockId;
            return this;
        }
        public Builder setStringTableId(short stringTableId){
            this.stringTableId = stringTableId;
            return this;
        }
        public Builder setStringEntryId(short stringEntryId){
            this.stringEntryId = stringEntryId;
            return this;
        }
        public Builder setStringIndexId(int stringIndexId){
            this.stringIndexId = stringIndexId;
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
        public BattleMessageEffect build(){
            return new BattleMessageEffect(isReloadEffectTargetPokemonId, isReloadEffectActorPokemonId, effectTargetPokemonId, effectActorPokemonId, stringGroup, stringBlockId, stringTableId, stringEntryId, stringIndexId);
        }
    }

}
