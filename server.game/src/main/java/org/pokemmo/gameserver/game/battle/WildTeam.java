package org.pokemmo.gameserver.game.battle;

import java.util.List;

public class WildTeam extends BattleTeam {
    public WildTeam(byte factionIndex, byte teamMaxPokemonAmount, List<BattlePokemonData> teamCanDebutPokemons) {
        super(factionIndex, BattleTeamType.WILD, teamMaxPokemonAmount, (byte) 0, teamCanDebutPokemons, null);
    }
}
