package org.pokemmo.gameserver.game.battle;
import org.pokemmo.gameserver.codecs.BattleDebutPokemonCodec;
import org.pokemmo.gameserver.codecs.BattleTeamPokemonCodec;
import org.pokemmo.gameserver.game.battle.effect.*;
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

final class BattleRoundSettlement extends BattleContextComponent {
    BattleRoundSettlement(BattleContextState context) {
        super(context);
    }

    public void addActionPokemons(){
        this.actionPokemons.clear();
        for(FactionData factionData :debutFactions){
            for (BattlePokemonData debutPokemon : factionData.getDebutAlivePokemons()){
                this.actionPokemons.add(debutPokemon);
            }
        }
    }

    public void botUseMove(BattlePokemonData actionPokemon){
        //如果敌人宝可梦未选择技能时
        if (actionPokemon != null) {
            if(actionPokemon.getBattlePokemonCommandType() == BattlePokemonCommandType.NULL){
                short botChoiceMove = actionPokemon.getPokemonData().choiceRandomMove();
                //TODO 需要对强化技能，其他技能进行选择阵营与目标宝可梦索引
                byte targetFaction = getEnemyFactionIndex(actionPokemon.getDebutFactionIndex());
                byte targetPokemonInDebutIndex = 0;
                pokemonUseMove(actionPokemon,botChoiceMove,targetFaction,targetPokemonInDebutIndex);
            }
        }
    }

    public void swapPokemonSettlement(){
        addActionPokemons();
        this.actionPokemons.sort((o1, o2) -> getPokemonSpeedStat(o1) - getPokemonSpeedStat(o2));
        for (BattlePokemonData actionPokemon : actionPokemons) {
            if (actionPokemon.getBattlePokemonCommandType() == BattlePokemonCommandType.SWAP) {
                executeInRoundSwap(actionPokemon);
            }
        }
    }

    public boolean useItemSettlement(){
        addActionPokemons();
        this.actionPokemons.sort((o1, o2) ->getPokemonSpeedStat(o1 )- getPokemonSpeedStat(o2));
        for(BattlePokemonData actionPokemon : actionPokemons) {
            if (actionPokemon.getBattlePokemonCommandType() == BattlePokemonCommandType.ITEM) {
                ItemData itemData = ItemManager.getItemData(actionPokemon.getRoundChoiceItemIndexId());
                BattlePokemonData useItemTargetPokemon = actionPokemon.getTargetPokemon();
                if (useItemTargetPokemon != null) {
                    if (battleBasisInfo.getBattleType() == BattleType.WildBattle
                            && itemData != null && itemData.isCaptureBall()
                            && settleCapture(actionPokemon)) {
                        checkAndHandleBattleFinish(actionPokemon.getOwnerSession());
                        return true;
                    }
                    switch (actionPokemon.getRoundChoiceItemIndexId()){
                        //TODO 实现使用道具的效果

                    }
                }
            }
        }
        return false;
    }

    public void beforeRoundCommandSettlement(){
        //重新添加行动宝可梦到行动列表
        addActionPokemons();
        //结算指令前不考虑道具，场地，以及特性的加速
        this.actionPokemons.sort((o1, o2) ->getPokemonSpeedStat(o1 )- getPokemonSpeedStat(o2));
        for(BattlePokemonData actionPokemon : actionPokemons)
        {
            //先对特性进行结算
            if(actionPokemon.isFirstRoundDebut()){
                //设置宝可梦不再是第一回合登场
                actionPokemon.setFirstRoundDebut(false);
                if(PokemonManager.getDebutDirectTriggerAbility().contains(actionPokemon.getPokemonAbilityIndexId()))
                {
                    byte actionPokemonFaction = actionPokemon.getDebutFactionIndex();
                    byte enemyPokemonFaction = getEnemyFactionIndex(actionPokemonFaction);
                    byte enemyFactionAlivePokemonAmount = (byte)debutFactions.get(enemyPokemonFaction).getDebutAlivePokemonSize();
                    switch (actionPokemon.getPokemonAbilityIndexId()){
                        case 22://威吓
                            byte factionPokemonsStatChangeValues[][] = new byte[enemyFactionAlivePokemonAmount][1];
                            //遍历所有敌方宝可梦，与攻击的强化等级，攻击不是最低等级，则进行强化等级下降
                            for (int i = 0; i < enemyFactionAlivePokemonAmount; i++) {
                                BattlePokemonData enemyPokemon = debutFactions.get(enemyPokemonFaction).getDebutPokemons()[i];
                                if(enemyPokemon.getStaticStats()[PokemonStatType.ATTACK.getType()] !=-6){
                                    factionPokemonsStatChangeValues[i] = new byte[]{-1};
                                    //攻击强化等级下降
                                    enemyPokemon.getStaticStats()[PokemonStatType.ATTACK.getType()]--;
                                }
                                else{
                                    factionPokemonsStatChangeValues[i] = new byte[]{0};
                                }
                            }
                            //设置威吓效果的技能效果展示
                            BattlePokemonAbilityTriggerEffect pokemonIntimidateAbilityTriggerEffect = new BattlePokemonAbilityTriggerEffect.Builder().
                                    setActorPokemonAbilityIndexId(actionPokemon.getPokemonAbilityIndexId()).
                                    setShowAbilityPokemonId(actionPokemon.getPokemonData().getPokemonId()).
                                    build();
                            actionPokemon.getCauseWithNoTargetActions().add(new BattlePokemonCauseAction(0, 0, pokemonIntimidateAbilityTriggerEffect));
                            //设置队伍攻击强化等级下降的效果
                            BattleFactionStatChangeEffect battleFactionStatChangeEffect = new BattleFactionStatChangeEffect.Builder().
                                    setFactionIndex(getEnemyFactionIndex(actionPokemon.getDebutFactionIndex())).
                                    setChangeStatTypes(new PokemonStatType[]{PokemonStatType.ATTACK}).
                                    setBattleTeamStatChangeTargetLevel((byte)-1).
                                    setFactionPokemonsStatChangeValues(factionPokemonsStatChangeValues).
                                    build();
                            actionPokemon.getCauseWithNoTargetActions().add(new BattlePokemonCauseAction(0, 0, battleFactionStatChangeEffect));
                            break;
                        case 36://复制
                            int randomCopyPokemonIndex = random.nextInt(debutFactions.get(enemyPokemonFaction).getDebutAlivePokemonSize());
                            long copyTargetPokemonId = debutFactions.get(enemyPokemonFaction).getDebutPokemons()[randomCopyPokemonIndex].getPokemonData().getPokemonId();
                            short targetPokemonAbilityIndexId = debutFactions.get(enemyPokemonFaction).getDebutPokemons()[randomCopyPokemonIndex].getPokemonAbilityIndexId();
                            BattlePokemonAbilityTriggerEffect pokemonCopyAbilityTriggerEffect = new BattlePokemonAbilityTriggerEffect.Builder().
                                    setActorPokemonAbilityIndexId(actionPokemon.getPokemonAbilityIndexId()).
                                    setShowAbilityPokemonId(actionPokemon.getPokemonData().getPokemonId()).
                                    setAbilityTargetPokemonId(copyTargetPokemonId).
                                    setCopyEnemyAbility(targetPokemonAbilityIndexId).
                                    build();
                            actionPokemon.getCauseWithNoTargetActions().add(new BattlePokemonCauseAction(0, 0, pokemonCopyAbilityTriggerEffect));
                        case 2://降雨
                        case 45://扬沙
                        case 70://日照
                        case 76://气闸
                        case 117://降雪
                            BattleWeatherType weatherType = BattleWeatherAbilityMapper.ABILITY_WEATHER_MAP.get(actionPokemon.getPokemonAbilityIndexId());
                            //设置扬沙效果的技能效果展示
                            BattlePokemonAbilityTriggerEffect pokemonWeatherAbilityTriggerEffect = new BattlePokemonAbilityTriggerEffect.Builder().
                                    setActorPokemonAbilityIndexId(actionPokemon.getPokemonAbilityIndexId()).
                                    setShowAbilityPokemonId(actionPokemon.getPokemonData().getPokemonId()).
                                    build();
                            actionPokemon.getCauseWithNoTargetActions().add(new BattlePokemonCauseAction(0, 0, pokemonWeatherAbilityTriggerEffect));
                            BattleWeatherChangeEffect weatherEffect = new BattleWeatherChangeEffect.Builder().
                                    setBattleWeatherType(weatherType).
                                    build();
                            actionPokemon.getCauseWithNoTargetActions().add(new BattlePokemonCauseAction(0, 0, weatherEffect));
                            break;
                        case 108://预知梦
                            int randomForeWarnPokemonIndex = random.nextInt(debutFactions.get(enemyPokemonFaction).getDebutAlivePokemonSize());
                            int moveSize = 0;
                            for(short moveIndexId:debutFactions.get(enemyPokemonFaction).getDebutPokemons()[randomForeWarnPokemonIndex].getPokemonData().getMoves()){
                                if(moveIndexId != 0){
                                    moveSize++;
                                }
                            }
                            int randomForeWarnMoveIndex = random.nextInt(moveSize);
                            short moveIndexId = debutFactions.get(enemyPokemonFaction).getDebutPokemons()[randomForeWarnPokemonIndex].getPokemonData().getMoves()[randomForeWarnMoveIndex];
                            long foreWarnTargetPokemonId = debutFactions.get(enemyPokemonFaction).getDebutPokemons()[randomForeWarnPokemonIndex].getPokemonData().getPokemonId();
                            BattlePokemonAbilityTriggerEffect pokemonForeWarnAbilityTriggerEffect = new BattlePokemonAbilityTriggerEffect.Builder().
                                    setActorPokemonAbilityIndexId(actionPokemon.getPokemonAbilityIndexId()).
                                    setShowAbilityPokemonId(actionPokemon.getPokemonData().getPokemonId()).
                                    build();
                            actionPokemon.getCauseWithNoTargetActions().add(new BattlePokemonCauseAction(0, 0, pokemonForeWarnAbilityTriggerEffect));
                            BattlePokemonForeWarnEffect pokemonForeWarnEffect = new BattlePokemonForeWarnEffect.Builder().
                                    setForeWarnMoveIndexId(moveIndexId).
                                    setReloadEffectTargetPokemonId(foreWarnTargetPokemonId).
                                    build();
                            actionPokemon.getCauseWithNoTargetActions().add(new BattlePokemonCauseAction(0, 0, pokemonForeWarnEffect));
                            break;
                        case 46://压迫感
                        case 127://紧张感
                            BattlePokemonAbilityTriggerEffect pokemonAbilityTriggerEffect = new BattlePokemonAbilityTriggerEffect.Builder().
                                    setActorPokemonAbilityIndexId(actionPokemon.getPokemonAbilityIndexId()).
                                    setShowAbilityPokemonId(actionPokemon.getPokemonData().getPokemonId()).
                                    build();
                            actionPokemon.getCauseWithNoTargetActions().add(new BattlePokemonCauseAction(0, 0, pokemonAbilityTriggerEffect));
                            break;
                        case 88://下载
                            //计算敌方宝可梦的防御与特防能力值之和
                            short enemyTotalDefense = 0;
                            short enemyTotalSpDefense = 0;
                            for (int i = 0; i < enemyFactionAlivePokemonAmount; i++) {
                                BattlePokemonData enemyPokemon = debutFactions.get(enemyPokemonFaction).getDebutPokemons()[i];
                                enemyTotalDefense += getPokemonDefenseStat(enemyPokemon);
                                enemyTotalSpDefense += getPokemonSpDefenseStat(enemyPokemon);
                            }
                            BattlePokemonStatChangeEffect pokemonDownloadAbilityTriggerEffect = new BattlePokemonStatChangeEffect.Builder().
                                    setReloadEffectActorPokemonId(actionPokemon.getPokemonData().getPokemonId()).
                                    build();
                            actionPokemon.getCauseWithNoTargetActions().add(new BattlePokemonCauseAction(0, 0, pokemonDownloadAbilityTriggerEffect));
                            //检测敌人的总共防御与特防能力值，对相应能力进行提升
                            if(enemyTotalDefense<= enemyTotalSpDefense){
                                BattlePokemonStatChangeEffect pokemonStatChangeEffect = new BattlePokemonStatChangeEffect.Builder().
                                        setReloadEffectTargetPokemonId(actionPokemon.getPokemonData().getPokemonId()).
                                        setStatChangeType(PokemonStatChangeType.NORMAL_CHANGE).
                                        setStatType(PokemonStatType.ATTACK).
                                        setTargetChangeValue(1).
                                        setActualChangeValue(1).
                                        build();
                                actionPokemon.getCauseWithNoTargetActions().add(new BattlePokemonCauseAction(0, 0, pokemonStatChangeEffect));
                            }
                            else{
                                BattlePokemonStatChangeEffect pokemonStatChangeEffect = new BattlePokemonStatChangeEffect.Builder().
                                        setReloadEffectTargetPokemonId(actionPokemon.getPokemonData().getPokemonId()).
                                        setStatChangeType(PokemonStatChangeType.NORMAL_CHANGE).
                                        setStatType(PokemonStatType.SPECIAL_ATTACK).
                                        setTargetChangeValue(1).
                                        setActualChangeValue(1).
                                        build();
                                actionPokemon.getCauseWithNoTargetActions().add(new BattlePokemonCauseAction(0, 0, pokemonStatChangeEffect));
                            }
                    }
                }
                //对不听话的宝可梦进行结算
                if(battleBasisInfo.getBattleType().isPve() || battleBasisInfo.getBattleType() == BattleType.WildBattle){
                    Session ownerSession = actionPokemon.getOwnerSession();
                    if(ownerSession != null){
                        byte badgeLimitLevel = ownerSession.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get().getCurrentBadgeLimitLevel();
                        if(actionPokemon.getPokemonData().getLevel() > badgeLimitLevel){
                            BattlePokemonDisobeyEffect pokemonDisobeyEffect = new BattlePokemonDisobeyEffect.Builder().
                                    setDisobeyType(PokemonDisobeyType.ATTACK_SELF).
                                    build();
                            actionPokemon.getCauseWithNoTargetActions().add(new BattlePokemonCauseAction(actionPokemon.getPokemonData().getPokemonId(), actionPokemon.getPokemonData().getPokemonId(), pokemonDisobeyEffect));
                            BattlePokemonRemainHpEffect pokemonRemainHpEffect = new BattlePokemonRemainHpEffect.Builder().
                                    setRemainHp((short)0).
                                    build();
                            actionPokemon.getCauseWithNoTargetActions().add(new BattlePokemonCauseAction(actionPokemon.getPokemonData().getPokemonId(), actionPokemon.getPokemonData().getPokemonId(), pokemonRemainHpEffect));
                        }
                    }
                }
                //广播所有行动宝可梦的无目标行动
                if(!actionPokemon.getCauseWithNoTargetActions().isEmpty()){
                    broadcastBattlePokemonActionWithNoSelector(actionPokemon);
                }
            }
        }
    }

    public void InRoundCommandSettlement(){
        //重新添加行动宝可梦到行动列表
        addActionPokemons();
        //结算指令前不考虑道具，场地，以及特性的加速
        this.actionPokemons.sort((o1, o2) -> {
            // 获取双方的技能优先度
            byte priority1 = MoveManager.getPokemonMove(o1.getRoundChoiceMoveIndexId()).getMovePriority();
            byte priority2 = MoveManager.getPokemonMove(o2.getRoundChoiceMoveIndexId()).getMovePriority();
            // 优先度不同时，优先度大的排在前面
            if (priority1 != priority2) {
                return priority2 - priority1;
            }
            // 优先度相同时，按速度排序
            int speed1 = getPokemonSpeedStat(o1);
            int speed2 = getPokemonSpeedStat(o2);
            if (battleBasisInfo.getBattlePublicFieldInfo().isHasTrickRoom()&& battleBasisInfo.getBattlePublicFieldInfo().getTrickRoomRemainRound()>0) {
                // 戏法空间：同优先度内速度慢的在前
                return speed1 - speed2;
            } else {
                // 正常情况：同优先度内速度快的在前
                return speed2 - speed1;
            }
        });
        for (BattlePokemonData actionPokemon : actionPokemons){
            if(actionPokemon.getBattlePokemonCommandType() == BattlePokemonCommandType.MOVE)
            {
                if (actionPokemon.getPokemonData().getCurrentHp() > 0) {
                    //更新行动宝可梦上一轮使用的技能
                    actionPokemon.setLastUseSkillIndexId(actionPokemon.getRoundChoiceMoveIndexId());
                    //设置行动宝可梦已使用过技能
                    actionPokemon.setAlreadyUseSkill(true);
                    short usedMoveIndexId = actionPokemon.getRoundChoiceMoveIndexId();
                    PokemonMoveData usedMoveData = MoveManager.getPokemonMove(usedMoveIndexId);
                    MoveDamageType moveDamageType = usedMoveData.getMoveDamageType();
                    //获取技能的基础命中率
                    short moveBaseAccuracy = usedMoveData.getMoveBaseAccuracy();
                    for (BattlePokemonData targetPokemon : actionPokemon.getWithSelectorTargetPokemons()){
                        //检查技能是否有伤害
                        if (usedMoveData.getMoveBaseAccuracy() > 0) {
                            //获取技能的击打次数
                            int moveAttackTimes = getMoveAttackTimes(usedMoveIndexId);
                            for (int i = 0; i < moveAttackTimes; i++) {
                                //检测技能是否失败
                                if(targetPokemon.getPokemonData().getCurrentHp()==0){
                                    targetPokemon.updateSufferFlag(PokemonSufferType.FALSE.getType());
                                    break;
                                }
                                //使用的技能是寄生种子
                                if(usedMoveIndexId ==73){
                                    //目标是草系或者已经被寄生了，不生效
                                    if(targetPokemon.getPokemonFirstType() == PokemonType.GRASS || targetPokemon.getPokemonSecondType() == PokemonType.GRASS || targetPokemon.isInLeechSeed()){
                                        targetPokemon.updateSufferFlag(PokemonSufferType.FALSE.getType());
                                        break;
                                    }
                                }
                                //当技能不是必中技能且行动宝可梦不是无防守特性时，才对命中进行计算
                                if(moveBaseAccuracy<101 && actionPokemon.getPokemonAbilityIndexId() != 99){
                                    if (!computeMoveIsHit(moveBaseAccuracy, actionPokemon, targetPokemon)) {
                                        //处理没打中
                                        if (moveAttackTimes == 1) {
                                            targetPokemon.updateSufferFlag(PokemonSufferType.MISS.getType());
                                            targetPokemon.getSufferActions().add(null);
                                        }
                                        continue;
                                    }
                                }
                                BasePokemonActionEffect pokemonActionEffect = computerMoveEffect(usedMoveData, moveDamageType, actionPokemon, targetPokemon);
                                targetPokemon.getSufferActions().add(pokemonActionEffect);
                                //如果目标宝可梦血量为0，且击杀者未设置，设置击杀者为行动方宝可梦
                                if (targetPokemon.getPokemonData().getCurrentHp() == 0 && targetPokemon.getDefeaterPokemon() == null) {
                                    targetPokemon.setDefeaterPokemon(actionPokemon);
                                }
                            }
                        }
                    }
                }
                if(actionPokemon.getOwnerSession() != null){
                    byte choiceMovePos = actionPokemon.getPokemonData().getMovePos(actionPokemon.getRoundChoiceMoveIndexId());
                    actionPokemon.getOwnerSession().send(new SendBattlePokemonUpdateMovePpPacket(actionPokemon.getPokemonData().getPokemonId(),choiceMovePos,actionPokemon.getPokemonData().getMoveRemainPpByMoveIndexId(actionPokemon.getRoundChoiceMoveIndexId())));
                }
            }
        }
        //对所有玩家广播有目标的行动结果
        broadcastBattlePokemonActionWithSelector();
        //对战中被击败宝可梦的经验结算
        settleFaintedPokemonsExp();
    }

    public void AfterRoundCommandSettlement(){
        //重新添加行动宝可梦到行动列表
        addActionPokemons();
        //清除行动宝可梦的数据
        for(BattlePokemonData actionPokemon : actionPokemons){
            actionPokemon.clearDataAfterRound();
        }
        //更新天气剩余回合
        if(battleBasisInfo.getBattlePublicFieldInfo().isHasWeather()){
            BattleWeatherType battleWeatherType = battleBasisInfo.getBattlePublicFieldInfo().getBattleWeatherType();
            int weatherRemainRound = battleBasisInfo.getBattlePublicFieldInfo().getWeatherRemainRound();
            if(weatherRemainRound>1){
                battleBasisInfo.getBattlePublicFieldInfo().setPublicWeather(battleWeatherType,weatherRemainRound-1);
            }
            else{
                battleBasisInfo.getBattlePublicFieldInfo().setPublicWeather(BattleWeatherType.NORMAL,0);
            }
        }
        //更新戏法空间剩余回合
        if(battleBasisInfo.getBattlePublicFieldInfo().isHasTrickRoom()){
           byte trickRoomRemainRound = battleBasisInfo.getBattlePublicFieldInfo().getTrickRoomRemainRound();
           if(trickRoomRemainRound>1){
               battleBasisInfo.getBattlePublicFieldInfo().setTrickRoomData(trickRoomRemainRound-1);
           }
           else{
               battleBasisInfo.getBattlePublicFieldInfo().setTrickRoomData(0);
           }
        }
        //更新奇妙空间剩余回合
        if(battleBasisInfo.getBattlePublicFieldInfo().isHasWonderRoom()){
            byte wonderRoomRemainRound = battleBasisInfo.getBattlePublicFieldInfo().getWonderRoomRemainRound();
            if(wonderRoomRemainRound>1){
                battleBasisInfo.getBattlePublicFieldInfo().setWonderRoomData(wonderRoomRemainRound-1);
            }
            else {
                battleBasisInfo.getBattlePublicFieldInfo().setWonderRoomData(0);
            }
        }
        //更新魔法空间剩余回合
        if(battleBasisInfo.getBattlePublicFieldInfo().isHasMagicRoom()){
            byte magicRoomRemainRound = battleBasisInfo.getBattlePublicFieldInfo().getMagicRoomRemainRound();
            if(magicRoomRemainRound>1){
                battleBasisInfo.getBattlePublicFieldInfo().setMagicRoomData(magicRoomRemainRound-1);
            }
            else {
                battleBasisInfo.getBattlePublicFieldInfo().setMagicRoomData(0);
            }
        }
        //更新重力场剩余回合
        if(battleBasisInfo.getBattlePublicFieldInfo().isHasGravityField()){
            byte gravityFieldRemainRound = battleBasisInfo.getBattlePublicFieldInfo().getGravityFieldRemainRound();
            if(gravityFieldRemainRound>1){
                battleBasisInfo.getBattlePublicFieldInfo().setGravityFieldData(gravityFieldRemainRound-1);
            }
            else {
                battleBasisInfo.getBattlePublicFieldInfo().setGravityFieldData(0);
            }
        }
        //更新反转对战剩余回合
        if(battleBasisInfo.getBattlePublicFieldInfo().isHasInverseBattleEffect()){
            byte inverseBattleEffectRemainRound = battleBasisInfo.getBattlePublicFieldInfo().getInverseBattleEffectRemainRound();
            if(inverseBattleEffectRemainRound>1){
                battleBasisInfo.getBattlePublicFieldInfo().setInverseBattleEffect(inverseBattleEffectRemainRound-1);
            }
            else {
                battleBasisInfo.getBattlePublicFieldInfo().setInverseBattleEffect(0);
            }
        }
        //更新伤害类型反转剩余回合
        if(battleBasisInfo.getBattlePublicFieldInfo().isHasDamageTypeReverseEffect()){
            byte damageTypeReverseEffectRemainRound = battleBasisInfo.getBattlePublicFieldInfo().getDamageTypeReverseEffectRemainRound();
            if(damageTypeReverseEffectRemainRound>1){
                battleBasisInfo.getBattlePublicFieldInfo().setDamageTypeReverseEffect(damageTypeReverseEffectRemainRound-1);
            }
            else {
                battleBasisInfo.getBattlePublicFieldInfo().setDamageTypeReverseEffect(0);
            }
        }
    }

    private int getMoveAttackTimes(short usedMoveIndexId){
        switch (usedMoveIndexId){
            //连环巴掌
            case 3:
                //连续拳
            case 4:
                return random.nextInt(2,5);
            default:
                return 1;
        }
    }

    public void roundSettlement(){
        Session hostSession = debutFactions.get(0).getPlayerSessionByTeamIndex(0);
        //对交换宝可梦进行结算
        swapPokemonSettlement();
        //检测是否对战结束
        if (checkAndHandleBattleFinish(hostSession)) {
            return;
        }
        //对使用道具进行结算
        if (useItemSettlement()) {
            return;
        }
        //指令前的结算
        beforeRoundCommandSettlement();
        //检测是否对战结束
        if (checkAndHandleBattleFinish(hostSession)) {
            return;
        }
        if(battleBasisInfo.getBattleRoundAmount() >0){
            for (int i = 0; i < actionPokemons.size(); i++) {
                BattlePokemonData battlePokemon = actionPokemons.get(i);
                if(battlePokemon.getOwnerSession() == null){
                    botUseMove(battlePokemon);
                }
            }
        }
        //指令的结算
        InRoundCommandSettlement();
        //检测是否对战结束
        if (checkAndHandleBattleFinish(hostSession)) {
            return;
        }
        //回合结束的结算
        AfterRoundCommandSettlement();
        //检测回合末效果致死后的胜负
        if (checkAndHandleBattleFinish(hostSession)) {
            return;
        }
        //检测是否有宝可梦阵亡需要替补
        if (checkAndPromptFaintReplacements()) {
            return;
        }
        //无濒死替补等待，正常开启下一回合
        startNextRound();
    }
}

