package org.pokemmo.gameserver.game.trainer;
import java.util.List;

public class JsonTrainerBattleTeamConfig {
    private String teamName;
    private List<JsonTrainerPokemonConfig> pokemons;
    public TrainerBattleTeam toTrainerBattleTeam() {
        return new TrainerBattleTeam(teamName,  pokemons.stream().map(JsonTrainerPokemonConfig::toTrainerPokemonData).toArray(TrainerPokemonData[]::new));
    }
}
