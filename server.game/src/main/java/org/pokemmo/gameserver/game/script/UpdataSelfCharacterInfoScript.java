package org.pokemmo.gameserver.game.script;

public class UpdataSelfCharacterInfoScript {
    private boolean isUpdateMoney;
    private boolean isUpdateSafariInfo;
    private boolean isUpdateCoins;
    private boolean isUpdatePcBoxAmount;
    private boolean isUpdateRepelInfo;
    private boolean isUpdateBattlePoints;
    private boolean isUpdateLureInfo;
    private boolean isUpdateParticleEffect;
    public UpdataSelfCharacterInfoScript(boolean isUpdateMoney, boolean isUpdateSafariInfo, boolean isUpdateCoins, boolean isUpdatePcBoxAmount, boolean isUpdateRepelInfo, boolean isUpdateBattlePoints, boolean isUpdateLureInfo, boolean isUpdateParticleEffect) {
       this.isUpdateMoney = isUpdateMoney;
       this.isUpdateSafariInfo = isUpdateSafariInfo;
       this.isUpdateCoins = isUpdateCoins;
       this.isUpdatePcBoxAmount = isUpdatePcBoxAmount;
       this.isUpdateRepelInfo = isUpdateRepelInfo;
       this.isUpdateBattlePoints = isUpdateBattlePoints;
       this.isUpdateLureInfo = isUpdateLureInfo;
       this.isUpdateParticleEffect = isUpdateParticleEffect;
    }
    public boolean isUpdateMoney() {
        return isUpdateMoney;
    }
    public boolean isUpdateSafariInfo() {
        return isUpdateSafariInfo;
    }
    public boolean isUpdateCoins() {
        return isUpdateCoins;
    }
    public boolean isUpdatePcBoxAmount() {
        return isUpdatePcBoxAmount;
    }
    public boolean isUpdateRepelInfo() {
        return isUpdateRepelInfo;
    }
    public boolean isUpdateBattlePoints() {
        return isUpdateBattlePoints;
    }
    public boolean isUpdateLureInfo() {
        return isUpdateLureInfo;
    }
    public boolean isUpdateParticleEffect() {
        return isUpdateParticleEffect;
    }
}
