package org.pokemmo.gameserver.game.pokemon;

public enum PokemonNatureType {
    HARDY(0,null,null,null,null),
    LONELY(1,PokemonStatType.ATTACK,PokemonStatType.DEFENSE,PokemonNatureFlavorType.SPICY, PokemonNatureFlavorType.SOUR),
    BRAVE(2,PokemonStatType.ATTACK,PokemonStatType.SPEED,PokemonNatureFlavorType.SPICY, PokemonNatureFlavorType.BITTER),
    ADAMANT(3,PokemonStatType.ATTACK,PokemonStatType.SPECIAL_ATTACK,PokemonNatureFlavorType.SPICY, PokemonNatureFlavorType.DRY),
    NAUGHTY(4,PokemonStatType.ATTACK,PokemonStatType.SPECIAL_DEFENSE,PokemonNatureFlavorType.SOUR, PokemonNatureFlavorType.SWEET),
    BOLD(5,PokemonStatType.DEFENSE,PokemonStatType.ATTACK,PokemonNatureFlavorType.SOUR, PokemonNatureFlavorType.SPICY),
    DOCILE (6,null,null,null,null),
    RELAXED(7,PokemonStatType.DEFENSE,PokemonStatType.SPEED,PokemonNatureFlavorType.SOUR, PokemonNatureFlavorType.BITTER),
    IMPISH(8,PokemonStatType.DEFENSE,PokemonStatType.SPECIAL_ATTACK,PokemonNatureFlavorType.SOUR, PokemonNatureFlavorType. SPICY),
    LAX(9,PokemonStatType.DEFENSE,PokemonStatType.SPECIAL_DEFENSE,PokemonNatureFlavorType.SOUR, PokemonNatureFlavorType.SWEET),
    TIMID(10,PokemonStatType.SPEED,PokemonStatType.ATTACK,PokemonNatureFlavorType.BITTER, PokemonNatureFlavorType.SPICY),
    HASTY(11,PokemonStatType.SPEED,PokemonStatType.DEFENSE,PokemonNatureFlavorType.BITTER, PokemonNatureFlavorType.SOUR),
    SERIOUS(12,null,null,null,null),
    JOLLY(13,PokemonStatType.SPEED,PokemonStatType.SPECIAL_ATTACK,PokemonNatureFlavorType.BITTER, PokemonNatureFlavorType.SPICY),
    NAIVE(14,PokemonStatType.SPEED,PokemonStatType.SPECIAL_DEFENSE,PokemonNatureFlavorType.BITTER, PokemonNatureFlavorType.SWEET),
    MODEST(15,PokemonStatType.SPECIAL_ATTACK,PokemonStatType.ATTACK,PokemonNatureFlavorType.DRY, PokemonNatureFlavorType.SPICY),
    MILD(16,PokemonStatType.SPECIAL_ATTACK,PokemonStatType.DEFENSE,PokemonNatureFlavorType.DRY, PokemonNatureFlavorType.SOUR),
    QUIET(17,PokemonStatType.SPECIAL_ATTACK,PokemonStatType.SPEED,PokemonNatureFlavorType.DRY, PokemonNatureFlavorType.BITTER),
    BASHFUL(18,null,null,null,null),
    RASH(19,PokemonStatType.SPECIAL_ATTACK,PokemonStatType.SPECIAL_DEFENSE,PokemonNatureFlavorType.DRY, PokemonNatureFlavorType.SWEET),
    CALM(20,PokemonStatType.SPECIAL_DEFENSE,PokemonStatType.ATTACK,PokemonNatureFlavorType.SWEET, PokemonNatureFlavorType.SPICY),
    GENTLE(21,PokemonStatType.SPECIAL_DEFENSE,PokemonStatType.DEFENSE,PokemonNatureFlavorType.SWEET, PokemonNatureFlavorType.SOUR),
    SASSY(22,PokemonStatType.SPECIAL_DEFENSE,PokemonStatType.SPEED,PokemonNatureFlavorType.SWEET, PokemonNatureFlavorType.BITTER),
    CAREFUL(23,PokemonStatType.SPECIAL_DEFENSE,PokemonStatType.SPECIAL_ATTACK,PokemonNatureFlavorType.SWEET, PokemonNatureFlavorType.DRY),
    QUIRKY(24,null,null,null,null);
    private byte type;
    private PokemonStatType increasedStat;
    private PokemonStatType decreasedStat;
    private PokemonNatureFlavorType likedFlavor;
    private PokemonNatureFlavorType dislikedFlavor;
    static PokemonNatureType[] allTypeArray = {
        HARDY, LONELY, BRAVE, ADAMANT, NAUGHTY,
        BOLD, DOCILE, RELAXED, IMPISH, LAX,
        TIMID, HASTY, SERIOUS, JOLLY, NAIVE,
        MODEST, MILD, QUIET, BASHFUL, RASH,
        CALM, GENTLE, SASSY, CAREFUL, QUIRKY
    };
    PokemonNatureType(int type, PokemonStatType increasedStat, PokemonStatType decreasedStat, PokemonNatureFlavorType likedFlavor, PokemonNatureFlavorType dislikedFlavor) {
        this.type = (byte) type;
        this.increasedStat = increasedStat;
        this.decreasedStat = decreasedStat;
        this.likedFlavor = likedFlavor;
        this.dislikedFlavor = dislikedFlavor;
    }
    public byte getType() {
        return type;
    }
    public PokemonStatType getIncreasedStat() {
        return increasedStat;
    }
    public PokemonStatType getDecreasedStat() {
        return decreasedStat;
    }
    public PokemonNatureFlavorType getLikedFlavor() {
        return likedFlavor;
    }
    public PokemonNatureFlavorType getDislikedFlavor() {
        return dislikedFlavor;
    }
    public double getStatMultiplier(PokemonStatType statType) {
        if (statType == null) {
            return 1.0d;
        }
        if (statType == increasedStat) {
            return 1.1d;
        }
        if (statType == decreasedStat) {
            return 0.9d;
        }
        return 1.0d;
    }
    public static PokemonNatureType getByType(int type) {
        if (type < 0 || type >= allTypeArray.length) {
            return HARDY;
        }
        return allTypeArray[type];
    }
    public static PokemonNatureType getByPersonalityValue(int personalityValue) {
        return getByType(Math.floorMod(personalityValue, allTypeArray.length));
    }
}
