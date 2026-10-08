package org.pokemmo.gameserver.game.trainer;

import lombok.Getter;

@Getter
public class JsonTrainerBindingConfig {
    private String map;
    private int entityIdx;
    private String script;
    private short trainerTeamId;
    private int sightRange = 1;
}
