package org.pokemmo.gameserver.game.trainer;
import lombok.Getter;
import org.pokemmo.gameserver.game.battle.BattleTeamType;

import java.util.List;
@Getter
public class TrainerTeamData {
    private short trainerTeamId;
    private TrainerLevelType trainerLevelType;
    private BattleTeamType battleTeamType;
    private byte trainerRegionIndexId;
    private short trainerNameIndexId;
    private int money;
    private List<TrainerBattleTeam> trainerBattleTeams;
    public TrainerTeamData(short trainerTeamId, TrainerLevelType trainerLevelType, BattleTeamType battleTeamType, byte trainerRegionIndexId, short trainerNameIndexId, int money, List<TrainerBattleTeam> trainerBattleTeams) {
        this.trainerTeamId = trainerTeamId;
        this.trainerLevelType = trainerLevelType;
        this.battleTeamType = battleTeamType;
        this.trainerRegionIndexId = trainerRegionIndexId;
        this.trainerNameIndexId = trainerNameIndexId;
        this.money = money;
        this.trainerBattleTeams = trainerBattleTeams;
    }
}
