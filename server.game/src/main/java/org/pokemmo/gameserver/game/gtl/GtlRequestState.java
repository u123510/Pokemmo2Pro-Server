package org.pokemmo.gameserver.game.gtl;

import java.util.List;

import org.pokemmo.gameserver.game.pokemon.PokemonEggGroupType;
import org.pokemmo.gameserver.game.pokemon.PokemonNatureType;

/**
 * The last GTL page requested by a client session. The request sequence is
 * echoed by the server so the client can accept an unsolicited refresh.
 * Rule 24 is retained even though the current schema cannot express its
 * client-side rarity bit.
 */
public record GtlRequestState(
        byte requestSequence,
        GtlListType listType,
        GtlFilterType filterType,
        GtlGenderFilterType genderFilterType,
        Integer minPrice,
        Integer maxPrice,
        Integer minLevel,
        Integer maxLevel,
        Long minEggYearTimestamp,
        Long maxEggYearTimestamp,
        PokemonEggGroupType eggGroupFilterType,
        PokemonNatureType pokemonNatureType,
        GtlShinyFilterType shinyFilterType,
        Integer particleFilterType,
        Integer learnedMoveIndexId,
        Integer pokemonAbilityIndexId,
        Integer hiddenAbilityFilterType,
        Integer alphaFilterType,
        Integer minIvLimitIndex,
        Integer minIvLimit,
        Integer maxIvLimitIndex,
        Integer maxIvLimit,
        Integer minEvLimitIndex,
        Integer minEvLimit,
        Integer maxEvLimitIndex,
        Integer maxEvLimit,
        Integer ivMinMatchCount,
        boolean hideFemaleOnlySpecies,
        boolean hideDitto,
        boolean altFormUnlocked,
        short pageIndex,
        List<Short> pokemonDexIds,
        List<Short> itemIndexIds,
        Integer itemFashionSlot) {
    /** IV indexes: 0 = HP, 1 = Attack, 2 = Defense, 3 = Sp. Atk, 4 = Sp. Def, 5 = Speed. */
    /** Client sentinel for matching Pokemon with at least one particle. */
    public static final int ANY_PARTICLE_FILTER_TYPE = 0xFC;

    /** Rule 13 values: 0 = hidden ability, 1 = no hidden ability. */
    public static final int HIDDEN_ABILITY_TRUE = 0;
    public static final int HIDDEN_ABILITY_FALSE = 1;

    /** Rule 14 values: 0 = Alpha, 1 = normal, 2 = fateful encounter. */
    public static final int ALPHA_ONLY = 0;
    public static final int ALPHA_NORMAL = 1;
    public static final int FATEFUL_ENCOUNTER = 2;

    /** Rule 19 counts the number of perfect (31) IVs that must be present. */
    public static final int MAX_IV_MIN_MATCH_COUNT = 6;

    /** Rules 17/18 use the six EV indexes: 0 = HP, 1 = Attack, 2 = Defense,
     * 3 = Sp. Atk, 4 = Sp. Def, 5 = Speed. */
    public static final int MAX_EV_LIMIT = 255;

    /** Rule 21 excludes species whose dex gender ratio is female-only (254). */
    public static final int FEMALE_ONLY_GENDER_RATIO = 254;

    public GtlRequestState {
        pokemonDexIds = pokemonDexIds == null ? List.of() : List.copyOf(pokemonDexIds);
        itemIndexIds = itemIndexIds == null ? List.of() : List.copyOf(itemIndexIds);
    }

    public static GtlRequestState defaultMonsterPage() {
        return new GtlRequestState(
                (byte) 0,
                GtlListType.MONSTER,
                GtlFilterType.NEWEST,
                null,
                null, null,
                null, null,
                null, null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null, null,
                null, null,
                null, null,
                null, null,
                null,
                false,
                false,
                false,
                (short) 0,
                List.of(),
                List.of(),
                null);
    }
}
