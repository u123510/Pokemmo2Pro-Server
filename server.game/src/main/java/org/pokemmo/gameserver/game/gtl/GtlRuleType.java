package org.pokemmo.gameserver.game.gtl;

import java.util.HashMap;

public enum GtlRuleType {
    POKEMON_INDEX_ID(0),
    POKEMON_SEX(1),
    POKEMON_NATURE(2),
    MIN_LEVEL(3),
    MAX_LEVEL(4),
    MIN_IV(5),
    MAX_IV(6),
    EGG_GROUP(7),
    SHINY(8),
    MIN_PRICE(9),
    MAX_PRICE(10),
    PVP_LEVEL(11),
    POKEMON_PARTICLE(12),
    POKEMON_HIDDEN_ABILITY(13),
    POKEMON_ALPHA(14),
    EGG_YEAR(15),
    LEARNED_MOVE(16),
    MIN_EV(17),
    MAX_EV(18),
    IV_MIN_MATCH(19),
    POKEMON_ABILITY(20),
    HIDDEN_ONLY_FEMALE(21),
    HIDDEN_DITTO(22),
    ALT_FORM_UNLOCKED(24),
    ITEM_POCKET(32),
    ITEM_FASHION_SLOT(33);
    private static final HashMap<Integer, GtlRuleType> ALL_TYPES = new HashMap<>();

    static {
        for (GtlRuleType ruleType : values()) {
            ALL_TYPES.put(Byte.toUnsignedInt(ruleType.type), ruleType);
        }
    }

    private final byte type;
    GtlRuleType(int type){
        this.type = (byte) type;
    }
    public byte getType() {
        return type;
    }
    public static GtlRuleType getByType(int type){
        return ALL_TYPES.get(type);
    }
}
