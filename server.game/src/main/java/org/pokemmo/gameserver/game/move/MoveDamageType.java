package org.pokemmo.gameserver.game.move;

public enum MoveDamageType {
    PHYSICAL(0),
    SPECIAL(1),
    STATUS(2);
    private byte type;
    MoveDamageType(int type) {
        this.type = (byte)type;
    }
    public static MoveDamageType getByType(int type) {
        for (MoveDamageType value : MoveDamageType.values()) {
            if (value.type == type) {
                return value;
            }
        }
        return null;
    }
}
