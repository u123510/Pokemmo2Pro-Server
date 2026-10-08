package org.pokemmo.gameserver.game.battle;

import lombok.Getter;
import lombok.Setter;
import org.pokemmo.gameserver.game.battle.effect.BasePokemonActionEffect;
import org.pokemmo.gameserver.game.move.MoveManager;
import org.pokemmo.gameserver.game.move.MoveDamageType;
import org.pokemmo.gameserver.game.move.PokemonMoveData;
import org.pokemmo.gameserver.game.pokemon.*;
import org.server.Session;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

@Getter @Setter @Slf4j
public class BattlePokemonData {
    private static final short UNRESOLVED_BATTLE_PARTICLE_EFFECT = Short.MIN_VALUE;
    //是否存在宝可梦
    private boolean hasDebutPokemon;
    // 宝可梦在对战中的阵营
    private byte debutFactionIndex = -1;
    // 宝可梦所属的队伍索引
    private byte pokemonTeamIndex = -1;
    // 宝可梦在对战中的位置
    private byte debutIndex = -1;
    // 宝可梦数据
    private PokemonData pokemonData;
    // 宝可梦战斗中的道具
    private short pokemonBattleItem = -1;
    //强化等级
    private byte[] staticStats = new byte[8];
    //要害命中强化等级
    private byte criticalHitLevel = 0;
    // 是否已经用过技能了
    private boolean isAlreadyUseSkill;
    // 是否被挑衅
    private boolean isTaunt;
    // 是否锁住道具无法使用
    private boolean isItemBlock;
    // 是否陷入无理取闹
    private boolean isTorment;
    // 是否陷入回复封锁
    private boolean isHealBlock;
    //是否有不能用的技能
    private boolean hasDisableUseSkill;
    //不能用的技能id
    private short disableSkillIndexId = -1;
    //是否处于再来一次状态
    private boolean isInEncore;
    //再来一次锁住技能id
    private short encoreLockedSkillIndexId = -1;
    //是否被道具锁招
    private boolean isItemLockSkill;
    // 被道具锁招的技能id
    private short itemLockedSkillIndexId = -1;
    //锁住技能的道具id
    private short itemLockedIndexId = -1;
    // 是否在地面上
    private boolean isOnGround;
    // 是否扎根
    private boolean isIngrain;
    // 是否被击落
    private boolean isFallen;
    // 是否重新加载技能
    private boolean isReloadSkill;
    // 是否重新加载宝可梦数据
    private boolean isReloadPokemonData;
    // 是否重新加载宝可梦属性
    private boolean isReloadPokemonType;
    // 是否重新加载宝可梦特性
    private boolean isReloadPokemonAbility;
    // 宝可梦特性值
    private short pokemonAbilityIndexId = -1;
    // 是否重新加载宝可梦索引ID
    private boolean isReloadPokemonIndexId;
    // 宝可梦是否处于强化状态
    private boolean isPokemonAggrandizement;
    // 是否是Boss宝可梦
    private boolean isBoss;
    // Boss宝可梦类型
    private BattleBossType bossType;
    // 是否重置宝可梦技能
    private boolean isResetPokemonSkill;
    // 是否不设置宝可梦渲染动画速度
    private boolean isNotSetPokemonSpriteRenderAnimationSpeed;
    // 是否被藤拘
    private boolean isVinesEntangled;
    // 是否被识破或 可以被普通系或者格斗系伤害
    private boolean canHurtByNormalAndFightType;
    // 是否被奇迹之眼 可以被超能系伤害
    private boolean canHurtByPsychicType;
    // 是否力量戏法状态(防御攻击交换)
    private boolean isInPowerTrick;
    // 是否处于寄生种子状态
    private boolean isInLeechSeed;
    // 寄生者的宝可梦登场选择器
    private byte leecherPokemonDebutSelector = -1;
    //最后一次使用的技能
    private short lastUseSkillIndexId = -1;
    // 宝可梦性别
    private byte pokemonSex;
    // 宝可梦第一属性
    private PokemonType pokemonFirstType;
    // 宝可梦第二属性
    private PokemonType pokemonSecondType;
    //宝可梦当前选择的技能
    private short roundChoiceMoveIndexId = -1;
    //宝可梦当前选择使用的道具
    private short roundChoiceItemIndexId = -1;
    // 经验池宝可梦数据
    private HashSet<BattlePokemonData> expPoolPokemons = new HashSet<>(0);
    // 存在选择目标的受影响宝可梦 宝可梦的技能选择影响的宝可梦
    private List<BattlePokemonData> withSelectorTargetPokemons = new ArrayList<>(0);
    // 自身遭受行为的镖旗
    private short sufferFlag = 0;
    // 自身遭受的有指向目标的行为
    private List<BasePokemonActionEffect> sufferActions = new ArrayList<>(0);
    // 自身造成的的无指向目标的行为
    private List<BattlePokemonCauseAction> causeWithNoTargetActions = new ArrayList<>(0);
    //击败该宝可梦的宝可梦
    private BattlePokemonData defeaterPokemon;
    //是否已经完成经验结算
    private boolean expSettled;
    //宝可梦执行的指令类型
    private BattlePokemonCommandType battlePokemonCommandType = BattlePokemonCommandType.NULL;
    //宝可梦行动选择的目标
    private BattlePokemonData targetPokemon;
    //宝可梦所属玩家的会话
    private Session ownerSession;
    //是否首回合登场
    private boolean isFirstRoundDebut = true;
    //随机质子在同一场战斗中只解析一次，确保队伍/出场/换位封包一致
    private short battleParticleEffectType = UNRESOLVED_BATTLE_PARTICLE_EFFECT;
    //本回合最近一次承受的伤害，用于反击、镜面反射和金属爆炸等技能
    private int lastDamageTaken;
    private MoveDamageType lastDamageType;

    public short getBattleParticleEffectType() {
        if (battleParticleEffectType == UNRESOLVED_BATTLE_PARTICLE_EFFECT) {
            battleParticleEffectType = pokemonData == null
                    ? -1 : pokemonData.getCurrentSelectParticleEffectTypeForBattle();
            log.debug("战斗质子解析: pokemonId={}, configured={}, selected={}",
                    pokemonData == null ? -1 : pokemonData.getPokemonId(),
                    pokemonData == null ? -1 : pokemonData.getCurrentSelectParticleEffectType(),
                    battleParticleEffectType);
        }
        return battleParticleEffectType;
    }
    public byte getSelectorData(){
        return (byte) (debutFactionIndex|(debutIndex<<4));
    }
    //宝可梦退场以后清除数据
    public void clearDataAfterExit(){
        this.battleParticleEffectType = UNRESOLVED_BATTLE_PARTICLE_EFFECT;
        this.lastDamageTaken = 0;
        this.lastDamageType = null;
        this.debutFactionIndex = -1;
        this.pokemonTeamIndex = -1;
        this.debutIndex = -1;
        for(int i = 0;i<this.staticStats.length;i++){
            this.staticStats[i] = 0;
        }
        this.criticalHitLevel = 0;
        this.isAlreadyUseSkill = false;
        this.isTaunt = false;
        this.isItemBlock = false;
        this.isTorment = false;
        this.isHealBlock = false;
        this.hasDisableUseSkill = false;
        this.disableSkillIndexId = -1;
        this.isInEncore = false;
        this.encoreLockedSkillIndexId = -1;
        this.isItemLockSkill = false;
        this.itemLockedSkillIndexId = -1;
        this.itemLockedIndexId = -1;
        this.isOnGround = false;
        this.isIngrain = false;
        this.isFallen = false;
        this.isReloadSkill = false;
        this.isReloadPokemonData = false;
        this.isReloadPokemonType = false;
        this.isReloadPokemonAbility = false;
        this.isReloadPokemonIndexId = false;
        this.isPokemonAggrandizement = false;
        this.isVinesEntangled = false;
        this.canHurtByNormalAndFightType = false;
        this.canHurtByPsychicType = false;
        this.isInPowerTrick = false;
        this.lastUseSkillIndexId = -1;
        this.pokemonFirstType = pokemonData.getPokemonFirstType();
        this.pokemonSecondType = pokemonData.getPokemonSecondType();
        this.roundChoiceMoveIndexId = -1;
        this.roundChoiceItemIndexId = -1;
        this.isInLeechSeed = false;
        this.leecherPokemonDebutSelector = -1;
        this.expPoolPokemons.clear();
        this.expSettled = false;
        this.withSelectorTargetPokemons.clear();
        this.sufferFlag = 0;
        this.sufferActions.clear();
        this.causeWithNoTargetActions.clear();
        this.battlePokemonCommandType = BattlePokemonCommandType.NULL;
        this.targetPokemon = null;
        this.isFirstRoundDebut = false;
    }
    //回合结束以后清除数据
    public void clearDataAfterRound(){
        this.withSelectorTargetPokemons.clear();
        this.sufferFlag = 0;
        this.sufferActions.clear();
        this.causeWithNoTargetActions.clear();
        this.defeaterPokemon = null;
        this.lastDamageTaken = 0;
        this.lastDamageType = null;
        this.battlePokemonCommandType = BattlePokemonCommandType.NULL;
        this.targetPokemon = null;
    }
    public void addPokemonsToExpPool(List<BattlePokemonData> playerPokemons){
        for(BattlePokemonData playerPokemon : playerPokemons){
            if(playerPokemon != null){
                if(playerPokemon.getPokemonData().getLevel()<100){
                    this.expPoolPokemons.add(playerPokemon);
                }
            }
        }
    }
    public void updateSufferFlag(short sufferFlag){
        this.sufferFlag = sufferFlag;
    }

    public int getLastDamageTaken() {
        return lastDamageTaken;
    }

    public MoveDamageType getLastDamageType() {
        return lastDamageType;
    }

    public void setLastDamageTaken(int lastDamageTaken, MoveDamageType lastDamageType) {
        this.lastDamageTaken = Math.max(0, lastDamageTaken);
        this.lastDamageType = this.lastDamageTaken == 0 ? null : lastDamageType;
    }
    public boolean checkMoveCanUse(short MoveIndexId){
        //检测是否存在道具锁定技能
        if(isItemLockSkill){
            if(MoveIndexId == lastUseSkillIndexId){
                return false;
            }
        }
        //检测挑衅
        if(isTaunt){
            PokemonMoveData moveData = MoveManager.getPokemonMove(MoveIndexId);
            if(moveData.getMoveBaseAccuracy()<1){
                return false;
            }
        }
        return true;
    }
    public BattlePokemonData(Session ownerSession, PokemonData pokemonData,PokemonType pokemonFirstType, PokemonType pokemonSecondType,int pokemonSex,int pokemonAbilityIndexId,int debutFactionIndex,int pokemonTeamIndex,int debutIndex,boolean hasDebutPokemon) {
        this.ownerSession = ownerSession;
        this.pokemonData = pokemonData;
        this.pokemonBattleItem = pokemonData.getItem();
        this.pokemonFirstType = pokemonFirstType;
        this.pokemonSecondType = pokemonSecondType;
        this.pokemonSex = (byte) pokemonSex;
        this.pokemonAbilityIndexId = (short) pokemonAbilityIndexId;
        this.debutFactionIndex = (byte) debutFactionIndex;
        this.debutIndex = (byte) debutIndex;
        this.pokemonTeamIndex = (byte) pokemonTeamIndex;
        this.hasDebutPokemon = hasDebutPokemon;
    }
}
