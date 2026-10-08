package org.pokemmo.gameserver.game.trade;

/** Status values consumed by the client trade window (S2C 0x51). */
public enum TradeStatusType {
    STARTED(0),
    COMPLETE(1),
    CANCEL(2),
    FINAL_CONFIRM(3),
    LOCKED(4);

    private final byte type;

    TradeStatusType(int type) {
        this.type = (byte) type;
    }

    public byte getType() {
        return type;
    }
}
