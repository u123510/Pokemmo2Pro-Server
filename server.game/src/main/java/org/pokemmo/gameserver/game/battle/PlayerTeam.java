package org.pokemmo.gameserver.game.battle;

import lombok.Getter;
import org.pokemmo.gameserver.game.character.CharacterData;
import org.server.Session;

import java.util.List;

@Getter
public class PlayerTeam extends BattleTeam {
    private CharacterData characterData;
    private boolean isPvpRank;
    private PvpRankLevelType pvpRankLevel;
    private byte streakTimes;
    //默认战斗状态机未已开始，如果重连需要改到重连状态
    private BattleStateType battleStateType = BattleStateType.STATE_STARTED;

    public PlayerTeam(int factionIndex, int teamMaxPokemonAmount, int remainItemUseTimes, List<BattlePokemonData> teamCanDebutPokemons, CharacterData characterData, boolean isPvpRank, PvpRankLevelType pvpRankLevel, int streakTimes, Session playerSession) {
        super(factionIndex, BattleTeamType.PLAYER, teamMaxPokemonAmount, remainItemUseTimes, teamCanDebutPokemons, playerSession);
        this.characterData = characterData;
        this.isPvpRank = isPvpRank;
        this.pvpRankLevel = pvpRankLevel;
        this.streakTimes = (byte)streakTimes;
    }
    public PlayerTeam(int factionIndex, int teamMaxPokemonAmount, int remainItemUseTimes, List<BattlePokemonData> teamCanDebutPokemons, CharacterData characterData, Session playerSession) {
        super(factionIndex, BattleTeamType.PLAYER, teamMaxPokemonAmount, remainItemUseTimes, teamCanDebutPokemons, playerSession);
        this.characterData = characterData;
    }
}
