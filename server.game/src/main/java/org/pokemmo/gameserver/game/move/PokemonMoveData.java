package org.pokemmo.gameserver.game.move;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.pokemmo.gameserver.game.pokemon.PokemonStatType;
import org.pokemmo.gameserver.game.pokemon.PokemonType;

import java.util.ArrayList;
import java.util.List;
@Getter @Setter @AllArgsConstructor
public class PokemonMoveData {
    //技能索引id
    private short moveIndexId;
    //技能的属性
    private PokemonType movePokemonType;
    //技能的伤害类型
    private MoveDamageType moveDamageType;
    //技能的基础威力
    private short moveBasePower;
    //技能是否真实伤害
    private boolean isTrueDamage;
    //技能的目标类型
    private MoveTargetType moveTargetType;
    //技能的基础pp
    private byte moveBasePp;
    //技能的基础准确率
    private byte moveBaseAccuracy;
    //技能的先制等级
    private byte movePriority;
    //技能附加改变宝可梦能力等级效果触发概率
    private byte moveAdditionChangeStatEffectTriggerRatio;
    //技能附加改变宝可梦能力等级效果等级变化
    private byte moveAdditionEffectChangeStatLevel;
    //技能附加改变宝可梦能力等级效果是否使用者触发
    private boolean isUserTriggerAdditionChangeStatEffect;
    //技能附加效果改变宝可梦能力的类型
    private List<PokemonStatType> moveAdditionEffectChangePokemonStatArray = new ArrayList<>();
    //未知效果，所有技能该数组大小为0
    private List<PokemonStatType> moveAttachChangePokemonStatArray = new ArrayList<>();
    //技能改变宝可梦能力的类型数组
    private byte moveChangeStatTypeArray[];
    //技能改变宝可梦能力的等级变化数组
    private byte moveChangeStatLevelArray[];
    //技能改变宝可梦能力的等级变化概率数组
    private byte moveChangeStatRatioArray[];
    //技能信息镖旗
    private int moveInfoFlag;
    //技能boss信息镖旗
    private byte moveBossInfoFlag;
    //技能回复hp的比例
    private byte moveHpRecoverRatio;
    //技能通过伤害回复hp的比例
    private byte moveHpRecoverByDamageRatio;
    //技能附加效果字符串索引id与填充数据
    private List<MoveAdditionStringData> moveAdditionStringDatas = new ArrayList<>();
    public static class Builder{
        private short moveIndexId;
        private PokemonType movePokemonType;
        private MoveDamageType moveDamageType;
        private short moveBasePower;
        private boolean isTrueDamage;
        private MoveTargetType moveTargetType;
        private byte moveBasePp;
        private byte moveBaseAccuracy;
        private byte movePriority;
        private byte moveAdditionChangeStatEffectTriggerRatio;
        private byte moveAdditionEffectChangeStatLevel;
        private boolean isUserTriggerAdditionChangeStatEffect;
        private List<PokemonStatType> moveAdditionEffectChangePokemonStatArray = new ArrayList<>();
        private List<PokemonStatType> moveAttachChangePokemonStatArray = new ArrayList<>();
        private byte moveChangeStatTypeArray[];
        private byte moveChangeStatLevelArray[];
        private byte moveChangeStatRatioArray[];
        private int moveInfoFlag;
        private byte moveBossInfoFlag;
        private byte moveHpRecoverRatio;
        private byte moveHpRecoverByDamageRatio;
        private List<MoveAdditionStringData> moveAdditionStringDatas = new ArrayList<>();
        public Builder setMoveIndexId(short moveIndexId) {
            this.moveIndexId = moveIndexId;
            return this;
        }
        public Builder setMovePokemonType(PokemonType movePokemonType) {
            this.movePokemonType = movePokemonType;
            return this;
        }
        public Builder setMoveDamageType(MoveDamageType moveDamageType) {
            this.moveDamageType = moveDamageType;
            return this;
        }
        public Builder setMoveBasePower(short moveBasePower) {
            this.moveBasePower = moveBasePower;
            return this;
        }
        public Builder setIsTrueDamage(boolean isTrueDamage) {
            this.isTrueDamage = isTrueDamage;
            return this;
        }
        public Builder setMoveTargetType(MoveTargetType moveTargetType) {
            this.moveTargetType = moveTargetType;
            return this;
        }
        public Builder setMoveBasePp(byte moveBasePp) {
            this.moveBasePp = moveBasePp;
            return this;
        }
        public Builder setMoveBaseAccuracy(byte moveBaseAccuracy) {
            this.moveBaseAccuracy = moveBaseAccuracy;
            return this;
        }
        public Builder setMovePriority(byte movePriority) {
            this.movePriority = movePriority;
            return this;
        }
        public Builder setMoveAdditionChangeStatEffectTriggerRatio(byte moveAdditionChangeStatEffectTriggerRatio) {
            this.moveAdditionChangeStatEffectTriggerRatio = moveAdditionChangeStatEffectTriggerRatio;
            return this;
        }
        public Builder setMoveAdditionEffectChangeStatLevel(byte moveAdditionEffectChangeStatLevel) {
            this.moveAdditionEffectChangeStatLevel = moveAdditionEffectChangeStatLevel;
            return this;
        }
        public Builder setIsUserTriggerAdditionChangeStatEffect(boolean isUserTriggerAdditionChangeStatEffect) {
            this.isUserTriggerAdditionChangeStatEffect = isUserTriggerAdditionChangeStatEffect;
            return this;
        }
        public Builder addMoveAdditionEffectChangePokemonStat(PokemonStatType moveAdditionEffectChangePokemonStat) {
            this.moveAdditionEffectChangePokemonStatArray.add(moveAdditionEffectChangePokemonStat);
            return this;
        }
        public Builder addMoveAttachChangePokemonStat(PokemonStatType moveAttachChangePokemonStat) {
            this.moveAttachChangePokemonStatArray.add(moveAttachChangePokemonStat);
            return this;
        }
        public Builder setMoveChangeStatTypeArray(byte[] moveChangeStatTypeArray) {
            this.moveChangeStatTypeArray = moveChangeStatTypeArray;
            return this;
        }
        public Builder setMoveChangeStatLevelArray(byte[] moveChangeStatLevelArray) {
            this.moveChangeStatLevelArray = moveChangeStatLevelArray;
            return this;
        }
        public Builder setMoveChangeStatRatioArray(byte[] moveChangeStatRatioArray) {
            this.moveChangeStatRatioArray = moveChangeStatRatioArray;
            return this;
        }
        public Builder setMoveInfoFlag(int moveInfoFlag) {
            this.moveInfoFlag = moveInfoFlag;
            return this;
        }
        public Builder setMoveBossInfoFlag(byte moveBossInfoFlag) {
            this.moveBossInfoFlag = moveBossInfoFlag;
            return this;
        }
        public Builder setMoveHpRecoverRatio(byte moveHpRecoverRatio) {
            this.moveHpRecoverRatio = moveHpRecoverRatio;
            return this;
        }
        public Builder setMoveHpRecoverByDamageRatio(byte moveHpRecoverByDamageRatio) {
            this.moveHpRecoverByDamageRatio = moveHpRecoverByDamageRatio;
            return this;
        }
        public Builder addMoveAdditionStringData(MoveAdditionStringData moveAdditionStringData) {
            this.moveAdditionStringDatas.add(moveAdditionStringData);
            return this;
        }
        public PokemonMoveData bulid(){
            return new PokemonMoveData(moveIndexId, movePokemonType, moveDamageType, moveBasePower, isTrueDamage, moveTargetType, moveBasePp, moveBaseAccuracy, movePriority, moveAdditionChangeStatEffectTriggerRatio, moveAdditionEffectChangeStatLevel, isUserTriggerAdditionChangeStatEffect, moveAdditionEffectChangePokemonStatArray, moveAttachChangePokemonStatArray, moveChangeStatTypeArray, moveChangeStatLevelArray, moveChangeStatRatioArray, moveInfoFlag, moveBossInfoFlag, moveHpRecoverRatio, moveHpRecoverByDamageRatio, moveAdditionStringDatas);
        }
    }
}
