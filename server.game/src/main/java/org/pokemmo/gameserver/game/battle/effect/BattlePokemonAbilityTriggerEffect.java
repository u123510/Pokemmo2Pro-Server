package org.pokemmo.gameserver.game.battle.effect;

import lombok.Getter;
import org.pokemmo.gameserver.game.battle.BattleActionEffectType;
@Getter
public class BattlePokemonAbilityTriggerEffect extends BasePokemonActionEffect {
    private short actorPokemonAbilityIndexId;
    private long showAbilityPokemonId;
    private boolean abilityHasTriggerPokemon; // 1
    private long abilityTargetPokemonId;
    private boolean isShowMove;// 2
    private short showMoveIndexId;
    private boolean isCopyEnemyAbility;// 4
    private short targetPokemonAbilityIndexId;
    private boolean isEatItem;//8
    private short eatItemIndexId;

    public BattlePokemonAbilityTriggerEffect(boolean isReloadEffectTargetPokemonId, boolean isReloadEffectActorPokemonId, long effectTargetPokemonId, long effectActorPokemonId,
                                             short actorPokemonAbilityIndexId, long showAbilityPokemonId,
                                             boolean abilityHasTriggerPokemon, long abilityTargetPokemonId,
                                             boolean isShowMove,short showMoveIndexId,
                                             boolean isCopyEnemyAbility,short targetPokemonAbilityIndexId,
                                             boolean isEatItem,short eatItemIndexId
    ) {
        super(BattleActionEffectType.ABILITY_TRIGGER,isReloadEffectTargetPokemonId,isReloadEffectActorPokemonId,effectTargetPokemonId,effectActorPokemonId);
        this.actorPokemonAbilityIndexId = actorPokemonAbilityIndexId;
        this.showAbilityPokemonId = showAbilityPokemonId;
        this.abilityHasTriggerPokemon = abilityHasTriggerPokemon;
        this.abilityTargetPokemonId = abilityTargetPokemonId;
        this.isShowMove = isShowMove;
        this.showMoveIndexId = showMoveIndexId;
        this.isCopyEnemyAbility =isCopyEnemyAbility;
        this.targetPokemonAbilityIndexId =targetPokemonAbilityIndexId;
        this.isEatItem = isEatItem;
        this.eatItemIndexId = eatItemIndexId;
    }
    public static class Builder {
        private short actorPokemonAbilityIndexId;
        private long showAbilityPokemonId;
        private boolean abilityHasTriggerPokemon; // 1
        private long abilityTargetPokemonId;
        private boolean isShowMove;// 2
        private short showMoveIndexId;
        private boolean isCopyEnemyAbility;// 4
        private short targetPokemonAbilityIndexId;
        private boolean isEatItem;//8
        private short eatItemIndexId;
        private boolean isReloadEffectTargetPokemonId;
        private boolean isReloadEffectActorPokemonId;
        private long effectTargetPokemonId;
        private long effectActorPokemonId;
        public Builder setActorPokemonAbilityIndexId(short abilityIndexId) {
            this.actorPokemonAbilityIndexId = abilityIndexId;
            return this;
        }
        public Builder setShowAbilityPokemonId(long id) {
            this.showAbilityPokemonId = id;
            return this;
        }
        public Builder setAbilityTargetPokemonId(long abilityTargetPokemonId) {
            this.abilityHasTriggerPokemon = true;
            this.abilityTargetPokemonId = abilityTargetPokemonId;
            return this;
        }
        public Builder setShowMove(short moveIndexId) {
            this.isShowMove = true;
            this.showMoveIndexId = moveIndexId;
            return this;
        }
        public Builder setCopyEnemyAbility(short targetPokemonAbilityIndexId) {
            this.isCopyEnemyAbility = true;
            this.targetPokemonAbilityIndexId = targetPokemonAbilityIndexId;
            return this;
        }
        public Builder setEatItem(short itemIndexId) {
            this.isEatItem = true;
            this.eatItemIndexId = itemIndexId;
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
        public BattlePokemonAbilityTriggerEffect build() {
            return new BattlePokemonAbilityTriggerEffect(isReloadEffectTargetPokemonId,isReloadEffectActorPokemonId,effectTargetPokemonId,effectActorPokemonId,actorPokemonAbilityIndexId,showAbilityPokemonId,abilityHasTriggerPokemon,abilityTargetPokemonId,isShowMove,showMoveIndexId,isCopyEnemyAbility,targetPokemonAbilityIndexId,isEatItem,eatItemIndexId);
        }
    }
}
