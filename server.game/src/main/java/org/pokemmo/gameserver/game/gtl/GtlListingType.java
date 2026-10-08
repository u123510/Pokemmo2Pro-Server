package org.pokemmo.gameserver.game.gtl;

public enum GtlListingType {
    POKEMON(0),
    ITEM(1);

    private final byte type;

    GtlListingType(int type) {
        this.type = (byte) type;
    }

    public byte getType() {
        return type;
    }

    public static GtlListingType getByType(int type) {
        for (GtlListingType listingType : values()) {
            if ((listingType.type & 0xFF) == type) {
                return listingType;
            }
        }
        return null;
    }
}
