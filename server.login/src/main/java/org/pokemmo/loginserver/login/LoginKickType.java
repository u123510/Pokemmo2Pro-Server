package org.pokemmo.loginserver.login;

public enum LoginKickType {
    PLAYER(0),
    ACCOUNT(1),
    IP_ADDRESS(2),
    UNKNOWN(3);
    private byte type;
    LoginKickType(int type) {
        this.type = (byte) type;
    }

    public byte getType() {
        return type;
    }
}
