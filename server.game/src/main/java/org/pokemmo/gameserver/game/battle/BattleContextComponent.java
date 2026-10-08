package org.pokemmo.gameserver.game.battle;

import org.pokemmo.gameserver.game.battle.effect.BasePokemonActionEffect;
import org.pokemmo.gameserver.game.move.MoveDamageType;
import org.pokemmo.gameserver.game.move.PokemonMoveData;
import org.server.Session;

import java.util.List;
import java.util.Random;

abstract class BattleContextComponent {
    protected final BattleContextState context;
    protected final BattleBasisInfo battleBasisInfo;
    protected final List<FactionData> debutFactions;
    protected final Random random;
    protected final List<BattlePokemonData> actionPokemons;

    protected BattleContextComponent(BattleContextState context) {
        this.context = context;
        this.battleBasisInfo = context.battleBasisInfo;
        this.debutFactions = context.debutFactions;
        this.random = context.random;
        this.actionPokemons = context.actionPokemons;
    }

    protected List<Session> getBattlePlayerSessions() { return context.getBattlePlayerSessions(); }
    protected Session getPlayerSessionByFactionIndexAndTeamIndex(byte factionIndex, byte teamIndex) { return context.getPlayerSessionByFactionIndexAndTeamIndex(factionIndex, teamIndex); }
    protected boolean checkIsPlayerFaction(int factionIndex) { return context.checkIsPlayerFaction(factionIndex); }
    protected byte getEnemyFactionIndex(byte userFaction) { return context.getEnemyFactionIndex(userFaction); }
    protected byte getFactionPressureAbilityAmount(byte factionIndex) { return context.getFactionPressureAbilityAmount(factionIndex); }
    protected void handlePokemonGainedExp(Session session, BattlePokemonData defeaterPokemon, BattlePokemonData defeatedPokemon) { context.handlePokemonGainedExp(session, defeaterPokemon, defeatedPokemon); }
    protected void settleFaintedPokemonsExp() { context.settleFaintedPokemonsExp(); }
    protected byte getFactionIndexBySession(Session session) { return context.getFactionIndexBySession(session); }
    protected void broadcastBattlePokemonActionWithNoSelector(BattlePokemonData actionPokemon) { context.broadcastBattlePokemonActionWithNoSelector(actionPokemon); }
    protected void broadcastBattlePokemonActionWithSelector() { context.broadcastBattlePokemonActionWithSelector(); }
    protected void broadcastBattlePokemonSwap(BattlePokemonData actionPokemon) { context.broadcastBattlePokemonSwap(actionPokemon); }
    protected void broadcastBattleRoundUpdate() { context.broadcastBattleRoundUpdate(); }
    protected void addActionPokemons() { context.addActionPokemons(); }
    protected void pokemonUseMove(BattlePokemonData actionPokemon, short moveIndexId, byte targetFaction, byte targetPokemonInDebutIndex) { context.pokemonUseMove(actionPokemon, moveIndexId, targetFaction, targetPokemonInDebutIndex); }
    protected void computeMoveTargetPokemons(BattlePokemonData actorPokemon, byte userFaction, byte userPokemonInDebutIndex, byte targetFaction, byte targetPokemonInDebutIndex, short usedMoveIndexId) { context.computeMoveTargetPokemons(actorPokemon, userFaction, userPokemonInDebutIndex, targetFaction, targetPokemonInDebutIndex, usedMoveIndexId); }
    protected short getPokemonAttackStat(BattlePokemonData battlePokemonData) { return context.getPokemonAttackStat(battlePokemonData); }
    protected short getPokemonDefenseStat(BattlePokemonData battlePokemonData) { return context.getPokemonDefenseStat(battlePokemonData); }
    protected short getPokemonSpAttackStat(BattlePokemonData battlePokemonData) { return context.getPokemonSpAttackStat(battlePokemonData); }
    protected short getPokemonSpDefenseStat(BattlePokemonData battlePokemonData) { return context.getPokemonSpDefenseStat(battlePokemonData); }
    protected short getPokemonSpeedStat(BattlePokemonData battlePokemonData) { return context.getPokemonSpeedStat(battlePokemonData); }
    protected boolean computeMoveIsHit(short moveBaseAccuracy, BattlePokemonData actionPokemon, BattlePokemonData targetPokemon) { return context.computeMoveIsHit(moveBaseAccuracy, actionPokemon, targetPokemon); }
    protected BasePokemonActionEffect computerMoveEffect(PokemonMoveData usedMoveData, MoveDamageType moveDamageType, BattlePokemonData actionPokemon, BattlePokemonData targetPokemon) { return context.computerMoveEffect(usedMoveData, moveDamageType, actionPokemon, targetPokemon); }
    protected boolean checkAndHandleBattleFinish(Session hostSession) { return context.checkAndHandleBattleFinish(hostSession); }
    protected void roundSettlement() { context.roundSettlement(); }
    protected void botUseMove(BattlePokemonData actionPokemon) { context.botUseMove(actionPokemon); }
    protected void swapPokemonSettlement() { context.swapPokemonSettlement(); }
    protected boolean useItemSettlement() { return context.useItemSettlement(); }
    protected boolean settleCapture(BattlePokemonData actionPokemon) { return context.settleCapture(actionPokemon); }
    protected void beforeRoundCommandSettlement() { context.beforeRoundCommandSettlement(); }
    protected void InRoundCommandSettlement() { context.InRoundCommandSettlement(); }
    protected void AfterRoundCommandSettlement() { context.AfterRoundCommandSettlement(); }
    protected boolean checkFactionHasAlive(int factionIndex) { return context.checkFactionHasAlive(factionIndex); }
    protected void startNextRound() { context.startNextRound(); }
    protected boolean isWaitingForFaintReplacement() { return context.isWaitingForFaintReplacement(); }
    protected boolean isWaitingForFaintReplacement(byte factionIndex, byte slotIndex) { return context.isWaitingForFaintReplacement(factionIndex, slotIndex); }
    protected boolean isPokemonOnField(BattlePokemonData pokemon) { return context.isPokemonOnField(pokemon); }
    protected void executeInRoundSwap(BattlePokemonData actionPokemon) { context.executeInRoundSwap(actionPokemon); }
    protected boolean handleFaintReplacementSwap(byte swapFaction, byte swapSlot, int swapIndex) { return context.handleFaintReplacementSwap(swapFaction, swapSlot, swapIndex); }
    protected boolean checkAndPromptFaintReplacements() { return context.checkAndPromptFaintReplacements(); }
}
