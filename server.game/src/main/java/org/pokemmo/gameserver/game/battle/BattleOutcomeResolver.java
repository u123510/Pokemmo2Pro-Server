package org.pokemmo.gameserver.game.battle;
import org.pokemmo.gameserver.codecs.BattleDebutPokemonCodec;
import org.pokemmo.gameserver.codecs.BattleTeamPokemonCodec;
import org.pokemmo.gameserver.game.battle.effect.*;
import org.pokemmo.gameserver.game.character.CharacterManager;
import org.pokemmo.gameserver.game.story.StoryService;
import org.pokemmo.gameserver.game.pool.GameSessionPool;
import org.pokemmo.gameserver.game.item.ItemData;
import org.pokemmo.gameserver.game.item.ItemManager;
import org.pokemmo.gameserver.game.move.MoveDamageType;
import org.pokemmo.gameserver.game.move.MoveManager;
import org.pokemmo.gameserver.game.move.PokemonMoveData;
import org.pokemmo.gameserver.game.pokemon.*;
import org.pokemmo.gameserver.game.string.BattleString;
import org.pokemmo.gameserver.game.string.BattleStringType;
import org.pokemmo.gameserver.protocol.GameProtocol;
import org.pokemmo.gameserver.protocol.packets.s2c.*;
import org.server.Session;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

final class BattleOutcomeResolver extends BattleContextComponent {
    BattleOutcomeResolver(BattleContextState context) {
        super(context);
    }

    public boolean checkIsPlayerPokemonAllAction(){
        for(FactionData faction : debutFactions){
            if(!faction.checkIsPlayerPokemonAllAction()){
                return false;
            }
        }
        return true;
    }

    public boolean roundTimeoutSettlement(BattleType battleType,Session hostSession){
        byte hostFactionIndex = getFactionIndexBySession(hostSession);
        byte enemyFactionIndex = getEnemyFactionIndex(hostFactionIndex);
        //如果是pvp对战，则直接对超时选择的一方判负
        if(battleType == BattleType.PlayerBattle) {
            boolean isFirstFactionAllAction = debutFactions.get(0).checkIsPlayerPokemonAllAction();
            boolean isSecondFactionAllAction = debutFactions.get(1).checkIsPlayerPokemonAllAction();
            //此时说明双方都超时，发起者判负
            if(!isFirstFactionAllAction && !isSecondFactionAllAction){
                debutFactions.get(hostFactionIndex).setFactionStatType(FactionResultType.DEFEAT);
                debutFactions.get(enemyFactionIndex).setFactionStatType(FactionResultType.VICTORY);
            }
            //一阵营超时，二阵营未超时
            if(!isFirstFactionAllAction && isSecondFactionAllAction){
                debutFactions.get(0).setFactionStatType(FactionResultType.DEFEAT);
                debutFactions.get(1).setFactionStatType(FactionResultType.VICTORY);
            }
            if(isFirstFactionAllAction && !isSecondFactionAllAction){
                debutFactions.get(0).setFactionStatType(FactionResultType.VICTORY);
                debutFactions.get(1).setFactionStatType(FactionResultType.DEFEAT);
            }
            checkAndHandleBattleFinish(hostSession);
            return true;
        }
        //如果是玩家合作的对战
        if(battleType == BattleType.CooperativeBossBattle){
            //此时玩家阵营一定是阵营0，对玩家宝可梦进行遍历，对未行动的宝可梦随机选择技能
            for (BattlePokemonData pokemon : debutFactions.get(hostFactionIndex).getDebutPokemons()) {
                if(pokemon.getPokemonData().getCurrentHp() > 0){
                    //如果宝可梦未行动，则随机选择技能
                    if(pokemon.getBattlePokemonCommandType() == BattlePokemonCommandType.NULL){
                        botUseMove(pokemon);
                    }
                }
            }
            //回合结算
            roundSettlement();
            return checkAndHandleBattleFinish(hostSession);
            //需要对战斗进行结算
        }
        return false;
    }

    public boolean forceVictory(Session session) {
        byte winnerFactionIndex = getFactionIndexBySession(session);
        if (winnerFactionIndex < 0 || winnerFactionIndex >= debutFactions.size()) {
            return false;
        }

        byte loserFactionIndex = getEnemyFactionIndex(winnerFactionIndex);
        if (loserFactionIndex < 0 || loserFactionIndex >= debutFactions.size()) {
            return false;
        }

        FactionData winnerFaction = debutFactions.get(winnerFactionIndex);
        FactionData loserFaction = debutFactions.get(loserFactionIndex);
        if (winnerFaction.getFactionStatType() != FactionResultType.IN_BATTLE
                || loserFaction.getFactionStatType() != FactionResultType.IN_BATTLE) {
            return false;
        }

        winnerFaction.setFactionStatType(FactionResultType.VICTORY);
        loserFaction.setFactionStatType(FactionResultType.DEFEAT);
        return checkAndHandleBattleFinish(session);
    }

    public boolean runAway(Session session, byte selectorData) {
        if (battleBasisInfo.getBattleType() != BattleType.WildBattle
                || !battleBasisInfo.isCanRun()) {
            return false;
        }

        byte runnerFactionIndex = getFactionIndexBySession(session);
        if (!isValidFactionIndex(runnerFactionIndex)
                || (selectorData & 0x0F) != runnerFactionIndex) {
            return false;
        }

        byte winnerFactionIndex = getEnemyFactionIndex(runnerFactionIndex);
        if (!isValidFactionIndex(winnerFactionIndex)) {
            return false;
        }

        FactionData runnerFaction = debutFactions.get(runnerFactionIndex);
        FactionData winnerFaction = debutFactions.get(winnerFactionIndex);
        if (runnerFaction.getFactionStatType() != FactionResultType.IN_BATTLE
                || winnerFaction.getFactionStatType() != FactionResultType.IN_BATTLE) {
            return false;
        }

        runnerFaction.setFactionStatType(FactionResultType.RUN);
        winnerFaction.setFactionStatType(FactionResultType.VICTORY);
        broadcastRunResult(selectorData, BattleRunResultType.NORMAL_SUCCESS);
        broadcastBattleFinish(winnerFactionIndex);
        return true;
    }

    public boolean forfeit(Session session, byte selectorData) {
        if (battleBasisInfo.getBattleType() != BattleType.PlayerBattle
                || !battleBasisInfo.isCanSurrender()
                || battleBasisInfo.getBattleRoundAmount() < battleBasisInfo.getSurrenderLimitArround()) {
            return false;
        }

        byte forfeiterFactionIndex = getFactionIndexBySession(session);
        if (!isValidFactionIndex(forfeiterFactionIndex)
                || (selectorData & 0x0F) != forfeiterFactionIndex) {
            return false;
        }

        byte winnerFactionIndex = getEnemyFactionIndex(forfeiterFactionIndex);
        if (!isValidFactionIndex(winnerFactionIndex)) {
            return false;
        }

        FactionData forfeiterFaction = debutFactions.get(forfeiterFactionIndex);
        FactionData winnerFaction = debutFactions.get(winnerFactionIndex);
        if (forfeiterFaction.getFactionStatType() != FactionResultType.IN_BATTLE
                || winnerFaction.getFactionStatType() != FactionResultType.IN_BATTLE) {
            return false;
        }

        forfeiterFaction.setFactionStatType(FactionResultType.DEFEAT);
        winnerFaction.setFactionStatType(FactionResultType.VICTORY);
        broadcastRunResult(selectorData, BattleRunResultType.FORFEIT);
        broadcastBattleFinish(winnerFactionIndex);
        return true;
    }

    public boolean checkAndHandleBattleFinish(Session hostSession){
        settleFaintedPokemonsExp();
        byte hostFactionIndex = getFactionIndexBySession(hostSession);
        byte enemyFactionIndex = getEnemyFactionIndex(hostFactionIndex);
        byte winnerFaction = -1;
        if(debutFactions.get(hostFactionIndex).getFactionStatType() == FactionResultType.VICTORY
                || debutFactions.get(hostFactionIndex).getFactionStatType() == FactionResultType.CATCH_POKEMON){
            winnerFaction = hostFactionIndex;
        }
        if(debutFactions.get(enemyFactionIndex).getFactionStatType() == FactionResultType.VICTORY
                || debutFactions.get(enemyFactionIndex).getFactionStatType() == FactionResultType.CATCH_POKEMON){
            winnerFaction = enemyFactionIndex;
        }
        //如果在检测前还未进行胜负判断，则在此进行正常的宝可梦死亡胜负判断
        if(winnerFaction == -1){
            boolean hostFactionIsAllDied = !checkFactionHasAlive(hostFactionIndex);
            boolean enemyFactionIsAllDied = !checkFactionHasAlive(enemyFactionIndex);
            //双方同时全部阵亡
            if(hostFactionIsAllDied && enemyFactionIsAllDied){
                this.debutFactions.get(hostFactionIndex).setFactionStatType(FactionResultType.DEFEAT);
                this.debutFactions.get(enemyFactionIndex).setFactionStatType(FactionResultType.VICTORY);
                winnerFaction = enemyFactionIndex;
            }
            //发起者全部阵亡，敌人阵营未阵亡
            if(hostFactionIsAllDied && !enemyFactionIsAllDied){
                this.debutFactions.get(hostFactionIndex).setFactionStatType(FactionResultType.DEFEAT);
                this.debutFactions.get(enemyFactionIndex).setFactionStatType(FactionResultType.VICTORY);
                winnerFaction = enemyFactionIndex;
            }
            //敌人阵营全部阵亡，发起者未阵亡
            if(!hostFactionIsAllDied && enemyFactionIsAllDied){
                this.debutFactions.get(hostFactionIndex).setFactionStatType(FactionResultType.VICTORY);
                this.debutFactions.get(enemyFactionIndex).setFactionStatType(FactionResultType.DEFEAT);
                winnerFaction = hostFactionIndex;
            }
        }
        if(winnerFaction != -1) {
            broadcastBattleFinish(winnerFaction);
            return true;
        }
        return false;
    }

    private boolean isValidFactionIndex(byte factionIndex) {
        return factionIndex >= 0 && factionIndex < debutFactions.size();
    }

    private void broadcastRunResult(byte selectorData, BattleRunResultType resultType) {
        for (Session playerSession : getBattlePlayerSessions()) {
            playerSession.send(new SendBattleRunResultPacket(selectorData, resultType));
        }
    }

    private void broadcastBattleFinish(byte presentationFactionIndex) {
        List<Session> sessions = getBattlePlayerSessions();
        for (Session playerSession : sessions) {
            CharacterManager player = playerSession.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get();
            if (context instanceof BattleManager manager) StoryService.onBattleFinished(player, manager);
            List<BattleString> battleStringList_0 = new ArrayList<>(0);
            battleStringList_0.add(new BattleString(BattleStringType.NULL_TYPE));
            List<BattleString> battleStringList_1 = new ArrayList<>(0);
            battleStringList_1.add(new BattleString(BattleStringType.NULL_TYPE));
            List<BattleString> battleStringList_2 = new ArrayList<>(0);
            playerSession.send(new SendBattleFinishPacket(presentationFactionIndex, battleStringList_0,
                    battleStringList_1, 0, 0, false, battleStringList_2));
        }
        BattleManager battleManager = context instanceof BattleManager manager ? manager : null;
        if (battleManager == null) {
            return;
        }
        battleManager.broadcastPlayerBattleStatus((byte) 0);
        for (Session spectatorSession : battleManager.getSpectatorSessions()) {
            CharacterManager spectatorManager =
                    spectatorSession.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get();
            if (spectatorManager != null && spectatorManager.getBattleManager() == battleManager) {
                spectatorManager.setBattleManager(null);
            }
            battleManager.removeSpectator(spectatorSession);
        }
        GameSessionPool.removeBattleManagerInPool(battleManager);
    }

    @Override
    protected boolean checkFactionHasAlive(int factionIndex) {
        DebutBattleTeam factionTeam = debutFactions.get(factionIndex).getFactionTeam();
        if (factionTeam == null || factionTeam.getBattleTeam() == null) {
            return false;
        }
        if (factionTeam.getBattleTeam().getTeamType() == BattleTeamType.COOPERATIVE_NPC || factionTeam.getBattleTeam().getTeamType() == BattleTeamType.COOPERATIVE_PLAYERS) {
            for (DebutBattleTeam debutBattleTeam : ((CooperativeTeam) factionTeam.getBattleTeam()).getCoordinateActiveTeamMap().values()) {
                if (debutBattleTeam != null && debutBattleTeam.getBattleTeam() != null && debutBattleTeam.getBattleTeam().getTeamPokemons() != null) {
                    for (BattlePokemonData pokemonData : debutBattleTeam.getBattleTeam().getTeamPokemons()) {
                        if (pokemonData != null && pokemonData.getPokemonData() != null && pokemonData.getPokemonData().getCurrentHp() > 0) {
                            return true;
                        }
                    }
                }
            }
        } else if (factionTeam.getBattleTeam().getTeamPokemons() != null) {
            for (BattlePokemonData pokemonData : factionTeam.getBattleTeam().getTeamPokemons()) {
                if (pokemonData != null && pokemonData.getPokemonData() != null && pokemonData.getPokemonData().getCurrentHp() > 0) {
                    return true;
                }
            }
        }
        return false;
    }
}

