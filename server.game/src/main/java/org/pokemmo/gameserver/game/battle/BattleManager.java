package org.pokemmo.gameserver.game.battle;
import org.pokemmo.gameserver.game.item.ItemData;
import org.pokemmo.gameserver.game.item.ItemManager;
import org.pokemmo.gameserver.game.character.CharacterManager;
import org.pokemmo.gameserver.game.character.PlayerVisibilityService;
import org.pokemmo.gameserver.game.map.MapConnectionType;
import org.pokemmo.gameserver.game.map.MapData;
import org.pokemmo.gameserver.protocol.GameProtocol;
import org.pokemmo.gameserver.protocol.packets.s2c.*;
import org.server.Session;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Set;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Getter
public class BattleManager extends BattleContext {
    private final AtomicBoolean initialRoundSettled = new AtomicBoolean();
    private final Set<Session> spectatorSessions = ConcurrentHashMap.newKeySet();

    public BattleManager(BattleType battleType, BattleFormatType battleFormatType, BattleFormType battleFormType, BattleFacilityType battleFacilityType, FactionData selfFaction, FactionData enemyFaction, int battleAlreadyRunTime){
        super(battleType, battleFormatType, battleFormType, battleFacilityType, selfFaction, enemyFaction, battleAlreadyRunTime);
    }
    //处理战斗初始化
    public void handleBattleBegin(Session session,boolean isReconnect,boolean isSpectate){
        byte selfFactionIndex = isSpectate ? -1 : getFactionIndexBySession(session);
        byte enemyFactionIndex = isSpectate ? -1 : getEnemyFactionIndex(selfFactionIndex);
        session.send(new SendBattleInitPacket(this,session,isReconnect,isSpectate));
        if(!isReconnect && !isSpectate
                && battleBasisInfo.getBattleType() != BattleType.PlayerBattle
                && initialRoundSettled.compareAndSet(false, true)){
            //当战斗类型为人机战斗类型时候，
            switch (battleBasisInfo.getBattleType())
            {
                case WildBattle:
                case LowTrainerBattle:
                case AceTrainerBattle:
                case RivalBattle:
                case GymLeaderBattle:
                case EliteBattle:
                    for(BattlePokemonData botPokemon:debutFactions.get(enemyFactionIndex).getDebutAlivePokemons()){
                        if(botPokemon != null){
                            botPokemon.addPokemonsToExpPool(debutFactions.get(selfFactionIndex).getDebutAlivePokemons());
                        }
                    }
                    break;
            }
            roundSettlement();
        }
        if(isSpectate) {
            session.send(new SendBattleWaitForPlayerActionPacket());
        }
        else{
            for (BattlePokemonData debutPokemon : debutFactions.get(selfFactionIndex).getDebutAlivePokemons()) {
                session.send(new SendBattleDebutPokemonCanActionPacket(debutPokemon.getDebutIndex(), true));
            }
        }
    }

    public boolean addSpectator(Session session) {
        if (session == null || battleBasisInfo.getBattleType() != BattleType.PlayerBattle
                || getFactionIndexBySession(session) >= 0 || !session.isActive()) {
            return false;
        }
        synchronized (spectatorSessions) {
            if (spectatorSessions.size() >= 16 || !spectatorSessions.add(session)) {
                return false;
            }
        }
        try {
            handleBattleBegin(session, false, true);
            return true;
        } catch (RuntimeException exception) {
            spectatorSessions.remove(session);
            throw exception;
        }
    }

    public boolean removeSpectator(Session session) {
        return session != null && spectatorSessions.remove(session);
    }

    public boolean isSpectator(Session session) {
        return session != null && spectatorSessions.contains(session);
    }

    public List<Session> getSpectatorSessions() {
        return new ArrayList<>(spectatorSessions);
    }

    /** Broadcasts the client-visible PvP state for both fighters to nearby players. */
    public void broadcastPlayerBattleStatus(byte status) {
        if (battleBasisInfo.getBattleType() != BattleType.PlayerBattle) {
            return;
        }
        Set<Long> sentKeys = ConcurrentHashMap.newKeySet();
        for (Session fighterSession : super.getBattlePlayerSessions()) {
            CharacterManager fighter = fighterSession == null
                    ? null : fighterSession.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get();
            if (fighter == null || fighter.getCharacterData() == null
                    || fighter.getCharacterData().getPlayerEntity() == null) {
                continue;
            }
            MapData map = fighter.getCurrentMapDatas()[MapConnectionType.NOTHING.getType()];
            if (map == null) {
                continue;
            }
            long fighterId = fighter.getCharacterData().getPlayerEntity().getEntityGameId();
            for (Session recipient : map.getPlayerSessionPool().values()) {
                if (recipient == null || !recipient.isActive()) {
                    continue;
                }
                CharacterManager recipientManager =
                        recipient.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get();
                if (recipientManager == null || recipientManager.getCharacterData() == null
                        || recipientManager.getCharacterData().getPlayerEntity() == null
                        || recipientManager.getCharacterData().getChannel() != fighter.getCharacterData().getChannel()
                        || !sameMap(fighter, recipientManager)) {
                    continue;
                }
                long recipientId = recipientManager.getCharacterData().getPlayerEntity().getEntityGameId();
                if (sentKeys.add(recipientId * 31L + fighterId)) {
                    PlayerVisibilityService.sendIfVisible(fighter, recipient, new SendBattleStatusPacket(fighterId, status));
                }
            }
        }
    }

    private static boolean sameMap(CharacterManager first, CharacterManager second) {
        return first.getCharacterData().getPlayerEntity().getRegionIndexId()
                == second.getCharacterData().getPlayerEntity().getRegionIndexId()
                && first.getCharacterData().getPlayerEntity().getMapHeaderIdOrGbaMapGroupId()
                == second.getCharacterData().getPlayerEntity().getMapHeaderIdOrGbaMapGroupId()
                && first.getCharacterData().getPlayerEntity().getGbaMapId()
                == second.getCharacterData().getPlayerEntity().getGbaMapId();
    }

    @Override
    public List<Session> getBattlePlayerSessions() {
        List<Session> sessions = new ArrayList<>(super.getBattlePlayerSessions());
        for (Session spectatorSession : spectatorSessions) {
            if (spectatorSession != null && spectatorSession.isActive()
                    && !sessions.contains(spectatorSession)) {
                sessions.add(spectatorSession);
            }
        }
        return sessions;
    }
    public void handlePreviewBattleBegin(Session session,boolean isReconnect,boolean isSpectate) {
        session.send(new SendBattleInitPacket(this,session,isReconnect,isSpectate));

    }
    public void handlePreviewBattlePokemonDebut(Session session,boolean isReconnect,boolean isSpectate){
        byte selfFactionIndex = getFactionIndexBySession(session);
        byte selfTeamInDebutIndex = debutFactions.get(selfFactionIndex).getPlayerSessionDebutIndex(session);
        session.send(new SendBattlePreviewPokemonDebutPacket((byte) 2,debutFactions,false,
                null,selfFactionIndex,selfTeamInDebutIndex,new boolean[0],battleBasisInfo.getBattleFormType()));
        if(!isSpectate) {
            session.send(new SendBattleDebutPokemonCanActionPacket((byte) 0, true));
            /*for (BattlePokemonData debutPokemon : debutFactions.get(selfFactionIndex).getDebutAlivePokemons()) {
                session.send(new SendBattleDebutPokemonCanActionPacket(debutPokemon.getDebutIndex(), true));
            }*/
        }
    }


    //重连以后将双方阵营的session记录进行更新
    public boolean exChangeSessionText(Session oldSession,Session newSession){
        if (isSpectator(oldSession)) {
            spectatorSessions.remove(oldSession);
            if (newSession == null || !newSession.isActive() || spectatorSessions.size() >= 16) {
                return false;
            }
            return spectatorSessions.add(newSession);
        }
        //将战斗设置为重连状态
        byte oldSessionFactionIndex = getFactionIndexBySession(oldSession);
        byte oldSessionTeamInDebutIndex = debutFactions.get(oldSessionFactionIndex).getPlayerSessionDebutIndex(oldSession);
        FactionData oldSessionFactionData = debutFactions.get(oldSessionFactionIndex);
        if(oldSessionFactionData != null){
            return oldSessionFactionData.exchangeSessionByTeamIndex(oldSessionTeamInDebutIndex,newSession);
        }
        return false;
    }

    //处理使用技能
    public boolean handlePlayerUseMove(byte userFaction,byte userPokemonInDebutIndex,byte targetFaction,byte targetPokemonInDebutIndex,short moveIndexId) throws InterruptedException {
        if (userFaction < 0 || userFaction >= debutFactions.size()) {
            return false;
        }
        if(battleBasisInfo.getBattleFormatType() == BattleFormatType.SINGLE_BATTLE) {
            targetFaction = getEnemyFactionIndex(userFaction);
        }
        if (userPokemonInDebutIndex < 0
                || userPokemonInDebutIndex >= debutFactions.get(userFaction).getDebutPokemons().length
                || targetFaction < 0 || targetFaction >= debutFactions.size()
                || targetPokemonInDebutIndex < 0
                || targetPokemonInDebutIndex >= debutFactions.get(targetFaction).getDebutPokemons().length) {
            return false;
        }
        BattlePokemonData actionPokemon = debutFactions.get(userFaction).getDebutPokemons()[userPokemonInDebutIndex];
        BattlePokemonData targetPokemon = debutFactions.get(targetFaction).getDebutPokemons()[targetPokemonInDebutIndex];
        //合法性检测
        if(actionPokemon != null && targetPokemon != null){
            if(actionPokemon.getPokemonData().getCurrentHp() > 0 && targetPokemon.getPokemonData().getCurrentHp() > 0){
                //判断并设置宝可梦的技能行动信息
                pokemonUseMove(actionPokemon,moveIndexId,targetFaction,targetPokemonInDebutIndex);
            }
        }
        boolean accepted = actionPokemon != null
                && actionPokemon.getBattlePokemonCommandType() == BattlePokemonCommandType.MOVE;
        if(accepted && checkIsPlayerPokemonAllAction()){
            roundSettlement();
        }
        return accepted;
    }
    //处理玩家更换宝可梦
    public boolean handlePlayerSwap(byte swapFaction, byte swapPokemonDebutIndex, int swapIndex) {
        if (swapFaction < 0 || swapFaction >= debutFactions.size()
                || swapPokemonDebutIndex < 0
                || swapPokemonDebutIndex >= debutFactions.get(swapFaction).getDebutPokemons().length) {
            return false;
        }
        FactionData swapFactionData = debutFactions.get(swapFaction);
        BattlePokemonData originalPokemonData = swapFactionData.getDebutPokemons()[swapPokemonDebutIndex];
        if (originalPokemonData == null || swapIndex < 0) {
            return false;
        }
        if (isWaitingForFaintReplacement(swapFaction, swapPokemonDebutIndex)
                || originalPokemonData.getPokemonData() == null
                || originalPokemonData.getPokemonData().getCurrentHp() <= 0) {
            return handleFaintReplacementSwap(swapFaction, swapPokemonDebutIndex, swapIndex);
        }
        byte swapTeamIndex = swapFactionData.getPokemonTeamIndex(originalPokemonData);
        BattleTeam swapTeam = debutFactions.get(swapFaction).getFactionTeamByTeamIndex(swapTeamIndex);
        if (swapTeam == null || swapIndex >= swapTeam.getTeamPokemons().size()) {
            return false;
        }
        BattlePokemonData swapPokemon = swapTeam.getTeamPokemons().get(swapIndex);
        if (swapPokemon != null && swapPokemon.getPokemonData() != null
                && swapPokemon.getPokemonData().getCurrentHp() > 0
                && !isPokemonOnField(swapPokemon)) {
            originalPokemonData.setBattlePokemonCommandType(BattlePokemonCommandType.SWAP);
            originalPokemonData.setTargetPokemon(swapPokemon);
        }
        boolean accepted = originalPokemonData.getBattlePokemonCommandType() == BattlePokemonCommandType.SWAP;
        if (accepted && checkIsPlayerPokemonAllAction()) {
            roundSettlement();
        }
        return accepted;
    }
    public boolean handlePlayerUseItem(short itemIndexId,long itemTargetPokemonId,byte userFaction,byte userPokemonInDebutIndex){
        ItemData itemData = ItemManager.getItemData(itemIndexId);
        if (itemData == null || userFaction < 0 || userFaction >= debutFactions.size()
                || userPokemonInDebutIndex < 0
                || userPokemonInDebutIndex >= debutFactions.get(userFaction).getDebutPokemons().length) {
            return false;
        }
        BattlePokemonData actionPokemon = debutFactions.get(userFaction)
                .getDebutPokemons()[userPokemonInDebutIndex];
        if (actionPokemon == null || actionPokemon.getPokemonData() == null
                || actionPokemon.getPokemonData().getCurrentHp() <= 0) {
            return false;
        }

        BattlePokemonData useItemTargetPokemon;
        if (battleBasisInfo.getBattleType() == BattleType.WildBattle
                && itemData.isCaptureBall()) {
            byte enemyFaction = getEnemyFactionIndex(userFaction);
            useItemTargetPokemon = debutFactions.get(enemyFaction).getDebutPokemons().length == 0
                    ? null : debutFactions.get(enemyFaction).getDebutPokemons()[0];
        } else {
            BattleTeam userTeam = debutFactions.get(userFaction)
                    .getFactionTeamByTeamIndex(actionPokemon.getPokemonTeamIndex());
            useItemTargetPokemon = userTeam == null ? null
                    : userTeam.getTeamPokemonById(itemTargetPokemonId);
        }
        if (useItemTargetPokemon != null && useItemTargetPokemon.getPokemonData() != null
                && useItemTargetPokemon.getPokemonData().getCurrentHp() > 0) {
            //设置行动类型
            actionPokemon.setBattlePokemonCommandType(BattlePokemonCommandType.ITEM);
            //设置目标宝可梦
            actionPokemon.setTargetPokemon(useItemTargetPokemon);
            //设置当前回合使用的道具
            actionPokemon.setRoundChoiceItemIndexId(itemIndexId);

        }
        boolean accepted = actionPokemon.getBattlePokemonCommandType() == BattlePokemonCommandType.ITEM;
        if(accepted && checkIsPlayerPokemonAllAction()){
            roundSettlement();
        }
        return accepted;
    }

    public boolean isFactionInBattle(byte factionIndex) {
        return factionIndex >= 0 && factionIndex < debutFactions.size()
                && debutFactions.get(factionIndex).getFactionStatType() == FactionResultType.IN_BATTLE;
    }
    public static class Builder{
        private BattleType battleType;
        private BattleFormatType battleFormatType;
        private BattleFormType battleFormType;
        private BattleFacilityType battleFacilityType;
        private FactionData selfFaction;
        private FactionData enemyFaction;
        private int battleAlreadyRunTime;
        public Builder setBattleType(BattleType battleType) {
            this.battleType = battleType;
            return this;
        }
        public Builder setBattleFormatType(BattleFormatType battleFormatType) {
            this.battleFormatType = battleFormatType;
            return this;
        }
        public Builder setBattleFormType(BattleFormType battleFormType) {
            this.battleFormType = battleFormType;
            return this;
        }
        public Builder setBattleFacilityType(BattleFacilityType battleFacilityType) {
            this.battleFacilityType = battleFacilityType;
            return this;
        }
        public Builder setSelfFaction(FactionData selfFaction) {
            this.selfFaction = selfFaction;
            return this;
        }
        public Builder setEnemyFaction(FactionData enemyFaction) {
            this.enemyFaction = enemyFaction;
            return this;
        }
        public Builder setBattleAlreadyRunTime(int battleAlreadyRunTime) {
            this.battleAlreadyRunTime = battleAlreadyRunTime;
            return this;
        }
        public BattleManager build(){
            return new BattleManager(battleType,battleFormatType,battleFormType,battleFacilityType,selfFaction,enemyFaction,battleAlreadyRunTime);
        }
    }
}
