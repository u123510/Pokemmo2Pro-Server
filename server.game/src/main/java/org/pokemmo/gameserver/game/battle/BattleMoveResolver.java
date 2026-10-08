package org.pokemmo.gameserver.game.battle;
import lombok.extern.slf4j.Slf4j;
import org.pokemmo.gameserver.game.battle.effect.BasePokemonActionEffect;
import org.pokemmo.gameserver.game.battle.effect.BattlePokemonLeechedSeedEffect;
import org.pokemmo.gameserver.game.battle.effect.BattlePokemonRemainHpEffect;
import org.pokemmo.gameserver.game.battle.effect.BattlePokemonStatChangeEffect;
import org.pokemmo.gameserver.game.move.MoveDamageType;
import org.pokemmo.gameserver.game.move.MoveManager;
import org.pokemmo.gameserver.game.move.PokemonMoveData;
import org.pokemmo.gameserver.game.pokemon.PokemonStatType;

import java.util.Arrays;

@Slf4j
final class BattleMoveResolver extends BattleContextComponent {
    private final BattleDamageCalculator damageCalculator;

    BattleMoveResolver(BattleContextState context) {
        super(context);
        this.damageCalculator = new BattleDamageCalculator(context);
    }

    public void pokemonUseMove(BattlePokemonData actionPokemon,short moveIndexId,byte targetFaction,byte targetPokemonInDebutIndex){
        if (actionPokemon != null){
            if(actionPokemon.getPokemonData().getCurrentHp() > 0){
                byte movePos = actionPokemon.getPokemonData().getMovePos(moveIndexId);
                log.debug("[战斗技能] 宝可梦ID={} 请求使用技能ID={} | 当前招式槽={}",
                        actionPokemon.getPokemonData().getPokemonId(),
                        moveIndexId,
                        Arrays.toString(actionPokemon.getPokemonData().getMoves()));
                //判断技能位置是否合法
                if(movePos != -1) {
                    short remainPp = actionPokemon.getPokemonData().getMovesPp()[movePos];
                    log.debug("[战斗技能] 找到技能位置={} 剩余PP={}", movePos, remainPp);
                    if(remainPp > 0){
                        byte enemyFaction = getEnemyFactionIndex(actionPokemon.getDebutFactionIndex());
                        //设置行动宝可梦的技能剩余pp
                        actionPokemon.getPokemonData().setMoveRemainPpByPressureAbilityComputer(movePos,getFactionPressureAbilityAmount(enemyFaction));
                        //计算行动宝可梦的目标列表
                        computeMoveTargetPokemons(actionPokemon,actionPokemon.getDebutFactionIndex(),actionPokemon.getDebutIndex(),targetFaction,targetPokemonInDebutIndex,moveIndexId);
                        //检测并设置当前回合使用的技能
                        boolean canUse = actionPokemon.checkMoveCanUse(moveIndexId);
                        log.debug("[战斗技能] checkMoveCanUse={} (道具锁技能={} 挑衅={})",
                                canUse, actionPokemon.isItemLockSkill(), actionPokemon.isTaunt());
                        if(canUse){
                            actionPokemon.setAlreadyUseSkill(true);
                            actionPokemon.setBattlePokemonCommandType(BattlePokemonCommandType.MOVE);
                            actionPokemon.setRoundChoiceMoveIndexId(moveIndexId);
                            log.debug("[战斗技能] 已确认：宝可梦ID={} 使用技能ID={}",
                                    actionPokemon.getPokemonData().getPokemonId(), moveIndexId);
                        } else {
                            log.debug("[战斗技能] 被拦截（挑衅/道具锁定）：宝可梦ID={} 技能ID={}",
                                    actionPokemon.getPokemonData().getPokemonId(), moveIndexId);
                        }
                    } else {
                        log.debug("[战斗技能] PP不足：宝可梦ID={} 技能ID={} 位置={} PP={}",
                                actionPokemon.getPokemonData().getPokemonId(), moveIndexId, movePos, remainPp);
                    }
                } else {
                    log.debug("[战斗技能] 技能ID={}不在招式槽中：宝可梦ID={} 实际招式槽={}",
                            moveIndexId,
                            actionPokemon.getPokemonData().getPokemonId(),
                            Arrays.toString(actionPokemon.getPokemonData().getMoves()));
                }
            } else {
                log.debug("[战斗技能] 宝可梦ID={}已倒下(HP={})，跳过",
                        actionPokemon.getPokemonData().getPokemonId(),
                        actionPokemon.getPokemonData().getCurrentHp());
            }
        }
    }

    public BasePokemonActionEffect computerMoveEffect(PokemonMoveData usedMoveData, MoveDamageType moveDamageType , BattlePokemonData actionPokemon, BattlePokemonData targetPokemon){
        if (moveDamageType == MoveDamageType.PHYSICAL || moveDamageType == MoveDamageType.SPECIAL) {
            return applyCalculatedDamage(usedMoveData, moveDamageType, actionPokemon, targetPokemon);
        }
        BasePokemonActionEffect moveEffect = null;
        if(actionPokemon.getPokemonData().getCurrentHp()>0 && targetPokemon.getPokemonData().getCurrentHp()>0){
            switch (moveDamageType)
            {
                case STATUS:
                {

                    switch (usedMoveData.getMoveIndexId()) {
                        case 73://寄生种子
                             BattlePokemonLeechedSeedEffect battlePokemonLeechedSeedEffect = new BattlePokemonLeechedSeedEffect.Builder()
                                     .setReloadEffectActorPokemonId(actionPokemon.getPokemonData().getPokemonId())
                                     .setReloadEffectTargetPokemonId(targetPokemon.getPokemonData().getPokemonId())
                                     .setIsInLeechSeed(targetPokemon.isInLeechSeed())
                                     .build();
                             //更新宝可梦的寄生种子状态
                             targetPokemon.setInLeechSeed(true);
                             //更新寄生者的宝可梦登场选择器
                             targetPokemon.setLeecherPokemonDebutSelector(actionPokemon.getSelectorData());
                            moveEffect = battlePokemonLeechedSeedEffect;
                            return moveEffect;
                        //单一增益减益类型
                        case 14: //剑舞
                        case 28://泼沙
                        case 39://摇尾巴
                        case 43://瞪眼
                        case 45://叫声
                        case 81://吐丝
                        case 96://瑜伽姿势
                        case 103://刺耳声
                        case 104://影子分身
                        case 106://变硬
                        case 107://变小
                        case 108://烟幕
                        case 111://变圆
                        case 112://屏障
                        case 133://瞬间失忆
                        case 159://棱角化
                        case 204://撒娇
                        case 336://长嚎
                            byte targetPokemonStatArray[] = targetPokemon.getStaticStats();
                            BattlePokemonStatChangeEffect battlePokemonStatChangeEffect;
                            byte firstStatChangeType = usedMoveData.getMoveChangeStatTypeArray()[0];
                            //获取改变宝可梦强化等级的类型
                            PokemonStatType singleChangeStatType = PokemonStatType.getDisplayByType(firstStatChangeType);
                            //获取目标宝可梦的指定类型强化等级
                            byte targetPokemonStatLevel = targetPokemonStatArray[singleChangeStatType.getType()];
                            //获取技能的目标强化等级
                            int targetChangeStatLevel = usedMoveData.getMoveChangeStatLevelArray()[0];
                            //计算实际应该改变的强化等级
                            int actualSingleStatChangeValue = 0;
                            if (targetChangeStatLevel > 0) {
                                actualSingleStatChangeValue = Math.min(6 - targetPokemonStatLevel, targetChangeStatLevel);
                            }
                            if (targetChangeStatLevel < 0) {
                                actualSingleStatChangeValue = Math.max(-6 - targetPokemonStatLevel, targetChangeStatLevel);
                            }
                            //重新设置强化等级
                            targetPokemon.getStaticStats()[singleChangeStatType.getType()] = (byte)(targetPokemonStatLevel + actualSingleStatChangeValue);
                            battlePokemonStatChangeEffect = new BattlePokemonStatChangeEffect.Builder()
                                    .setReloadEffectActorPokemonId(actionPokemon.getPokemonData().getPokemonId())
                                    .setReloadEffectTargetPokemonId(targetPokemon.getPokemonData().getPokemonId())
                                    .setStatChangeType(PokemonStatChangeType.NORMAL_CHANGE)
                                    .setStatType(singleChangeStatType)
                                    .setTargetChangeValue(targetChangeStatLevel)
                                    .setActualChangeValue(actualSingleStatChangeValue)
                                    .build();
                            moveEffect = battlePokemonStatChangeEffect;
                            return moveEffect;
                    }
                }
                default:
                    break;
            }
        }
        return moveEffect;
    }

    private BasePokemonActionEffect applyCalculatedDamage(PokemonMoveData usedMoveData,
                                                           MoveDamageType moveDamageType,
                                                           BattlePokemonData actionPokemon,
                                                           BattlePokemonData targetPokemon) {
        if (usedMoveData == null || actionPokemon == null || targetPokemon == null
                || actionPokemon.getPokemonData() == null || targetPokemon.getPokemonData() == null
                || actionPokemon.getPokemonData().getCurrentHp() <= 0
                || targetPokemon.getPokemonData().getCurrentHp() <= 0) {
            return null;
        }
        int targetCount = Math.max(1, actionPokemon.getWithSelectorTargetPokemons().size());
        BattleDamageCalculator.DamageResult result = damageCalculator.calculate(
                usedMoveData, moveDamageType, actionPokemon, targetPokemon, targetCount);
        int damageTaken = result.damage();
        targetPokemon.setLastDamageTaken(damageTaken, damageTaken > 0 ? moveDamageType : null);
        short remainHp = (short) Math.max(0, targetPokemon.getPokemonData().getCurrentHp() - result.damage());
        targetPokemon.getPokemonData().setCurrentHp(remainHp);
        return new BattlePokemonRemainHpEffect.Builder()
                .setReloadEffectTargetPokemonId(targetPokemon.getPokemonData().getPokemonId())
                .setRemainHp(remainHp)
                .build();
    }

    public void computeMoveTargetPokemons(BattlePokemonData actorPokemon,byte userFaction,byte userPokemonInDebutIndex,byte targetFaction,byte targetPokemonInDebutIndex,short usedMoveIndexId){
        //获取使用技能的信息
        PokemonMoveData usedMoveData = MoveManager.getPokemonMove(usedMoveIndexId);
        //获取技能的目标类型
        switch (usedMoveData.getMoveTargetType()){
            //任意宝可梦除自己
            case ANY_POKEMON_EXCEPT_SELF:
                actorPokemon.getWithSelectorTargetPokemons().add(debutFactions.get(targetFaction).getDebutPokemons()[targetPokemonInDebutIndex]);
                break;
            //周围所有宝可梦,并移除自身
            case ALL_AROUND_POKEMON:
                actorPokemon.getWithSelectorTargetPokemons().addAll(Arrays.asList(debutFactions.get(userFaction).getDebutPokemons()));
                actorPokemon.getWithSelectorTargetPokemons().addAll(Arrays.asList(debutFactions.get(targetFaction).getDebutPokemons()));
                actorPokemon.getWithSelectorTargetPokemons().remove(actorPokemon);
                break;
            //周围所有敌人
            case ALL_AROUND_ENEMY:
                actorPokemon.getWithSelectorTargetPokemons().addAll(Arrays.asList(debutFactions.get(targetFaction).getDebutPokemons()));
                break;
            //任意敌人
            case ANY_ADJACENT_ENEMY:
                actorPokemon.getWithSelectorTargetPokemons().add(debutFactions.get(targetFaction).getDebutPokemons()[targetPokemonInDebutIndex]);
                break;
            default:
                actorPokemon.getWithSelectorTargetPokemons().add(debutFactions.get(targetFaction).getDebutPokemons()[userPokemonInDebutIndex]);
                break;
        }
    }

    public boolean computeMoveIsHit(short moveBaseAccuracy,BattlePokemonData actionPokemon,BattlePokemonData targetPokemon){
        byte actionPokemonFactionIndex = actionPokemon.getDebutFactionIndex();
        byte actionPokemonAccuracyStatLevel = actionPokemon.getStaticStats()[PokemonStatType.ACCURACY.getType()];
        byte targetPokemonEvasionStatLevel = targetPokemon.getStaticStats()[PokemonStatType.EVASION.getType()];
        float actionPokemonAccuracyRate = 1;
        if(actionPokemonAccuracyStatLevel > 0){
            actionPokemonAccuracyRate = (float) (2 + actionPokemonAccuracyStatLevel) / 2;
        } else if (actionPokemonAccuracyStatLevel < 0) {
            actionPokemonAccuracyRate = (float) 2 / (2 + Math.abs(actionPokemonAccuracyStatLevel));
        }
        float targetPokemonEvasionRate = 1;
        if(targetPokemonEvasionStatLevel > 0){
            targetPokemonEvasionRate = (float) (2 + targetPokemonEvasionStatLevel) / 2;
        } else if (targetPokemonEvasionStatLevel < 0) {
            targetPokemonEvasionRate = (float) 2 / (2 + Math.abs(targetPokemonEvasionStatLevel));
        }
        float actualAccuracyRate = (actionPokemonAccuracyRate * targetPokemonEvasionRate*moveBaseAccuracy) / 100;
        //广角镜1.3倍命中率
        if(actionPokemon.getPokemonBattleItem() == 5265 || actionPokemon.getPokemonBattleItem() == 6265){
            actualAccuracyRate *=1.3;
        }
        //TODO实现对焦镜效果
        //复眼1.3倍命中
        if(actionPokemon.getPokemonAbilityIndexId() == 14){
            actualAccuracyRate *=1.3;
        }
        //行动方宝可梦存在胜利之星1.1倍命中率
        for(BattlePokemonData battlePokemonData : debutFactions.get(actionPokemonFactionIndex).getDebutAlivePokemons()){
            if(battlePokemonData.getPokemonAbilityIndexId() == 162){
                actualAccuracyRate *=1.1;
            }
        }
        //重力1.67倍命中率
        if(battleBasisInfo.getBattlePublicFieldInfo().isHasGravityField()&& battleBasisInfo.getBattlePublicFieldInfo().getGravityFieldRemainRound()>0){
            actualAccuracyRate *=1.67;
        }
        //目标宝可梦携带光粉或悠闲薰香行动宝可梦0.9倍命中率
        if(targetPokemon.getPokemonBattleItem() == 5232||targetPokemon.getPokemonBattleItem() == 6231
                || targetPokemon.getPokemonBattleItem() == 5255 || targetPokemon.getPokemonBattleItem() == 6255){
            //当行动宝可梦为AI时，0.95倍命中率
            //当行动宝可梦为玩家时，0.9倍命中率
            if(actionPokemon.getOwnerSession() == null){
                actualAccuracyRate *=0.95;
            }
            else{
                actualAccuracyRate *=0.9;
            }
        }
        //对天气进行判断
        if(battleBasisInfo.getBattlePublicFieldInfo().isHasWeather() && battleBasisInfo.getBattlePublicFieldInfo().getWeatherRemainRound()>0){
            switch (battleBasisInfo.getBattlePublicFieldInfo().getBattleWeatherType()) {
                case SANDSTORM://沙隐
                    if(targetPokemon.getPokemonAbilityIndexId() == 8){
                        if(actionPokemon.getOwnerSession() == null){
                            actualAccuracyRate *=0.9;
                        }
                        else {
                            actualAccuracyRate *=0.8;
                        }
                    }
                    break;
                case SNOW://雪隐
                    if(actionPokemon.getPokemonAbilityIndexId() == 81){
                        if(actionPokemon.getOwnerSession() == null){
                            actualAccuracyRate *=0.9;
                        }
                        else {
                            actualAccuracyRate *=0.8;
                        }
                    }
                    break;
                case FOG:
                    actualAccuracyRate *=0.6;
                    break;
            }
        }
        float randomRate = (float) Math.random();
        if(randomRate <= actualAccuracyRate){
            return true;
        }
        return false;
    }
}
