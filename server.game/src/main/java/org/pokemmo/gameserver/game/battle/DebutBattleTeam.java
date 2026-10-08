package org.pokemmo.gameserver.game.battle;

import lombok.Getter;

@Getter
public class DebutBattleTeam {
    private byte teamOriginIndex;
    private byte teamCurrentIndex;
    private BattleTeam battleTeam;
    public DebutBattleTeam(BattleTeam battleTeam, int teamOriginIndex, int teamCurrentIndex) {
        this.battleTeam = battleTeam;
        this.teamOriginIndex = (byte) teamOriginIndex;
        this.teamCurrentIndex = (byte) teamCurrentIndex;
    }
}
