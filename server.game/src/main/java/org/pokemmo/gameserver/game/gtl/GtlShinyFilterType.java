package org.pokemmo.gameserver.game.gtl;

/**
 * Shiny filter values used by GTL rule type 8.
 */
public enum GtlShinyFilterType {
    ANY_SHINY(0),
    SECRET_SHINY(1),
    NORMAL_SHINY(2),
    NOT_SHINY(3);

    private final byte type;

    GtlShinyFilterType(int type) {
        this.type = (byte) type;
    }

    public byte getType() {
        return type;
    }

    public static GtlShinyFilterType getByType(int type) {
        for (GtlShinyFilterType filterType : values()) {
            if (Byte.toUnsignedInt(filterType.type) == type) {
                return filterType;
            }
        }
        return null;
    }
}
