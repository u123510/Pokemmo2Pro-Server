package org.pokemmo.gameserver.game.trainer;

import lombok.Getter;
import org.pokemmo.gameserver.game.battle.BattleFormatType;
import org.pokemmo.gameserver.game.battle.BattleTeamType;

import java.util.List;
@Getter
public class JsonTrainerTeamConfig {
    private short trainerTeamId;
    private String trainerLevelType;
    private String battleTeamType;
    private byte trainerRegionIndexId;
    private short trainerModelIndexId;
    private int money;
    private List<JsonTrainerBattleTeamConfig> battleTeams;
    public TrainerTeamData toTrainerTeam() {
        TrainerTeamData trainerTeamData = new TrainerTeamData(trainerTeamId, TrainerLevelType.getByName(trainerLevelType), BattleTeamType.getByName(battleTeamType), trainerRegionIndexId, trainerModelIndexId, money, battleTeams.stream().map(JsonTrainerBattleTeamConfig::toTrainerBattleTeam).toList());
        return trainerTeamData;
    }
}
