package org.pokemmo.gameserver.game.pokemon;

public final class PokemonRibbonMask {
    public static final int CONTEST_RIBBON_GROUPS = 5;
    public static final int STORED_CONTEST_RIBBON_COUNT = 6;
    public static final int MAX_CONTEST_RIBBON_LEVEL = 4;

    private static final long SUPPORTED_MASK = createSupportedMask();

    private PokemonRibbonMask() {
    }

    public static long encode(short[] contestRibbons, boolean[] normalRibbons) {
        if (contestRibbons == null || contestRibbons.length < CONTEST_RIBBON_GROUPS) {
            throw new IllegalArgumentException("Contest ribbon data must contain at least five groups.");
        }
        if (normalRibbons == null || normalRibbons.length != PokemonNormalRibbonType.values().length) {
            throw new IllegalArgumentException("Normal ribbon data must contain exactly 16 entries.");
        }

        long value = 0;
        for (int group = 0; group < CONTEST_RIBBON_GROUPS; group++) {
            short level = contestRibbons[group];
            if (level < 0 || level > MAX_CONTEST_RIBBON_LEVEL) {
                throw new IllegalArgumentException("Contest ribbon levels must be from 0 to 4.");
            }
            value |= (long) level << contestRibbonShift(group);
        }
        for (PokemonNormalRibbonType ribbonType : PokemonNormalRibbonType.values()) {
            int index = Byte.toUnsignedInt(ribbonType.getIndex());
            if (normalRibbons[index]) {
                value |= 1L << Byte.toUnsignedInt(ribbonType.getType());
            }
        }
        return value;
    }

    public static DecodedRibbons decode(long value) {
        if (value < 0 || (value & ~SUPPORTED_MASK) != 0) {
            throw new IllegalArgumentException("Ribbon mask contains unsupported bits.");
        }

        short[] contestRibbons = new short[STORED_CONTEST_RIBBON_COUNT];
        for (int group = 0; group < CONTEST_RIBBON_GROUPS; group++) {
            short level = (short) ((value >>> contestRibbonShift(group)) & 0x7L);
            if (level > MAX_CONTEST_RIBBON_LEVEL) {
                throw new IllegalArgumentException("Contest ribbon levels encoded in the mask must be from 0 to 4.");
            }
            contestRibbons[group] = level;
        }

        boolean[] normalRibbons = new boolean[PokemonNormalRibbonType.values().length];
        for (PokemonNormalRibbonType ribbonType : PokemonNormalRibbonType.values()) {
            int index = Byte.toUnsignedInt(ribbonType.getIndex());
            int bit = Byte.toUnsignedInt(ribbonType.getType());
            normalRibbons[index] = (value & (1L << bit)) != 0;
        }
        return new DecodedRibbons(contestRibbons, normalRibbons);
    }

    public static long getSupportedMask() {
        return SUPPORTED_MASK;
    }

    private static int contestRibbonShift(int group) {
        return group * 3 + 1;
    }

    private static long createSupportedMask() {
        long value = 0;
        for (int group = 0; group < CONTEST_RIBBON_GROUPS; group++) {
            value |= 0x7L << contestRibbonShift(group);
        }
        for (PokemonNormalRibbonType ribbonType : PokemonNormalRibbonType.values()) {
            value |= 1L << Byte.toUnsignedInt(ribbonType.getType());
        }
        return value;
    }

    public record DecodedRibbons(short[] contestRibbons, boolean[] normalRibbons) {
    }
}
