package org.pokemmo.gameserver.game.battle;

import org.pokemmo.gameserver.game.battle.effect.CooperativeTeam;

public class CooperativePlayersBattleTeam extends CooperativeTeam {
    public CooperativePlayersBattleTeam(DebutBattleTeam[] coordinateActiveTeams){
        super(BattleTeamType.COOPERATIVE_PLAYERS,coordinateActiveTeams);
    }
}
