package org.pokemmo.gameserver.game.battle;

import org.pokemmo.gameserver.game.battle.effect.CooperativeTeam;

import java.util.Map;

public class CooperativeNpcBattleTeam extends CooperativeTeam{
    private Map<Byte,BattleTeam> coordinateActiveTeamMap;
    public CooperativeNpcBattleTeam(DebutBattleTeam[] coordinateActiveTeams){
        super(BattleTeamType.COOPERATIVE_NPC,coordinateActiveTeams);
    }
}
