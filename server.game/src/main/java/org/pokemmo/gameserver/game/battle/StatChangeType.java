package org.pokemmo.gameserver.game.battle;

public enum StatChangeType {
    STAT_NORMAL_CHANGE(0),//正常改变
    STAT_INCREASE_FINISH(1),//增幅结束
    STAT_DECLINE_FINISH(2);//摆脱下降
    private byte type;
    StatChangeType(int type) {
        this.type = (byte) type;
    }
}
