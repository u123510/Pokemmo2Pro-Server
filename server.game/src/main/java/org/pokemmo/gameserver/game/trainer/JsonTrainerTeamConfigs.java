package org.pokemmo.gameserver.game.trainer;

import lombok.Getter;

import java.util.ArrayList;
@Getter
public class JsonTrainerTeamConfigs {
     private ArrayList<JsonTrainerTeamConfig> trainerTeams;
     private ArrayList<JsonTrainerBindingConfig> npcBindings;
}
