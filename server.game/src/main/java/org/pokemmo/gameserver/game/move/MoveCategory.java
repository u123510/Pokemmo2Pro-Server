package org.pokemmo.gameserver.game.move;

public enum MoveCategory {
    //蛋招式
    EGG_MOVES(0),
    //教学
    MOVE_TUTOR(1),
    //特殊招式
    SPECIAL_MOVES(2),
    //进化前招式
    PREVO_MOVES(3),
    //学习机技能
    MOVE_LEARNER_TOOLS(4),
    //蛋 &amp; 道具
    SPECIAL_EGG(5),
    //进化招式
    ON_EVOLUTION(6);
    public static final MoveCategory[] allTypeArray = new MoveCategory[]{EGG_MOVES, MOVE_TUTOR, SPECIAL_MOVES, PREVO_MOVES, MOVE_LEARNER_TOOLS, SPECIAL_EGG, ON_EVOLUTION};
    private byte type;
    MoveCategory(int type) {
        this.type = (byte) type;
    }
    public MoveCategory getMoveCategory(byte index){
        return allTypeArray[index];
    }
}
