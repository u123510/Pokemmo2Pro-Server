package org.pokemmo.gameserver.game.battle.effect;

import lombok.Getter;
import org.pokemmo.gameserver.game.battle.BattleTeam;
import org.pokemmo.gameserver.game.battle.BattleTeamType;
import org.pokemmo.gameserver.game.battle.DebutBattleTeam;

import java.util.Map;
@Getter
public abstract class CooperativeTeam extends BattleTeam {
    private Map<Byte,DebutBattleTeam> coordinateActiveTeamMap;
    public CooperativeTeam(BattleTeamType battleTeamType,DebutBattleTeam[] coordinateActiveTeams){
        super(battleTeamType);
        for (DebutBattleTeam debutBattleTeam : coordinateActiveTeams) {
            coordinateActiveTeamMap.put(debutBattleTeam.getTeamOriginIndex(), debutBattleTeam);
        }
        this.coordinateActiveTeamMap = java.util.Collections.unmodifiableMap(coordinateActiveTeamMap);
    }
}
