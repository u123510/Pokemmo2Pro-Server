package org.pokemmo.gameserver.game.battle;

public enum BattlePublicFieldType {
    DEF_EXCHANGE_SPEC_DEF_ROOM(0),
    FALL_INTO_ROOM(1),
    ITEM_DISAPPEAR_ROOM(2),
    GRAVITY_FIELD(3),
    //属性反转并且不再免疫
    DEBUT_POKEMON_TYPE_REVERSE_AND_IMMUNITY_INVALID(4),
    //伤害类型反转
    DAMAGE_TYPE_REVERSE(5),
    //岩浆池
    LAVA_POOL(6);
    private byte type;
    private static BattlePublicFieldType[] allTypeArray = {
            DEF_EXCHANGE_SPEC_DEF_ROOM,
            FALL_INTO_ROOM,
            ITEM_DISAPPEAR_ROOM,
            GRAVITY_FIELD,
            DEBUT_POKEMON_TYPE_REVERSE_AND_IMMUNITY_INVALID,
            DAMAGE_TYPE_REVERSE,
            LAVA_POOL
    };
    BattlePublicFieldType(int type) {
        this.type = (byte) type;
    }
    public static BattlePublicFieldType getByType(int type) {
        return allTypeArray[type];
    }
    public byte getType() {
        return type;
    }
}
