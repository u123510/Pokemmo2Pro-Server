package org.pokemmo.gameserver.game.pokemon;

public final class PokemonGenderUtil {
    private PokemonGenderUtil() {
    }

    public static int withGender(int personalityValue, short genderRatio, byte gender) {
        if (gender != 0 && gender != 1) {
            throw new IllegalArgumentException("Gender must be 0 or 1");
        }
        if (genderRatio < 0 || genderRatio > 255) {
            throw new IllegalArgumentException("Gender ratio must be between 0 and 255");
        }
        if (genderRatio == 255) {
            throw new IllegalArgumentException("Genderless Pokemon cannot have a gender");
        }

        // Keep the low byte strictly on one side of the ratio boundary. The
        // client treats a value equal to genderRatio as male, so using the
        // boundary itself for female can leave the client and server out of
        // sync.
        int currentGender = (personalityValue & 0xFF) >= genderRatio ? 0 : 1;
        if (currentGender == gender) {
            return personalityValue;
        }
        if (gender == 1 && genderRatio == 0) {
            throw new IllegalArgumentException("Male-only Pokemon cannot be female");
        }
        int targetLowByte = gender == 0 ? genderRatio : genderRatio - 1;
        int baseValue = (personalityValue & 0xFFFFFF00) | (targetLowByte & 0xFF);
        int natureValue = Math.floorMod(personalityValue, 25);
        for (int offset = -24; offset <= 24; offset++) {
            long candidate = (long) baseValue + 256L * offset;
            if (candidate >= Integer.MIN_VALUE && candidate <= Integer.MAX_VALUE
                    && Math.floorMod(candidate, 25) == natureValue) {
                return (int) candidate;
            }
        }
        throw new IllegalStateException("Unable to preserve Pokemon nature while changing gender");
    }
}
