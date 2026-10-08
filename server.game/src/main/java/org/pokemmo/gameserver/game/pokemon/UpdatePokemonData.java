package org.pokemmo.gameserver.game.pokemon;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Setter @Getter @AllArgsConstructor
public class UpdatePokemonData {
    private PokemonData updatePokemon;
    private boolean isReloadPokemonLevel;
    private boolean isReloadPokemonAbilityValue;
    private boolean isReloadPokemonMove;
    private boolean isReloadPokemonCurrentHp;
    private boolean isReloadPokemonStatus;
    private boolean isReloadPokemonIndexId;
    private boolean isReloadPokemonPos;
    private boolean isReloadPokemonEvs;
    private boolean isReloadPokemonItem;
    private boolean isReloadPokemonFriendValue;
    private boolean isReloadPokemonCatchInfo;
    private boolean isReloadPokemonEggValue;
    private boolean isUseRecoverItem;
    private boolean isReloadPokemonBallType;
    private boolean isReloadPokemonRibbonInfo;
    private boolean isReloadPokemonIndividualValue;
    private boolean isReloadPokemonCanRememberMove;
    private boolean isReloadNatureType;
    private boolean isReloadAbilityIndex;
    private boolean isReloadPokemonContestsStatus;
    private boolean isReloadPokemonHiddenPowerType;
    private boolean isReloadPokemonRarity;
    private boolean isReloadPokemonParticleEffects;
    private boolean isReloadCurrentSelectShowParticleEffect;

    public static class Builder {
        private PokemonData updatePokemon;
        private boolean isReloadPokemonLevel;
        private boolean isReloadPokemonAbilityValue;
        private boolean isReloadPokemonMove;
        private boolean isReloadPokemonCurrentHp;
        private boolean isReloadPokemonStatus;
        private boolean isReloadPokemonIndexId;
        private boolean isReloadPokemonPos;
        private boolean isReloadPokemonEvs;
        private boolean isReloadPokemonItem;
        private boolean isReloadPokemonFriendValue;
        private boolean isReloadPokemonCatchInfo;
        private boolean isReloadPokemonEggValue;
        private boolean isUseRecoverItem;
        private boolean isReloadPokemonBallType;
        private boolean isReloadPokemonRibbonInfo;
        private boolean isReloadPokemonIndividualValue;
        private boolean isReloadPokemonCanRememberMove;
        private boolean isReloadNatureType;
        private boolean isReloadAbilityIndex;
        private boolean isReloadPokemonContestsStatus;
        private boolean isReloadPokemonHiddenPowerType;
        private boolean isReloadPokemonRarity;
        private boolean isReloadPokemonParticleEffects;
        private boolean isReloadCurrentSelectShowParticleEffect;
        public Builder setUpdatePokemon(PokemonData updatePokemon) {
            this.updatePokemon = updatePokemon;
            return this;
        }
        public Builder setIsReloadPokemonLevel(boolean isReloadPokemonLevel) {
            this.isReloadPokemonLevel = isReloadPokemonLevel;
            return this;
        }
        public Builder setIsReloadPokemonAbilityValue(boolean isReloadPokemonAbilityValue) {
            this.isReloadPokemonAbilityValue = isReloadPokemonAbilityValue;
            return this;
        }
        public Builder setIsReloadPokemonMove(boolean isReloadPokemonMove) {
            this.isReloadPokemonMove = isReloadPokemonMove;
            return this;
        }
        public Builder setIsReloadPokemonCurrentHp(boolean isReloadPokemonCurrentHp) {
            this.isReloadPokemonCurrentHp = isReloadPokemonCurrentHp;
            return this;
        }
        public Builder setIsReloadPokemonStatus(boolean isReloadPokemonStatus) {
            this.isReloadPokemonStatus = isReloadPokemonStatus;
            return this;
        }
        public Builder setIsReloadPokemonIndexId(boolean isReloadPokemonIndexId) {
            this.isReloadPokemonIndexId = isReloadPokemonIndexId;
            return this;
        }
        public Builder setIsReloadPokemonPos(boolean isReloadPokemonPos) {
            this.isReloadPokemonPos = isReloadPokemonPos;
            return this;
        }
        public Builder setIsReloadPokemonEvs(boolean isReloadPokemonEvs) {
            this.isReloadPokemonEvs = isReloadPokemonEvs;
            return this;
        }
        public Builder setIsReloadPokemonItem(boolean isReloadPokemonItem) {
            this.isReloadPokemonItem = isReloadPokemonItem;
            return this;
        }
        public Builder setIsReloadPokemonFriendValue(boolean isReloadPokemonFriendValue) {
            this.isReloadPokemonFriendValue = isReloadPokemonFriendValue;
            return this;
        }
        public Builder setIsReloadPokemonCatchInfo(boolean isReloadPokemonCatchInfo) {
          this.isReloadPokemonCatchInfo = isReloadPokemonCatchInfo;
          return this;
        }
        public Builder setIsReloadPokemonEggValue(boolean isReloadPokemonEggValue) {
            this.isReloadPokemonEggValue = isReloadPokemonEggValue;
            return this;
        }
        public Builder setIsUseRecoverItem(boolean isUseRecoverItem) {
            this.isUseRecoverItem = isUseRecoverItem;
            return this;
        }
        public Builder setIsReloadPokemonBallType(boolean isReloadPokemonBallType) {
           this.isReloadPokemonBallType = isReloadPokemonBallType;
           return this;
        }
        public Builder setIsReloadPokemonRibbonInfo(boolean isReloadPokemonRibbonInfo) {
            this.isReloadPokemonRibbonInfo = isReloadPokemonRibbonInfo;
            return this;
        }
        public Builder setIsReloadPokemonIndividualValue(boolean isReloadPokemonIndividualValue) {
            this.isReloadPokemonIndividualValue = isReloadPokemonIndividualValue;
            return this;
        }
        public Builder setIsReloadPokemonCanRememberMove(boolean isReloadPokemonCanRememberMove) {
            this.isReloadPokemonCanRememberMove = isReloadPokemonCanRememberMove;
            return this;
        }
        public Builder setIsReloadNatureType(boolean isReloadNatureType) {
            this.isReloadNatureType = isReloadNatureType;
            return this;
        }
        public Builder setIsReloadAbilityIndex(boolean isReloadAbilityIndex) {
            this.isReloadAbilityIndex = isReloadAbilityIndex;
            return this;
        }
        public Builder setIsReloadPokemonContestsStatus(boolean isReloadPokemonContestsStatus) {
            this.isReloadPokemonContestsStatus = isReloadPokemonContestsStatus;
            return this;
        }
        public Builder setIsReloadPokemonHiddenPowerType(boolean isReloadPokemonHiddenPowerType) {
            this.isReloadPokemonHiddenPowerType = isReloadPokemonHiddenPowerType;
            return this;
        }
        public Builder setIsReloadPokemonRarity(boolean isReloadPokemonRarity) {
            this.isReloadPokemonRarity = isReloadPokemonRarity;
            return this;
        }
        public Builder setIsReloadPokemonParticleEffects(boolean isReloadPokemonParticleEffects) {
            this.isReloadPokemonParticleEffects = isReloadPokemonParticleEffects;
            return this;
        }
        public Builder setIsReloadCurrentSelectShowParticleEffect(boolean isReloadCurrentSelectShowParticleEffect) {
            this.isReloadCurrentSelectShowParticleEffect = isReloadCurrentSelectShowParticleEffect;
            return this;
        }
        public UpdatePokemonData build() {
            return new UpdatePokemonData(updatePokemon,isReloadPokemonLevel,isReloadPokemonAbilityValue,isReloadPokemonMove,isReloadPokemonCurrentHp,isReloadPokemonStatus,isReloadPokemonIndexId,isReloadPokemonPos,isReloadPokemonEvs,isReloadPokemonItem,isReloadPokemonFriendValue,isReloadPokemonCatchInfo,isReloadPokemonEggValue,isUseRecoverItem,isReloadPokemonBallType,isReloadPokemonRibbonInfo,isReloadPokemonIndividualValue,isReloadPokemonCanRememberMove,isReloadNatureType,isReloadAbilityIndex,isReloadPokemonContestsStatus,isReloadPokemonHiddenPowerType,isReloadPokemonRarity,isReloadPokemonParticleEffects,isReloadCurrentSelectShowParticleEffect);
        }
    }
}
