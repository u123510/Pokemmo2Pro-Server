package org.pokemmo.gameserver.game.move;

public enum MoveTargetType {
    ANY_POKEMON_EXCEPT_SELF(0),
    //点穴
    SELF_OR_ADJACENT_TEAM_MEMBER(1),
    //抢先一步
    ANY_ADJACENT_ENEMY(3),
    ALL_AROUND_POKEMON(4),
    ALL_AROUND_ENEMY(5),
    SELF_AND_ALL_TEAM_MEMBER(6),
    SELF(7),
    //灭亡之歌
    ALL_DEBUT_POKEMON(8),
    RANDOM_ADJACENT_ENEMY(9),
    GLOBAL_FIELD_OR_WEATHER(10),
    ENEMY_FIELD(11),
    SELF_FIELD(12),
    //镜面反射，金属爆炸，双倍奉还，诅咒
    SPEC(13),
    //自由落体
    ONE_ENEMY_WHETHER_OR_NOT_ADJACENT(14),
    //帮助
    ANY_TEAM_MEMBER_EXCEPT_SELF(17);
    private byte type;
    MoveTargetType(int type) {
        this.type = (byte)type;
    }
    public byte getType(){
        return type;
    }
    public static MoveTargetType getByType(int type) {
        for (MoveTargetType value : MoveTargetType.values()) {
            if (value.type == type) {
                return value;
            }
        }
        return null; // 或者抛出异常
    }
}
