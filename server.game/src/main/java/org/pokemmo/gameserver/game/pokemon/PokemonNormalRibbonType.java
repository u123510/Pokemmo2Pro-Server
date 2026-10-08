package org.pokemmo.gameserver.game.pokemon;

public enum PokemonNormalRibbonType {
    CHAMPION_RIBBON(0,16),
    EFFORT_RIBBON(1,20),
    GIFT_RIBBON(2,21),
    WINNING_RIBBON(3,22),
    VICTORY_RIBBON(4,23),
    GORGEOUS_RIBBON(5,24),
    ROYAL_RIBBON(6,25),
    GORGEOUS_ROYAL_RIBBON(7,26),
    ALERT_RIBBON(8,27),
    SHOCK_RIBBON(9,28),
    DOWNCAST_RIBBON(10,29),
    CARELESS_RIBBON(11,30),
    RELAX_RIBBON(12,31),
    SNOOZE_RIBBON(13,32),
    SMILE_RIBBON(14,33),
    LEGEND_RIBBON(15,34);
    private byte index;
    private byte type;
    private static PokemonNormalRibbonType[] allTypeArray = {
            CHAMPION_RIBBON,
            EFFORT_RIBBON,
            GIFT_RIBBON,
            WINNING_RIBBON,
            VICTORY_RIBBON,
            GORGEOUS_RIBBON,
            ROYAL_RIBBON,
            GORGEOUS_ROYAL_RIBBON,
            ALERT_RIBBON,
            SHOCK_RIBBON,
            DOWNCAST_RIBBON,
            CARELESS_RIBBON,
            RELAX_RIBBON,
            SNOOZE_RIBBON,
            SMILE_RIBBON,
            LEGEND_RIBBON
    };
    PokemonNormalRibbonType(int index, int type) {
        this.index = (byte) index;
        this.type = (byte) type;
    }
    public byte getIndex() {
        return index;
    }
    public byte getType() {
        return type;
    }
    public static PokemonNormalRibbonType getByIndex(int index) {
        return allTypeArray[index];
    }
}
