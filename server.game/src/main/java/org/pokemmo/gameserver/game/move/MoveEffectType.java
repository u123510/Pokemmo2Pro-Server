package org.pokemmo.gameserver.game.move;

public enum MoveEffectType {

    LIMIT_BY_TAUNT(5),//被挑衅限制
    LIMIT_BY_GRAVITY(8),//被重力限制
    HAS_TARGET(11),//技能有指定的目标
    LIMIT_BY_HEAL_BLOCK(12);//被回复封锁限制

    private byte type;

    private MoveEffectType(int type) {
        this.type = (byte) type;
    }
}
