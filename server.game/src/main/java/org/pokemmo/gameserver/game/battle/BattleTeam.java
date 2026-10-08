package org.pokemmo.gameserver.game.battle;
import lombok.Getter;
import lombok.Setter;
import org.server.Session;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Getter @Setter
public abstract class BattleTeam {
    private byte factionIndex;
    private BattleTeamType teamType;
    private byte teamMaxPokemonAmount;
    private byte remainItemUseTimes;
    private List<BattlePokemonData> teamPokemons = new ArrayList<>();//此列表不应该直接使用
    private BattleFactionFieldInfo factionFieldInfo = new BattleFactionFieldInfo();
    private Session playerSession;
    public BattleTeam(int factionIndex, BattleTeamType teamType, int teamMaxPokemonAmount, int remainItemUseTimes, List<BattlePokemonData> teamPokemons, Session playerSession) {
        this.factionIndex = (byte) factionIndex;
        this.teamType = teamType;
        this.teamMaxPokemonAmount = (byte) teamMaxPokemonAmount;
        this.remainItemUseTimes = (byte) remainItemUseTimes;
        this.teamPokemons = teamPokemons;
        this.playerSession = playerSession;
    }
    public BattleTeam(BattleTeamType teamType){
        this.teamType = teamType;
    }
    public BattlePokemonData getTeamPokemonById(long pokemonId){
        for(BattlePokemonData battlePokemonData : teamPokemons){
            if(battlePokemonData != null && battlePokemonData.getPokemonData().getPokemonId()== pokemonId){
                return battlePokemonData;
            }
        }
        return null;
    }
    public List<BattlePokemonData> getTeamCanDebutPokemons(){
        List<BattlePokemonData> canDebutPokemons = new ArrayList<>();
        for(BattlePokemonData battlePokemonData : teamPokemons){
            if(battlePokemonData != null){
                canDebutPokemons.add(battlePokemonData);
            }
        }
        return canDebutPokemons;
    }
    public int getTeamCanDebutPokemonSize(){
        int size = 0;
        for(BattlePokemonData battlePokemonData : teamPokemons){
            if(battlePokemonData != null){
                size++;
            }
        }
        return size;
    }
    public boolean checkHasPokemon(BattlePokemonData pokemon){
        for(BattlePokemonData battlePokemonData : teamPokemons){
            if(battlePokemonData != null && battlePokemonData.equals(pokemon)){
                return true;
            }
        }
        return false;
    }
}
