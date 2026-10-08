package org.pokemmo.gameserver.protocol.packets.c2s;

import org.pokemmo.gameserver.game.gtl.GtlFilterType;
import org.pokemmo.gameserver.game.gtl.GtlGenderFilterType;
import org.pokemmo.gameserver.game.gtl.GtlListType;
import org.pokemmo.gameserver.game.gtl.GtlRequestState;
import org.pokemmo.gameserver.game.gtl.GtlShinyFilterType;
import org.pokemmo.gameserver.game.pokemon.PokemonEggGroupType;
import org.pokemmo.gameserver.game.pokemon.PokemonNatureType;

import java.util.List;

/** Parsed, validated wire fields for a GTL search request. */
record GtlDecodedRequest(
        byte requestSequence,
        byte listType,
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
        short pageIndex,
        List<Short> pokemonDexIds,
        List<Short> itemIndexIds) {
    GtlRequestState toState() {
        GtlListType decodedListType = GtlListType.getListType(listType & 0xFF);
        return new GtlRequestState(requestSequence, decodedListType, filterType,
                genderFilterType, minPrice, maxPrice, minLevel, maxLevel,
                minEggYearTimestamp, maxEggYearTimestamp, eggGroupFilterType,
                pokemonNatureType, shinyFilterType, particleFilterType,
                learnedMoveIndexId, pokemonAbilityIndexId, hiddenAbilityFilterType,
                alphaFilterType, minIvLimitIndex, minIvLimit, maxIvLimitIndex,
                maxIvLimit, minEvLimitIndex, minEvLimit, maxEvLimitIndex,
                maxEvLimit, ivMinMatchCount, hideFemaleOnlySpecies, hideDitto,
                altFormUnlocked, pageIndex, pokemonDexIds, itemIndexIds,
                itemFashionSlot);
    }
}
