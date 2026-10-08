package org.pokemmo.gameserver.game.gtl;

public enum GtlActionResult {
    SUCCESS(0),
    REJECTED(1);

    private final byte code;

    GtlActionResult(int code) {
        this.code = (byte) code;
    }

    public byte getCode() {
        return code;
    }
}
