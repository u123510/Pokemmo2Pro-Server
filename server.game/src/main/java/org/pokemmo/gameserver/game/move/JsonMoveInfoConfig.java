package org.pokemmo.gameserver.game.move;

import lombok.Getter;
import lombok.Setter;
import org.pokemmo.gameserver.game.pokemon.PokemonType;

/**
 * 自定义技能 JSON 配置：每个文件对应一个技能（字段与 move.json 导出格式一致，
 * 属性/伤害类型/目标类型用枚举名字符串）。与主文件 Move.bin 互不冲突，
 * 相同 moveIndexId 的自定义技能会覆盖主文件中的版本。
 */
@Getter @Setter
public class JsonMoveInfoConfig {
    //技能索引id
    private short moveIndexId;
    //技能的属性（枚举名，如 FIRE / FAIRY）
    private String movePokemonType;
    //技能的伤害类型（PHYSICAL / SPECIAL / STATUS）
    private String moveDamageType;
    //技能的基础威力
    private short moveBasePower;
    //技能是否真实伤害
    private boolean isTrueDamage;
    //技能的目标类型（枚举名，如 ANY_POKEMON_EXCEPT_SELF）
    private String moveTargetType;
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
    //技能改变宝可梦能力的类型数组
    private byte moveChangeStatTypeArray[] = new byte[3];
    //技能改变宝可梦能力的等级变化数组
    private byte moveChangeStatLevelArray[] = new byte[3];
    //技能改变宝可梦能力的等级变化概率数组
    private byte moveChangeStatRatioArray[] = new byte[3];
    //技能信息镖旗
    private int moveInfoFlag;
    //技能boss信息镖旗
    private byte moveBossInfoFlag;
    //技能回复hp的比例
    private byte moveHpRecoverRatio;
    //技能通过伤害回复hp的比例
    private byte moveHpRecoverByDamageRatio;

    public PokemonMoveData toPokemonMoveData() {
        return new PokemonMoveData.Builder()
                .setMoveIndexId(moveIndexId)
                .setMovePokemonType(PokemonType.valueOf(movePokemonType))
                .setMoveDamageType(MoveDamageType.valueOf(moveDamageType))
                .setMoveBasePower(moveBasePower)
                .setIsTrueDamage(isTrueDamage)
                .setMoveTargetType(MoveTargetType.valueOf(moveTargetType))
                .setMoveBasePp(moveBasePp)
                .setMoveBaseAccuracy(moveBaseAccuracy)
                .setMovePriority(movePriority)
                .setMoveAdditionChangeStatEffectTriggerRatio(moveAdditionChangeStatEffectTriggerRatio)
                .setMoveAdditionEffectChangeStatLevel(moveAdditionEffectChangeStatLevel)
                .setIsUserTriggerAdditionChangeStatEffect(isUserTriggerAdditionChangeStatEffect)
                .setMoveChangeStatTypeArray(moveChangeStatTypeArray)
                .setMoveChangeStatLevelArray(moveChangeStatLevelArray)
                .setMoveChangeStatRatioArray(moveChangeStatRatioArray)
                .setMoveInfoFlag(moveInfoFlag)
                .setMoveBossInfoFlag(moveBossInfoFlag)
                .setMoveHpRecoverRatio(moveHpRecoverRatio)
                .setMoveHpRecoverByDamageRatio(moveHpRecoverByDamageRatio)
                .bulid();
    }
}
