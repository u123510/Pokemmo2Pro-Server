package org.pokemmo.gameserver.game.battle;
import lombok.Getter;
import lombok.Setter;
import org.pokemmo.gameserver.game.battle.effect.CooperativeTeam;
import org.server.Session;

import java.util.ArrayList;
import java.util.List;

@Getter @Setter
public class FactionData {
    private byte factionIndex;
    private DebutBattleTeam factionTeam;
    private BattlePokemonData[] debutPokemons = new BattlePokemonData[0];
    private BattleFactionFieldInfo fieldInfo;
    private FactionResultType factionStatType = FactionResultType.IN_BATTLE;
    public FactionData(int factionIndex, DebutBattleTeam factionTeam, BattlePokemonData[] debutPokemons,BattleFactionFieldInfo fieldInfo){
        this.factionIndex = (byte) factionIndex;
        this.factionTeam  = factionTeam;
        this.debutPokemons = debutPokemons;
        this.fieldInfo = fieldInfo;
    }
    public boolean checkHasTargetSession(Session session){
        if(factionTeam.getBattleTeam().getTeamType() == BattleTeamType.COOPERATIVE_PLAYERS || factionTeam.getBattleTeam().getTeamType() == BattleTeamType.COOPERATIVE_NPC) {
            for(DebutBattleTeam team : ((CooperativeTeam)factionTeam.getBattleTeam()).getCoordinateActiveTeamMap().values()){
                if(team.getBattleTeam().getPlayerSession() == session){
                    return true;
                }
            }
        }
        else{
            return factionTeam.getBattleTeam().getPlayerSession() == session;
        }
        return false;
    }

    public byte getFactionTeamSize(){
        byte factionTeamSize = 0;
        if(factionTeam.getBattleTeam().getTeamType() == BattleTeamType.COOPERATIVE_NPC || factionTeam.getBattleTeam().getTeamType() == BattleTeamType.COOPERATIVE_PLAYERS) {
            factionTeamSize = (byte) ((CooperativeTeam)factionTeam.getBattleTeam()).getCoordinateActiveTeamMap().size();
        }
        else{
            factionTeamSize = 1;
        }
        return factionTeamSize;
    }

    public BattleTeam getFactionTeamByTeamIndex(int teamIndex){
        if(factionTeam.getBattleTeam().getTeamType() == BattleTeamType.COOPERATIVE_PLAYERS || factionTeam.getBattleTeam().getTeamType() == BattleTeamType.COOPERATIVE_NPC) {
            return ((CooperativeTeam)factionTeam.getBattleTeam()).getCoordinateActiveTeamMap().get((byte) teamIndex).getBattleTeam();
        }
        else{
            if(teamIndex == 0){
                return factionTeam.getBattleTeam();
            }
        }
        return null;
    }

    public byte getPokemonTeamIndex(BattlePokemonData pokemonData){
        if(pokemonData != null){
            if(factionTeam.getBattleTeam().getTeamType() == BattleTeamType.COOPERATIVE_PLAYERS || factionTeam.getBattleTeam().getTeamType() == BattleTeamType.COOPERATIVE_NPC) {
                for(DebutBattleTeam team : ((CooperativeTeam)factionTeam.getBattleTeam()).getCoordinateActiveTeamMap().values()){
                    if(team.getBattleTeam().checkHasPokemon(pokemonData)){
                        return team.getTeamOriginIndex();
                    }
                }
            }
            else{
                if(factionTeam.getBattleTeam().getPlayerSession()!=null){
                    if(factionTeam.getBattleTeam().checkHasPokemon(pokemonData)){
                        return factionTeam.getTeamOriginIndex();
                    }
                }
            }
        }
        return -1;
    }
    public List<Session> getFactionPlayerSessions(){
        List<Session> sessions = new ArrayList<>();
        if(factionTeam.getBattleTeam().getTeamType() == BattleTeamType.COOPERATIVE_PLAYERS || factionTeam.getBattleTeam().getTeamType() == BattleTeamType.COOPERATIVE_NPC) {
            for(DebutBattleTeam team : ((CooperativeTeam)factionTeam.getBattleTeam()).getCoordinateActiveTeamMap().values()){
                if(team.getBattleTeam().getPlayerSession()!=null){
                    sessions.add(team.getBattleTeam().getPlayerSession());
                }
            }
        }
        else{
            if(factionTeam.getBattleTeam().getPlayerSession()!=null){
                sessions.add(factionTeam.getBattleTeam().getPlayerSession());
            }
        }
        return sessions;
    }
    public Session getPlayerSessionByTeamIndex(int teamIndex){
        if(factionTeam.getBattleTeam().getTeamType() == BattleTeamType.COOPERATIVE_PLAYERS || factionTeam.getBattleTeam().getTeamType() == BattleTeamType.COOPERATIVE_NPC) {
            return ((CooperativeTeam)factionTeam.getBattleTeam()).getCoordinateActiveTeamMap().get(teamIndex).getBattleTeam().getPlayerSession();
        }
        else{
            if(factionTeam.getBattleTeam().getPlayerSession()!=null){
                if(teamIndex == 0){
                    return factionTeam.getBattleTeam().getPlayerSession();
                }
            }
        }
        return null;
    }
    public byte getPlayerSessionDebutIndex(Session session){
        if(factionTeam.getBattleTeam().getTeamType() == BattleTeamType.COOPERATIVE_PLAYERS || factionTeam.getBattleTeam().getTeamType() == BattleTeamType.COOPERATIVE_NPC) {
            for(DebutBattleTeam team : ((CooperativeTeam)factionTeam.getBattleTeam()).getCoordinateActiveTeamMap().values()){
                if(team.getBattleTeam().getPlayerSession() == session){
                    return team.getTeamOriginIndex();
                }
            }
        }
        else{
            if(factionTeam.getBattleTeam().getPlayerSession()!=null){
                if(session == session){
                    return 0;
                }
            }
        }
        return -1;
    }
    public boolean exchangeSessionByTeamIndex(byte teamIndex,Session newSession){
        if(factionTeam.getBattleTeam().getTeamType() == BattleTeamType.COOPERATIVE_PLAYERS || factionTeam.getBattleTeam().getTeamType() == BattleTeamType.COOPERATIVE_NPC) {
            DebutBattleTeam team = ((CooperativeTeam)factionTeam.getBattleTeam()).getCoordinateActiveTeamMap().get(teamIndex);
            if(team!=null){
                team.getBattleTeam().setPlayerSession(newSession);
                return true;
            }
        }
        else{
            if(factionTeam.getBattleTeam().getPlayerSession()!=null){
                if(teamIndex == 0){
                    factionTeam.getBattleTeam().setPlayerSession(newSession);
                    return true;
                }
            }
        }
        return false;
    }

    public boolean checkIsPlayerFaction(){
        if(factionTeam.getBattleTeam().getTeamType() == BattleTeamType.COOPERATIVE_PLAYERS || factionTeam.getBattleTeam().getTeamType() == BattleTeamType.COOPERATIVE_NPC) {
            for(DebutBattleTeam team : ((CooperativeTeam)factionTeam.getBattleTeam()).getCoordinateActiveTeamMap().values()){
                if(team.getBattleTeam().getPlayerSession() != null){
                    return true;
                }
            }
        }
        else{
            return factionTeam.getBattleTeam().getPlayerSession() != null;
        }
        return false;
    }
    public boolean checkIsPlayerPokemonAllAction(){
        if (debutPokemons.length == 0) {
            return true;
        }
        for (BattlePokemonData battlePokemon : debutPokemons) {
            if (battlePokemon == null || battlePokemon.getPokemonData() == null
                    || battlePokemon.getPokemonData().getCurrentHp() <= 0) {
                if (checkHasAlivePokemonInTeam()) {
                    return false;
                }
            } else if (battlePokemon.getOwnerSession() != null) {
                if (battlePokemon.getBattlePokemonCommandType() == BattlePokemonCommandType.NULL) {
                    return false;
                }
            }
        }
        return true;
    }

    public boolean checkHasAlivePokemonInTeam() {
        if (factionTeam.getBattleTeam().getTeamType() == BattleTeamType.COOPERATIVE_NPC
                || factionTeam.getBattleTeam().getTeamType() == BattleTeamType.COOPERATIVE_PLAYERS) {
            for (DebutBattleTeam team : ((CooperativeTeam) factionTeam.getBattleTeam()).getCoordinateActiveTeamMap().values()) {
                for (BattlePokemonData pokemonData : team.getBattleTeam().getTeamPokemons()) {
                    if (pokemonData != null && pokemonData.getPokemonData() != null
                            && pokemonData.getPokemonData().getCurrentHp() > 0) {
                        return true;
                    }
                }
            }
        } else {
            for (BattlePokemonData pokemonData : factionTeam.getBattleTeam().getTeamPokemons()) {
                if (pokemonData != null && pokemonData.getPokemonData() != null
                        && pokemonData.getPokemonData().getCurrentHp() > 0) {
                    return true;
                }
            }
        }
        return false;
    }
    public List<BattlePokemonData> getDebutAlivePokemons(){
        List<BattlePokemonData> alivePokemons = new ArrayList<>();
        for(BattlePokemonData battlePokemonData : debutPokemons){
            if (battlePokemonData.getPokemonData() != null) {
                if(battlePokemonData.getPokemonData().getCurrentHp() > 0){
                    alivePokemons.add(battlePokemonData);
                }
            }
        }
        return alivePokemons;
    }
    public int getDebutAlivePokemonSize(){
        int size = 0;
        for(BattlePokemonData battlePokemonData : debutPokemons){
            if(battlePokemonData != null){
                if(battlePokemonData.getPokemonData().getCurrentHp() > 0){
                    size++;
                }
            }
        }
        return size;
    }
}
