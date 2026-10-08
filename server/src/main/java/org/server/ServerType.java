package org.server;

public enum ServerType {
    NULL(-1),
    LOGIN(0),
    GAME(1),
    CHAT(2);
    private byte type;
    private static final ServerType[] allTypeArray = {NULL, LOGIN, GAME, CHAT};
    ServerType(int type) {
        this.type = (byte) type;
    }
    public byte getType() {
        return type;
    }
    public static ServerType getType(int type) {
        return allTypeArray[type+1];
    }
}
