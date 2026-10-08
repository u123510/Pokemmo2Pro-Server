package org.pokemmo.gameserver.game.string;

import lombok.Getter;

@Getter
public class GameLocalFormatString {
    private byte replaceIndex;
    private byte stringType;
    private byte regionIndex;
    private short[] stringIndexArray;
    private int intStringId;
    private long longStringId;
    private String notificationString;
    public GameLocalFormatString(int replaceIndex, int stringType, int regionIndex, short[] stringIndexArray, int intStringId, long longStringId, String notificationString) {
        this.replaceIndex = (byte) replaceIndex;
        this.stringType = (byte) stringType;
        this.regionIndex = (byte) regionIndex;
        this.stringIndexArray = stringIndexArray;
        this.intStringId = intStringId;
        this.longStringId = longStringId;
        this.notificationString = notificationString;
    }
    // 通知类型格式化
    public GameLocalFormatString(int replaceIndex,String notificationString) {
        this.replaceIndex = (byte) replaceIndex;
        this.stringType = 5;
        this.notificationString = notificationString;
    }
}
