package org.pokemmo.gameserver.game.trainer;

import lombok.Getter;
import org.pokemmo.gameserver.game.pokemon.PokemonData;
import org.server.Session;

@Getter
public class TrainerBattleTeam {
     private String teamName;
     private TrainerPokemonData[] trainerPokemonDatas;
     public TrainerBattleTeam(String teamName, TrainerPokemonData[] trainerPokemonDatas) {
        this.teamName = teamName;
        this.trainerPokemonDatas = trainerPokemonDatas;
    }
    public PokemonData[] convertPokemonTeamData(Session characterSession){
        PokemonData[] pokemonTeamData = new PokemonData[trainerPokemonDatas.length];
        for (int i = 0; i < trainerPokemonDatas.length; i++) {
            pokemonTeamData[i] = trainerPokemonDatas[i].convertPokemonData(characterSession);
        }
        return pokemonTeamData;
    }
}
