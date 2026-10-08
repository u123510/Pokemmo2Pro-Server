package org.pokemmo.gameserver.game.battle;
import org.pokemmo.gameserver.codecs.BattleDebutPokemonCodec;
import org.pokemmo.gameserver.codecs.BattleTeamPokemonCodec;
import org.pokemmo.gameserver.game.battle.effect.*;
import org.pokemmo.gameserver.game.item.ItemData;
import org.pokemmo.gameserver.game.item.ItemManager;
import org.pokemmo.gameserver.game.move.MoveDamageType;
import org.pokemmo.gameserver.game.move.MoveManager;
import org.pokemmo.gameserver.game.move.PokemonMoveData;
import org.pokemmo.gameserver.game.character.CharacterManager;
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

class BattleContextState {
    public BattleBasisInfo battleBasisInfo = new BattleBasisInfo();
    public List<FactionData> debutFactions = new ArrayList<>(2);
    public final Random random = new Random();
    public final List<BattlePokemonData> actionPokemons = new ArrayList<>(0);
    private final BattleBroadcastService broadcastService = new BattleBroadcastService(this);
    private final BattleMoveResolver moveResolver = new BattleMoveResolver(this);
    private final BattleStatCalculator statCalculator = new BattleStatCalculator(this);
    private final BattleOutcomeResolver outcomeResolver = new BattleOutcomeResolver(this);
    private final BattleCaptureService captureService = new BattleCaptureService(this);
    private final BattleSwitchService switchService = new BattleSwitchService(this);
    private final BattleRoundSettlement roundSettlement = new BattleRoundSettlement(this);
    protected BattleContextState(BattleType battleType, BattleFormatType battleFormatType, BattleFormType battleFormType, BattleFacilityType battleFacilityType, FactionData selfFaction, FactionData enemyFaction, int battleAlreadyRunTime){
        this.battleBasisInfo.setBattleType(battleType);
        this.battleBasisInfo.setBattleFormatType(battleFormatType);
        this.battleBasisInfo.setBattleFormType(battleFormType);
        this.battleBasisInfo.setBattleFacilityType(battleFacilityType);
        this.battleBasisInfo.setBattleAlreadyRunTime(battleAlreadyRunTime);
        this.battleBasisInfo.setCanRun(battleType.getCanRun());
        this.battleBasisInfo.setCanUseItem(battleType.getCanUseItem());
        this.battleBasisInfo.setUseItemLimitAmount(battleType.getCanUseItemAmount());
        if (battleType == BattleType.PlayerBattle) {
            this.battleBasisInfo.setCanSurrender(0);
        }
        this.debutFactions.add(selfFaction);
        this.debutFactions.add(enemyFaction);
        if(battleType == BattleType.PlayerBattle || battleType == BattleType.CooperativeBossBattle){
            this.battleBasisInfo.setHasLimitMinute(true);
            this.battleBasisInfo.setLimitMinutes((byte)1);
        }
    }
    public List<Session> getBattlePlayerSessions(){
        List<Session> playerSessions = new ArrayList<>(0);
        for(FactionData faction :debutFactions){
            List<Session> factionPlayerSessions = faction.getFactionPlayerSessions();
            playerSessions.addAll(factionPlayerSessions);
        }
        return playerSessions;
    }
    public Session getPlayerSessionByFactionIndexAndTeamIndex(byte factionIndex,byte teamIndex){
        return debutFactions.get(factionIndex).getPlayerSessionByTeamIndex(teamIndex);
    }
    //检查是否是玩家阵营
    public boolean checkIsPlayerFaction(int factionIndex){
        return debutFactions.get(factionIndex).checkIsPlayerFaction();
    }
    //获取敌人的阵营
    public byte getEnemyFactionIndex(byte userFaction){
        return (byte)(1-userFaction);
    }
    //获取敌方阵营压迫感宝可梦数量
    public byte getFactionPressureAbilityAmount(byte factionIndex){
        byte pressureAbilityAmount = 0;
        for(BattlePokemonData pokemon : debutFactions.get(factionIndex).getDebutPokemons()){
            if(pokemon != null){
                if(pokemon.getPokemonAbilityIndexId() == 46){
                    pressureAbilityAmount++;
                }
            }
        }
        return pressureAbilityAmount;
    }
    //宝可梦使用技能
    public void pokemonUseMove(BattlePokemonData actionPokemon,short moveIndexId,byte targetFaction,byte targetPokemonInDebutIndex){
        moveResolver.pokemonUseMove(actionPokemon, moveIndexId, targetFaction, targetPokemonInDebutIndex);
    }
    public boolean canGainExp() {
        return battleBasisInfo.getBattleType() == BattleType.WildBattle
                || battleBasisInfo.getBattleType() == BattleType.LowTrainerBattle
                || battleBasisInfo.getBattleType() == BattleType.AceTrainerBattle
                || battleBasisInfo.getBattleType() == BattleType.RivalBattle
                || battleBasisInfo.getBattleType() == BattleType.GymLeaderBattle
                || battleBasisInfo.getBattleType() == BattleType.EliteBattle;
    }

    public void settleFaintedPokemonsExp() {
        if (!canGainExp()) {
            return;
        }
        Session hostSession = debutFactions.get(0).getPlayerSessionByTeamIndex(0);
        byte playerFactionIndex = hostSession != null ? getFactionIndexBySession(hostSession) : 0;
        for (int i = 0; i < debutFactions.size(); i++) {
            if (i == playerFactionIndex) {
                continue;
            }
            FactionData faction = debutFactions.get(i);
            for (BattlePokemonData pokemon : faction.getDebutPokemons()) {
                if (pokemon != null && pokemon.getPokemonData() != null
                        && pokemon.getPokemonData().getCurrentHp() == 0
                        && !pokemon.isExpSettled()) {
                    pokemon.setExpSettled(true);
                    BattlePokemonData defeater = pokemon.getDefeaterPokemon();
                    handlePokemonGainedExp(hostSession, defeater, pokemon);
                }
            }
        }
    }

    //处理玩家宝可梦的经验获取
    public void handlePokemonGainedExp(Session session, BattlePokemonData defeaterPokemon, BattlePokemonData defeatedPokemon){
        if (defeatedPokemon == null || defeatedPokemon.getPokemonData() == null) {
            return;
        }
        if (defeaterPokemon != null && defeaterPokemon.getPokemonData() != null
                && defeaterPokemon.getPokemonData().getCurrentHp() > 0
                && defeaterPokemon.getPokemonData().getLevel() < 100) {
            defeatedPokemon.getExpPoolPokemons().add(defeaterPokemon);
        }
        List<BattlePokemonData> eligiblePokemons = new ArrayList<>();
        for (BattlePokemonData p : defeatedPokemon.getExpPoolPokemons()) {
            if (p != null && p.getPokemonData() != null
                    && p.getPokemonData().getLevel() < 100
                    && p.getPokemonData().getCurrentHp() > 0
                    && (p.getOwnerSession() != null || session != null)) {
                eligiblePokemons.add(p);
            }
        }
        if (eligiblePokemons.isEmpty()) {
            return;
        }

        PokemonDexData targetPokemonDexData = defeatedPokemon.getPokemonData().getPokemonDexData();
        int defeaterLevel = (defeaterPokemon != null && defeaterPokemon.getPokemonData() != null)
                ? defeaterPokemon.getPokemonData().getLevel()
                : eligiblePokemons.get(0).getPokemonData().getLevel();
        int defeatedLevel = defeatedPokemon.getPokemonData().getLevel();
        double exc_porrected_parameter = defeatedLevel / (double) (defeatedLevel + defeaterLevel);
        if (exc_porrected_parameter < 0.5) {
            exc_porrected_parameter *= 1.5;
        }
        int baseYield = (targetPokemonDexData != null && targetPokemonDexData.getPokemonYield() != null)
                ? targetPokemonDexData.getPokemonYield().getBaseExp()
                : 50;
        int gainedBaseExp = (int) (defeatedLevel * baseYield / 7.0 * exc_porrected_parameter + 1);
        switch (battleBasisInfo.getBattleType()) {
            case WildBattle:
                break;
            case LowTrainerBattle:
            case AceTrainerBattle:
            case RivalBattle:
            case GymLeaderBattle:
            case EliteBattle:
                gainedBaseExp = (int) (gainedBaseExp * 1.5);
                break;
            default:
                break;
        }

        int baseExpPerPokemon = Math.max(1, (int) (gainedBaseExp / (double) eligiblePokemons.size()));
        for (BattlePokemonData gainedExpPokemon : eligiblePokemons) {
            Session targetSession = gainedExpPokemon.getOwnerSession() != null ? gainedExpPokemon.getOwnerSession() : session;
            if (targetSession == null) {
                continue;
            }
            PokemonGetExpData expInfo = new PokemonGetExpData();
            expInfo.setPokemonId(gainedExpPokemon.getPokemonData().getPokemonId());
            expInfo.setBaseExp(baseExpPerPokemon);
            int pokemonGainedExp = baseExpPerPokemon;
            if (battleBasisInfo.getBattleType().isPve()) {
                pokemonGainedExp += (int) (baseExpPerPokemon * expInfo.getTrainerBattleBonus());
                expInfo.setHasTrainerBattleBonus(true);
            }
            if (gainedExpPokemon.getPokemonData().getTrainerId() != gainedExpPokemon.getPokemonData().getOriginalTrainerId()) {
                pokemonGainedExp += (int) (baseExpPerPokemon * expInfo.getTradeBonus());
                expInfo.setHasTradeBonus(true);
            }
            int newExp = gainedExpPokemon.getPokemonData().getExp() + pokemonGainedExp;
            gainedExpPokemon.getPokemonData().setExp(newExp);
            PokemonDexData gainedExpPokemonDexData = gainedExpPokemon.getPokemonData().getPokemonDexData();
            int newLevel = gainedExpPokemonDexData != null && gainedExpPokemonDexData.getGetExpSpeedType() != null
                    ? gainedExpPokemonDexData.getGetExpSpeedType().getLevelByExp(newExp)
                    : gainedExpPokemon.getPokemonData().getLevel();
            gainedExpPokemon.getPokemonData().setLevel((short) newLevel);
            gainedExpPokemon.getPokemonData().setMaxHp(
                    PokemonManager.calculateMaxHp(gainedExpPokemon.getPokemonData()));

            short[] evYields = gainedExpPokemon.getPokemonData().getPokemonEvs();
            if (targetPokemonDexData != null && targetPokemonDexData.getPokemonYield() != null && evYields != null) {
                for (int i = 0; i < 6 && i < evYields.length; i++) {
                    short evYield = targetPokemonDexData.getPokemonYield().getEvYieldByIndex(i);
                    if (evYield > 0) {
                        evYields[i] = (short) Math.min(252, evYields[i] + evYield);
                    }
                }
                gainedExpPokemon.getPokemonData().setPokemonEvs(evYields);
            }

            // 关键时序：先发送 0x16 更新等级经验包，客户端在对战队列注册 LM 事件
            UpdatePokemonData updatePokemonData = new UpdatePokemonData.Builder()
                    .setUpdatePokemon(gainedExpPokemon.getPokemonData())
                    .setIsReloadPokemonLevel(true)
                    .setIsReloadPokemonCurrentHp(true)
                    .setIsReloadPokemonAbilityValue(true)
                    .setIsReloadPokemonEvs(true)
                    .build();
            targetSession.send(new SendUpdatePokemonDataPacket(updatePokemonData));

            // 关键时序：随后发送 0x79 经验获得明细包，客户端匹配 LM 并填入明细
            targetSession.send(new SendPokemonGetExpPacket(expInfo));

            // 持久化到数据库
            CharacterManager characterManager = targetSession.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get();
            if (characterManager != null && characterManager.getCharacterService() != null) {
                characterManager.getCharacterService().updatePokemonGrowth(
                        characterManager.getCharacterData().getPlayerEntity().getEntityGameId(),
                        gainedExpPokemon.getPokemonData().getPokemonId(),
                        newExp,
                        (short) newLevel,
                        evYields
                );
            }
        }
    }
    //根据会话获取阵营
    public byte getFactionIndexBySession(Session session){
        for(FactionData faction : debutFactions){
            if(faction.checkHasTargetSession(session)){
                return faction.getFactionIndex();
            }
        }
        return -1;
    }
    //广播所有行动宝可梦的无目标行动
    public void broadcastBattlePokemonActionWithNoSelector(BattlePokemonData actionPokemon){
        broadcastService.broadcastBattlePokemonActionWithNoSelector(actionPokemon);
    }
    //广播所有行动宝可梦的有目标行动
    public void broadcastBattlePokemonActionWithSelector() {
        broadcastService.broadcastBattlePokemonActionWithSelector();
    }
    //广播所有更换宝可梦的行动
    public void broadcastBattlePokemonSwap(BattlePokemonData actionPokemon) {
        broadcastService.broadcastBattlePokemonSwap(actionPokemon);
    }
    //广播回合更新
    public void broadcastBattleRoundUpdate() {
        broadcastService.broadcastBattleRoundUpdate();
    }
    //将登场的所有宝可梦重新加入行动列表
    public void addActionPokemons(){
        roundSettlement.addActionPokemons();
    }
    //计算技能效果
    public BasePokemonActionEffect computerMoveEffect(PokemonMoveData usedMoveData, MoveDamageType moveDamageType , BattlePokemonData actionPokemon, BattlePokemonData targetPokemon){
        return moveResolver.computerMoveEffect(usedMoveData, moveDamageType, actionPokemon, targetPokemon);
    }
    public void computeMoveTargetPokemons(BattlePokemonData actorPokemon,byte userFaction,byte userPokemonInDebutIndex,byte targetFaction,byte targetPokemonInDebutIndex,short usedMoveIndexId){
        moveResolver.computeMoveTargetPokemons(actorPokemon, userFaction, userPokemonInDebutIndex, targetFaction, targetPokemonInDebutIndex, usedMoveIndexId);
    }
    //计算宝可梦的攻击能力值
    public short getPokemonAttackStat(BattlePokemonData battlePokemonData){
        return statCalculator.getPokemonAttackStat(battlePokemonData);
    }
    //计算宝可梦的防御能力值
    public short getPokemonDefenseStat(BattlePokemonData battlePokemonData){
        return statCalculator.getPokemonDefenseStat(battlePokemonData);
    }
    public short getPokemonSpAttackStat(BattlePokemonData battlePokemonData){
        return statCalculator.getPokemonSpAttackStat(battlePokemonData);
    }
    public short getPokemonSpDefenseStat(BattlePokemonData battlePokemonData){
        return statCalculator.getPokemonSpDefenseStat(battlePokemonData);
    }

    public short getPokemonSpeedStat(BattlePokemonData battlePokemonData) {
        return statCalculator.getPokemonSpeedStat(battlePokemonData);
    }
    //计算技能是否命中
    public boolean computeMoveIsHit(short moveBaseAccuracy,BattlePokemonData actionPokemon,BattlePokemonData targetPokemon){
        return moveResolver.computeMoveIsHit(moveBaseAccuracy, actionPokemon, targetPokemon);
    }
    //检测对战所有所属玩家的宝可梦是否都已经行动了
    public boolean checkIsPlayerPokemonAllAction(){
        return outcomeResolver.checkIsPlayerPokemonAllAction();
    }
    //回合的超时结算
    public boolean roundTimeoutSettlement(BattleType battleType,Session hostSession){
        return outcomeResolver.roundTimeoutSettlement(battleType, hostSession);
    }
    //将指定玩家所在阵营直接结算为胜利
    public boolean forceVictory(Session session) {
        return outcomeResolver.forceVictory(session);
    }

    // 逃离野外战斗；选择器用于客户端显示对应阵营的逃跑消息。
    public boolean runAway(Session session, byte selectorData) {
        return outcomeResolver.runAway(session, selectorData);
    }

    // 玩家对战投降与野外逃跑使用不同的结果和胜负语义。
    public boolean forfeit(Session session, byte selectorData) {
        return outcomeResolver.forfeit(session, selectorData);
    }

    //检测战斗是否结束，结束则结算战斗
    public boolean checkAndHandleBattleFinish(Session hostSession){
        return outcomeResolver.checkAndHandleBattleFinish(hostSession);
    }
    //人机选择技能
    public void botUseMove(BattlePokemonData actionPokemon){
        roundSettlement.botUseMove(actionPokemon);
    }
    //更换宝可梦结算
    public void swapPokemonSettlement(){
        roundSettlement.swapPokemonSettlement();
    }
    //使用道具结算
    public boolean useItemSettlement(){
        return roundSettlement.useItemSettlement();
    }
    public boolean settleCapture(BattlePokemonData actionPokemon) {
        return captureService.settle(actionPokemon);
    }
    //在战斗指令之前的结算
    public void beforeRoundCommandSettlement(){
        roundSettlement.beforeRoundCommandSettlement();
    }
    public void InRoundCommandSettlement(){
        roundSettlement.InRoundCommandSettlement();
    }
    public void AfterRoundCommandSettlement(){
        roundSettlement.AfterRoundCommandSettlement();
    }
    //回合结算，当所有玩家的宝可梦行动完成时，结算当前回合的结果
    public void roundSettlement(){
        roundSettlement.roundSettlement();
    }

    public boolean isWaitingForFaintReplacement() {
        return switchService.isWaitingForFaintReplacement();
    }

    public boolean isWaitingForFaintReplacement(byte factionIndex, byte slotIndex) {
        return switchService.isSlotWaitingForFaintReplacement(factionIndex, slotIndex);
    }

    public boolean checkAndPromptFaintReplacements() {
        return switchService.checkAndPromptFaintReplacements();
    }

    public boolean handleFaintReplacementSwap(byte swapFaction, byte swapSlot, int swapIndex) {
        return switchService.handleFaintReplacementSwap(swapFaction, swapSlot, swapIndex);
    }

    public void executeInRoundSwap(BattlePokemonData actionPokemon) {
        switchService.executeInRoundSwap(actionPokemon);
    }

    public void startNextRound() {
        switchService.startNextRound();
    }

    public boolean isPokemonOnField(BattlePokemonData pokemon) {
        return switchService.isPokemonOnField(pokemon);
    }

    public boolean checkFactionHasAlive(int factionIndex) {
        return outcomeResolver.checkFactionHasAlive(factionIndex);
    }
}
