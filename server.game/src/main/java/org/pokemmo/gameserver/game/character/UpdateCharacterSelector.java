package org.pokemmo.gameserver.game.character;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @AllArgsConstructor
public class UpdateCharacterSelector {
    private boolean isRefreshMoney;
    private boolean isRefreshSafariInfo;
    private boolean isRefreshCoins;
    private boolean isRefreshBoxInfo;
    private boolean isRefreshRepelInfo;
    private boolean isRefreshBattlePoints;
    private boolean isRefreshRureInfo;
    private boolean isRefreshParticleEffectInfo;
    private final byte rureTypeValue = -1;
    private final byte particleEffectTypeArray[] = new byte[0];
    private CharacterData character;
    public static class Builder {
        private boolean isRefreshMoney;
        private boolean isRefreshSafariInfo;
        private boolean isRefreshCoins;
        private boolean isRefreshBoxInfo;
        private boolean isRefreshRepelInfo;
        private boolean isRefreshBattlePoints;
        private boolean isRefreshRureInfo;
        private boolean isRefreshParticleEffectInfo;
        private CharacterData characterData;

        public Builder setRefreshMoney(boolean isRefreshMoney) {
            this.isRefreshMoney = isRefreshMoney;
            return this;
        }
        public Builder setRefreshSafariInfo(boolean isRefreshSafariInfo) {
            this.isRefreshSafariInfo = isRefreshSafariInfo;
            return this;
        }
        public Builder setRefreshCoins(boolean isRefreshCoins) {
            this.isRefreshCoins = isRefreshCoins;
            return this;
        }
        public Builder setRefreshBoxInfo(boolean isRefreshBoxInfo) {
            this.isRefreshBoxInfo = isRefreshBoxInfo;
            return this;
        }
        public Builder setRefreshRepelInfo(boolean isRefreshRepelInfo) {
            this.isRefreshRepelInfo = isRefreshRepelInfo;
            return this;
        }
        public Builder setRefreshBattlePoints(boolean isRefreshBattlePoints) {
            this.isRefreshBattlePoints = isRefreshBattlePoints;
            return this;
        }
        public Builder setRefreshRureInfo(boolean isRefreshRureInfo) {
            this.isRefreshRureInfo = isRefreshRureInfo;
            return this;
        }
        public Builder setRefreshParticleEffectInfo(boolean isRefreshParticleEffectInfo) {
            this.isRefreshParticleEffectInfo = isRefreshParticleEffectInfo;
            return this;
        }
        public Builder setCharacterData(CharacterData characterData) {
            this.characterData = characterData;
            return this;
        }
        public UpdateCharacterSelector build() {
            UpdateCharacterSelector updateCharacterSelector = new UpdateCharacterSelector(isRefreshMoney, isRefreshSafariInfo, isRefreshCoins, isRefreshBoxInfo, isRefreshRepelInfo, isRefreshBattlePoints, isRefreshRureInfo, isRefreshParticleEffectInfo,characterData);
            return updateCharacterSelector;
        }
    }
}
