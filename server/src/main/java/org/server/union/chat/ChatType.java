package org.server.union.chat;

public enum ChatType {
    NORMAL(0),
    UNK1(1),
    UNK2(2),
    SHOUT(3),
    WHISPER(4),
    TRADE(5),
    GLOBAL(6),
    CHANNEL(7),
    GUILD(8),
    LINK(9),
    UNK3(10),
    UNK4(11),
    UNK5(12),
    UNK6(13),
    UNK7(14),
    UNK8(15),
    SYSTEM_ANNOUNCEMENTS(16),
    GAME_NOTIFICATIONS(17),
    BATTLE(18);
    private byte type;
    private static ChatType[] allTypeArray = values();
    public static ChatType getByType(int type) {
        return allTypeArray[type];
    }
    ChatType(int type) {
        this.type = (byte) type;
    }
    public byte getType() {
        return type;
    }
}
