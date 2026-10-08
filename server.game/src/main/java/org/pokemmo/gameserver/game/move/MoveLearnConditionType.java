package org.pokemmo.gameserver.game.move;

public enum MoveLearnConditionType {
    //等级
    LEVEL(0),
    //遗传
    EGG_MOVE(1),
    //教学
    MOVE_TUTOR(2),
    //特殊
    SPECIAL_MOVE(3),
    //学习机
    MOVE_LEARNER_TOOL(4),
    //进化
    EVOLUTION(5),
    //进化前
    PREVO(6);

    private byte type;
    MoveLearnConditionType(int type){
        this.type = (byte)type;
    }

}
