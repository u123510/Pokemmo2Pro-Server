package org.pokemmo.gameserver.game.battle;

public enum BattleRoundSettlementType {
    /*
   回合开始结算
           */
    ABILITY_TRIGGER(0),//特性触发
    ITEM_EFFECT_TRIGGER(1),//道具效果触发
    FIELD_EFFECT(2),//场地效果
    POKEMON_DISOBEDIENT(3),//宝可梦不听话
    /*
      回合开始中
            */
    EXCHANGE_POKEMON(4),//更换宝可梦
    USE_ITEM(5),//使用道具
    USE_MOVE(6),//使用技能
    /*
      宝可梦行动后
                */
    ACTION_CONDITION_JUDGE(7),//行动条件判断，比如是否触发麻痹，着迷，冰冻，睡眠
    WEATHER_DAMAGE_EFFECT(8),//结算天气伤害
    POKEMON_STATUS_DAMAGE_EFFECT(9),//异常状态伤害结算
    ABILITY_DAMAGE_EFFECT(10),//宝可梦特性造成的伤害
    ITEM_DAMAGE_EFFECT(11);//道具伤害效果 如附着针，头盔
    private byte type;
    private static BattleRoundSettlementType[] allTypeArray = values();
    BattleRoundSettlementType(int type){
        this.type = (byte) type;
    }
    public byte getType(){
        return this.type;
    }
}
