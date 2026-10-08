package org.pokemmo.gameserver.game.gtl;

/**
 * Gender filter values used by GTL rule type 1.
 */
public enum GtlGenderFilterType {
    MALE(0),
    FEMALE(1),
    GENDERLESS(0xFF);

    private final int type;

    GtlGenderFilterType(int type) {
        this.type = type;
    }

    public int getType() {
        return type;
    }

    public static GtlGenderFilterType getByType(int type) {
        for (GtlGenderFilterType filterType : values()) {
            if (filterType.type == type) {
                return filterType;
            }
        }
        return null;
    }
}
