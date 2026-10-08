package org.pokemmo.gameserver.game.script;

import lombok.Getter;

@Getter
public class UpdatePlayerInfoScript extends GameScript {
    private boolean isRefreshMoney;
    private boolean isRefreshSafariInfo;
    private boolean isRefreshCoins;
    private boolean isRefreshBoxInfo;
    private boolean isRefreshRepelInfo;
    private boolean isRefreshBattlePoints;
    private boolean isRefreshRureInfo;
    private boolean isRefreshParticleEffectInfo;
    public UpdatePlayerInfoScript(boolean isRefreshMoney, boolean isRefreshSafariInfo, boolean isRefreshCoins, boolean isRefreshBoxInfo, boolean isRefreshRepelInfo, boolean isRefreshBattlePoints, boolean isRefreshRureInfo, boolean isRefreshParticleEffectInfo) {
        super(ScriptActionType.UPDATE_PLAYER_INFO);
        this.isRefreshMoney = isRefreshMoney;
        this.isRefreshSafariInfo = isRefreshSafariInfo;
        this.isRefreshCoins = isRefreshCoins;
        this.isRefreshBoxInfo = isRefreshBoxInfo;
        this.isRefreshRepelInfo =isRefreshRepelInfo;
        this.isRefreshBattlePoints = isRefreshBattlePoints;
        this.isRefreshRureInfo = isRefreshRureInfo;
        this.isRefreshParticleEffectInfo = isRefreshParticleEffectInfo;
    }
}
