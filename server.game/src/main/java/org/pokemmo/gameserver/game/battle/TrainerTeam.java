package org.pokemmo.gameserver.game.battle;

import lombok.Getter;

import java.util.List;

@Getter
public class TrainerTeam extends BattleTeam {
    private byte trainerRegionIndexId;
    private short trainerNameIndexId;
    private int money;
    public TrainerTeam(int factionIndex, int teamMaxPokemonAmount, int remainItemUseTimes, List<BattlePokemonData> teamCanDebutPokemons, int trainerRegionIndexId, int trainerNameIndexId,int money) {
        super(factionIndex, BattleTeamType.TRAINER, teamMaxPokemonAmount, remainItemUseTimes, teamCanDebutPokemons,null);
        this.trainerRegionIndexId = (byte) trainerRegionIndexId;
        this.trainerNameIndexId = (short) trainerNameIndexId;
        this.money = money;
    }
}
