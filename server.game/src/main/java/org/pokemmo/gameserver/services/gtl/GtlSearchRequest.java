package org.pokemmo.gameserver.services.gtl;

import org.pokemmo.gameserver.game.gtl.GtlFilterType;
import org.pokemmo.gameserver.game.gtl.GtlGenderFilterType;
import org.pokemmo.gameserver.game.gtl.GtlListType;
import org.pokemmo.gameserver.game.gtl.GtlRequestState;
import org.pokemmo.gameserver.game.gtl.GtlShinyFilterType;
import org.pokemmo.gameserver.game.pokemon.PokemonEggGroupType;
import org.pokemmo.gameserver.game.pokemon.PokemonNatureType;

import java.util.List;

/** Immutable canonical query parameters used by new GTL callers. */
public record GtlSearchRequest(
        Long sellerId,
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
        Integer itemFashionSlot,
        List<Short> pokemonDexIds,
        List<Short> itemIndexIds,
        int pageIndex,
        int pageSize) {
    public GtlSearchRequest {
        pokemonDexIds = pokemonDexIds == null ? List.of() : List.copyOf(pokemonDexIds);
        itemIndexIds = itemIndexIds == null ? List.of() : List.copyOf(itemIndexIds);
    }

    public static GtlSearchRequest fromState(GtlRequestState state, Long sellerId, int pageSize) {
        return new GtlSearchRequest(
                sellerId, state.listType(), state.filterType(), state.genderFilterType(),
                state.minPrice(), state.maxPrice(), state.minLevel(), state.maxLevel(),
                state.minEggYearTimestamp(), state.maxEggYearTimestamp(),
                state.eggGroupFilterType(), state.pokemonNatureType(), state.shinyFilterType(),
                state.particleFilterType(), state.learnedMoveIndexId(), state.pokemonAbilityIndexId(),
                state.hiddenAbilityFilterType(), state.alphaFilterType(), state.minIvLimitIndex(),
                state.minIvLimit(), state.maxIvLimitIndex(), state.maxIvLimit(),
                state.minEvLimitIndex(), state.minEvLimit(), state.maxEvLimitIndex(), state.maxEvLimit(),
                state.ivMinMatchCount(), state.hideFemaleOnlySpecies(), state.hideDitto(),
                state.altFormUnlocked(), state.itemFashionSlot(), state.pokemonDexIds(),
                state.itemIndexIds(), state.pageIndex(), pageSize);
    }
}
