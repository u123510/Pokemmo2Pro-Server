package org.pokemmo.gameserver.game.battle;
import lombok.Getter;

import java.util.List;

@Getter
public class CustomTrainerTeam extends BattleTeam {
    private byte trainerRegionIndexId;
    private byte trainerNameIndex;
    private byte trainerAnimationIndex;
    private int trainerNameLoacalStringIndex;
     public CustomTrainerTeam(int factionIndex, int teamMaxPokemonAmount, int remainItemUseTimes, List<BattlePokemonData> teamCanDebutPokemons, int trainerRegionIndexId, int trainerNameIndex, int trainerAnimationIndex, int trainerNameLoacalStringIndex) {
        super(factionIndex, BattleTeamType.CUSTOM_TRAINER, teamMaxPokemonAmount, remainItemUseTimes, teamCanDebutPokemons,null);
        this.trainerRegionIndexId = (byte)trainerRegionIndexId;
        this.trainerNameIndex = (byte)trainerNameIndex;
        this.trainerAnimationIndex = (byte)trainerAnimationIndex;
        this.trainerNameLoacalStringIndex = trainerNameLoacalStringIndex;
    }
}
