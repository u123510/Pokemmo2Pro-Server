package org.pokemmo.gameserver.game.instance;

public class GameInstance {
    private byte regionId;//地区ID
    private int  nextInstanceTime;//下次副本刷新时间
    private short finishTimes;//完成次数
    public GameInstance(byte regionId, int nextInstanceTime, short finishTimes) {
        this.regionId = regionId;
        this.nextInstanceTime = nextInstanceTime;
        this.finishTimes = finishTimes;
    }
    public byte getInstanceType() {
        return regionId;
    }
    public int getNextInstanceTime() {
        return nextInstanceTime;
    }
    public short getFinishTimes() {
        return finishTimes;
    }
}
