package org.pokemmo.gameserver.game.battle;
import org.pokemmo.gameserver.game.container.PokemonContainerType;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class BattleBasisInfo {
    //战斗基础信息
    private BattleType battleType;//战斗的类型 野怪，玩家，各种npc
    private BattleFormatType battleFormatType;//战斗战形式 单打双打
    private BattleFormType battleFormType;//战斗的种类 普通，沙湖，测试
    private BattleFacilityType battleFacilityType;//战斗的场地
    private int battleAlreadyRunTime;//战斗已经运行的时间 单位：秒
    private short battleRoundAmount = 0;
    //战斗状态信息
    private boolean canRun;
    //默认不以敌人视角为我方视角
    private boolean isUseEmemyAngle = false;
    private boolean hasInterruption = false;
    //是否可以使用物品
    private boolean canUseItem;
    private byte useItemLimitAmount;
    //是否限制时间
    private boolean hasLimitMinute;
    private byte limitMinutes;
    //是否是PVP
    private boolean isPvp;
    private byte pvpLevel;
    //是否有限制的条款
    private boolean hasLimitSmogon;
    private SmogonType[] limitSmogons;
    //是否限制双方回合时间
    private boolean hasTimeEvent;
    private TimedEvent[] selfSideTimeEvent;
    private TimedEvent[] enemySideTimeEvent;
    //是否重新加载战斗音乐
    private boolean isReloadBattleMusic;
    private byte battleMusicRegionIndexId;
    private short battleMusicIndexId;
    //是否是pvp排位
    private boolean isPvpRank;
    private PvpRankLevelType pvpRankLevelType;
    //是否可以投降
    private boolean canSurrender;
    private byte surrenderLimitArround;
    //是否重新设置战斗中的宝可梦容器类型
    private boolean isReloadPokemonContainerType;
    private PokemonContainerType battlePokemonContainerType;
    //是否重新设置战斗状态广播模式
    private boolean isReloadBattleStatsBroadcastMode;
    private BattleStatsBroadcastMode battleStatsBroadcastMode;
    private boolean[] isShowPokemonAbilityValue = new boolean[8];
    //是否重新设置随机种子
    private boolean isReloadRandomSeed;
    private int randomSeed;
    //是否忽略战斗动画
    private boolean isIgnoreBattleAnimation = false;
    //是否加入废弃使用
    private boolean hasUnuse;
    private byte unuse = 0;
    //是否boss对战限制等级
    private boolean hasBossBatLimitLevel;
    private byte bossBatLimitLevel;
    //战斗双方公共的场地心底
    private BattlePublicFieldInfo battlePublicFieldInfo = new BattlePublicFieldInfo();
    public BattleBasisInfo setPvpLevel(PvpLevelType pvpLevelType){
        this.isPvp = true;
        this.pvpLevel = pvpLevelType.getType();
        return this;
    }
    public BattleBasisInfo setLimitSmogons(SmogonType[] limitSmogons){
        this.hasLimitSmogon = true;
        this.limitSmogons = limitSmogons;
        return this;
    }
    public BattleBasisInfo setTimeEvent(TimedEvent[] selfSideTimeEvent, TimedEvent[] enemySideTimeEvent){
        this.hasTimeEvent = true;
        this.selfSideTimeEvent = selfSideTimeEvent;
        this.enemySideTimeEvent = enemySideTimeEvent;
        return this;
    }
    public BattleBasisInfo setBattleMusic(byte battleMusicRegionIndexId, short battleMusicIndexId) {
        this.isReloadBattleMusic = true;
        this.battleMusicRegionIndexId = battleMusicRegionIndexId;
        this.battleMusicIndexId = battleMusicIndexId;
        return this;
    }
    public BattleBasisInfo setPvpRank(PvpRankLevelType pvpRankLevelType) {
        this.isPvpRank = true;
        this.pvpRankLevelType = pvpRankLevelType;
        return this;
    }
    public BattleBasisInfo setCanSurrender(int surrenderLimitArround) {
        this.canSurrender = true;
        this.surrenderLimitArround = (byte) surrenderLimitArround;
        return this;
    }
    public BattleBasisInfo setPokemonContainerType(PokemonContainerType battlePokemonContainerType) {
        this.isReloadPokemonContainerType =true;
        this.battlePokemonContainerType = battlePokemonContainerType;
        return this;
    }
    public BattleBasisInfo setBattleStatsBroadcastMode(BattleStatsBroadcastMode battleStatsBroadcastMode , boolean[] isShowPokemonAbilityValue) {
        this.isReloadBattleStatsBroadcastMode = true;
        this.battleStatsBroadcastMode = battleStatsBroadcastMode;
        this.isShowPokemonAbilityValue = isShowPokemonAbilityValue;
        return this;
    }
    public BattleBasisInfo setRandomSeed(int randomSeed) {
        this.isReloadRandomSeed = true;
        this.randomSeed = randomSeed;
        return this;
    }
    public BattleBasisInfo setBossBatLimitLevel(int bossBatLimitLevel) {
        this.hasBossBatLimitLevel = true;
        this.bossBatLimitLevel = (byte) bossBatLimitLevel;
        return this;
    }
}
