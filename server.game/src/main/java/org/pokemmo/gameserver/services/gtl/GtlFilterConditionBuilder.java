package org.pokemmo.gameserver.services.gtl;

import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.SortField;
import org.jooq.impl.DSL;
import org.pokemmo.gameserver.game.gtl.GtlFilterType;
import org.pokemmo.gameserver.game.gtl.GtlGenderFilterType;
import org.pokemmo.gameserver.game.gtl.GtlRequestState;
import org.pokemmo.gameserver.game.gtl.GtlShinyFilterType;
import org.pokemmo.gameserver.game.pokemon.PokemonAbility;
import org.pokemmo.gameserver.game.pokemon.PokemonDexData;
import org.pokemmo.gameserver.game.pokemon.PokemonEggGroupType;
import org.pokemmo.gameserver.game.pokemon.PokemonManager;
import org.pokemmo.gameserver.game.pokemon.PokemonNatureType;
import org.pokemmo.gameserver.game.skin.SkinType;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.pokemmo.db.jooq.Tables.OWNED_ITEM;
import static org.pokemmo.db.jooq.Tables.POKEMON;

final class GtlFilterConditionBuilder {
    private GtlFilterConditionBuilder() {
    }

static List<SortField<?>> getGtlSortFields(GtlFilterType filterType) {
      return switch (filterType) {
        case NEWEST -> List.of(GtlSchema.GTL_CREATED_AT.desc(), GtlSchema.GTL_LISTING_ID.desc());
        case EARLIEST -> List.of(GtlSchema.GTL_CREATED_AT.asc(), GtlSchema.GTL_LISTING_ID.asc());
        case LOWEST_PRICE -> List.of(
                GtlSchema.GTL_UNIT_PRICE.asc(), GtlSchema.GTL_CREATED_AT.desc(), GtlSchema.GTL_LISTING_ID.desc());
        case HIGHEST_PRICE -> List.of(
                GtlSchema.GTL_UNIT_PRICE.desc(), GtlSchema.GTL_CREATED_AT.desc(), GtlSchema.GTL_LISTING_ID.desc());
      };
    }

    static Condition getPokemonNatureCondition(PokemonNatureType pokemonNatureType) {
      int natureIndex = Byte.toUnsignedInt(pokemonNatureType.getType());
      return POKEMON.PERSONALITY_VALUE.mod(25).add(25).mod(25).eq(natureIndex);
    }

    static Condition getPokemonGenderCondition(GtlGenderFilterType genderFilterType) {
      Map<Integer, List<Short>> dexIdsByGenderRatio = new HashMap<>();
      List<Short> genderlessDexIds = new ArrayList<>();
      for (PokemonDexData dexData : PokemonManager.getAllPokemonDexData()) {
        int genderRatio = Short.toUnsignedInt(dexData.getGenderRatio());
        if (genderRatio == 0xFF) {
          if (genderFilterType == GtlGenderFilterType.GENDERLESS) {
            genderlessDexIds.add(dexData.getPokemonIndexId());
          }
          continue;
        }
        dexIdsByGenderRatio
                .computeIfAbsent(genderRatio, ignored -> new ArrayList<>())
                .add(dexData.getPokemonIndexId());
      }

      if (genderFilterType == GtlGenderFilterType.GENDERLESS) {
        return genderlessDexIds.isEmpty()
                ? DSL.falseCondition()
                : POKEMON.DEX_ID.in(genderlessDexIds);
      }

      Field<Integer> personalityLowByte = POKEMON.PERSONALITY_VALUE.bitAnd(0xFF);
      Condition condition = DSL.falseCondition();
      for (Map.Entry<Integer, List<Short>> entry : dexIdsByGenderRatio.entrySet()) {
        Condition ratioCondition = genderFilterType == GtlGenderFilterType.MALE
                ? personalityLowByte.ge(entry.getKey())
                : personalityLowByte.lt(entry.getKey());
        condition = condition.or(
                POKEMON.DEX_ID.in(entry.getValue()).and(ratioCondition));
      }
      return condition;
    }

    static Condition getPokemonEggGroupCondition(
            PokemonEggGroupType eggGroupFilterType) {
      List<Short> matchingDexIds = PokemonManager.getAllPokemonDexData().stream()
              .filter(dexData -> dexData.getEggGroup() != null
                      && dexData.getEggGroup().contains(eggGroupFilterType))
              .map(PokemonDexData::getPokemonIndexId)
              .toList();
      return matchingDexIds.isEmpty()
              ? DSL.falseCondition()
              : POKEMON.DEX_ID.in(matchingDexIds);
    }

    static boolean isValidGtlPriceRange(Integer minPrice, Integer maxPrice) {
      return (minPrice == null || minPrice >= 0)
              && (maxPrice == null || maxPrice >= 0)
              && (minPrice == null || maxPrice == null || minPrice <= maxPrice);
    }

    static boolean isValidGtlFashionSlot(Integer itemFashionSlot) {
      return itemFashionSlot == null || SkinType.isGtlFashionSlot(itemFashionSlot);
    }

    /** Builds the client item-index condition for one of the ten fashion slots. */
    static Condition getGtlFashionItemCondition(int itemFashionSlot) {
      SkinType skinType = SkinType.getByType(itemFashionSlot);
      if (skinType == null || !SkinType.isGtlFashionSlot(itemFashionSlot)) {
        return DSL.falseCondition();
      }

      int baseItemIndex = 2_000 + skinType.getType() * 256;
      Condition condition = OWNED_ITEM.ITEM_INDEX_ID.ge((short) baseItemIndex)
              .and(OWNED_ITEM.ITEM_INDEX_ID.le((short) (baseItemIndex + 255)));
      if (skinType == SkinType.HAT) {
        condition = condition.or(
                OWNED_ITEM.ITEM_INDEX_ID.ge((short) 4_576)
                        .and(OWNED_ITEM.ITEM_INDEX_ID.le((short) 4_815)));
      }
      return condition;
    }

    static boolean isValidGtlLevelRange(Integer minLevel, Integer maxLevel) {
      return (minLevel == null || (minLevel >= 1 && minLevel <= 100))
              && (maxLevel == null || (maxLevel >= 1 && maxLevel <= 100))
              && (minLevel == null || maxLevel == null || minLevel <= maxLevel);
    }

    static boolean isValidGtlEggYearRange(
            Long minEggYearTimestamp, Long maxEggYearTimestamp) {
      return (minEggYearTimestamp == null) == (maxEggYearTimestamp == null)
              && (minEggYearTimestamp == null
              || (minEggYearTimestamp >= 0 && minEggYearTimestamp <= 0xFFFF_FFFFL
              && maxEggYearTimestamp >= 0 && maxEggYearTimestamp <= 0xFFFF_FFFFL
              && minEggYearTimestamp <= maxEggYearTimestamp));
    }

    static boolean isValidGtlIvLimit(Integer ivIndex, Integer ivLimit) {
      return (ivIndex == null && ivLimit == null)
              || (ivIndex != null && ivLimit != null
              && ivIndex >= 0 && ivIndex <= 5
              && ivLimit >= 0 && ivLimit <= 31);
    }

    static boolean isValidGtlIvLimitRange(
            Integer minIvLimitIndex,
            Integer minIvLimit,
            Integer maxIvLimitIndex,
            Integer maxIvLimit) {
      return minIvLimitIndex == null
              || maxIvLimitIndex == null
              || !minIvLimitIndex.equals(maxIvLimitIndex)
              || (minIvLimit != null && maxIvLimit != null && minIvLimit <= maxIvLimit);
    }

    static boolean isValidGtlIvMinMatchCount(Integer ivMinMatchCount) {
      return ivMinMatchCount == null
              || (ivMinMatchCount >= 0
              && ivMinMatchCount <= GtlRequestState.MAX_IV_MIN_MATCH_COUNT);
    }

    static boolean isValidGtlEvLimit(Integer evIndex, Integer evLimit) {
      return (evIndex == null && evLimit == null)
              || (evIndex != null && evLimit != null
              && evIndex >= 0 && evIndex <= 5
              && evLimit >= 0 && evLimit <= GtlRequestState.MAX_EV_LIMIT);
    }

    static boolean isValidGtlEvLimitRange(
            Integer minEvLimitIndex,
            Integer minEvLimit,
            Integer maxEvLimitIndex,
            Integer maxEvLimit) {
      return minEvLimitIndex == null
              || maxEvLimitIndex == null
              || !minEvLimitIndex.equals(maxEvLimitIndex)
              || (minEvLimit != null && maxEvLimit != null && minEvLimit <= maxEvLimit);
    }

    static Condition applyGtlPriceCondition(
            Condition condition, Integer minPrice, Integer maxPrice) {
      if (minPrice != null) {
        condition = condition.and(GtlSchema.GTL_UNIT_PRICE.ge(minPrice));
      }
      if (maxPrice != null) {
        condition = condition.and(GtlSchema.GTL_UNIT_PRICE.le(maxPrice));
      }
      return condition;
    }

    static Condition applyGtlLevelCondition(
            Condition condition, Integer minLevel, Integer maxLevel) {
      if (minLevel != null) {
        condition = condition.and(POKEMON.LEVEL_VALUE.ge((short) minLevel.intValue()));
      }
      if (maxLevel != null) {
        condition = condition.and(POKEMON.LEVEL_VALUE.le((short) maxLevel.intValue()));
      }
      return condition;
    }

    static Condition applyGtlEggYearCondition(
            Condition condition, Long minEggYearTimestamp, Long maxEggYearTimestamp) {
      if (minEggYearTimestamp == null && maxEggYearTimestamp == null) {
        return condition;
      }
      return condition.and(getPokemonEggYearCondition(
              minEggYearTimestamp, maxEggYearTimestamp));
    }

    static Condition getPokemonEggYearCondition(
            Long minEggYearTimestamp, Long maxEggYearTimestamp) {
      Condition condition = DSL.noCondition();
      if (minEggYearTimestamp != null) {
        condition = condition.and(POKEMON.CATCH_TIME.gt(toUtcDateTime(minEggYearTimestamp)));
      }
      if (maxEggYearTimestamp != null) {
        condition = condition.and(POKEMON.CATCH_TIME.lt(toUtcDateTime(maxEggYearTimestamp)));
      }
      return condition;
    }

    static LocalDateTime toUtcDateTime(long epochSeconds) {
      return LocalDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), ZoneOffset.UTC);
    }

    static Condition applyGtlIvCondition(
            Condition condition,
            Integer minIvLimitIndex,
            Integer minIvLimit,
            Integer maxIvLimitIndex,
            Integer maxIvLimit) {
      return condition.and(getPokemonIvCondition(
              minIvLimitIndex, minIvLimit, maxIvLimitIndex, maxIvLimit));
    }

    static Condition applyGtlEvCondition(
            Condition condition, Integer minEvLimitIndex, Integer minEvLimit) {
      return applyGtlEvCondition(condition, minEvLimitIndex, minEvLimit, null, null);
    }

    static Condition applyGtlEvCondition(
            Condition condition,
            Integer minEvLimitIndex,
            Integer minEvLimit,
            Integer maxEvLimitIndex,
            Integer maxEvLimit) {
      return condition.and(getPokemonEvCondition(
              minEvLimitIndex, minEvLimit, maxEvLimitIndex, maxEvLimit));
    }

    static Condition getPokemonIvCondition(
            Integer minIvLimitIndex,
            Integer minIvLimit,
            Integer maxIvLimitIndex,
            Integer maxIvLimit) {
      Condition condition = DSL.trueCondition();
      if (minIvLimitIndex != null && minIvLimit != null) {
        condition = condition.and(getPokemonIvValueField(minIvLimitIndex)
                .ge((short) minIvLimit.intValue()));
      }
      if (maxIvLimitIndex != null && maxIvLimit != null) {
        condition = condition.and(getPokemonIvValueField(maxIvLimitIndex)
                .le((short) maxIvLimit.intValue()));
      }
      return condition;
    }

    static Condition getPokemonIvMinMatchCountCondition(int ivMinMatchCount) {
      return DSL.condition(
              "cardinality(array_positions({0}, CAST(31 AS smallint))) >= {1}",
              POKEMON.IV_VALUES,
              DSL.val(ivMinMatchCount));
    }

    static Condition getPokemonExcludeFemaleOnlySpeciesCondition() {
      List<Short> matchingDexIds = PokemonManager.getAllPokemonDexData().stream()
              .filter(dexData -> Short.toUnsignedInt(dexData.getGenderRatio())
                      == GtlRequestState.FEMALE_ONLY_GENDER_RATIO)
              .map(PokemonDexData::getPokemonIndexId)
              .toList();
      return matchingDexIds.isEmpty()
              ? DSL.trueCondition()
              : POKEMON.DEX_ID.notIn(matchingDexIds);
    }

    static Condition getPokemonExcludeDittoCondition() {
      return POKEMON.DEX_ID.ne(GtlSchema.DITTO_DEX_ID);
    }

    static Field<Short> getPokemonIvValueField(int ivIndex) {
      return DSL.field("{0}[{1}]", Short.class, POKEMON.IV_VALUES, ivIndex + 1);
    }

    static Condition getPokemonEvCondition(
            Integer minEvLimitIndex, Integer minEvLimit) {
      return getPokemonEvCondition(minEvLimitIndex, minEvLimit, null, null);
    }

    static Condition getPokemonEvCondition(
            Integer minEvLimitIndex,
            Integer minEvLimit,
            Integer maxEvLimitIndex,
            Integer maxEvLimit) {
      if ((minEvLimitIndex == null || minEvLimit == null)
              && (maxEvLimitIndex == null || maxEvLimit == null)) {
        return DSL.trueCondition();
      }
      Condition condition = DSL.trueCondition();
      if (minEvLimitIndex != null && minEvLimit != null) {
        condition = condition.and(getPokemonEvValueField(minEvLimitIndex)
                .ge((short) minEvLimit.intValue()));
      }
      if (maxEvLimitIndex != null && maxEvLimit != null) {
        condition = condition.and(getPokemonEvValueField(maxEvLimitIndex)
                .le((short) maxEvLimit.intValue()));
      }
      return condition;
    }

    static Field<Short> getPokemonEvValueField(int evIndex) {
      return DSL.field("{0}[{1}]", Short.class, POKEMON.EV_VALUES, evIndex + 1);
    }

    static Condition getPokemonShinyCondition(GtlShinyFilterType shinyFilterType) {
      return switch (shinyFilterType) {
        case ANY_SHINY -> POKEMON.IS_SHINY.eq(true).or(POKEMON.IS_SECRET.eq(true));
        case SECRET_SHINY -> POKEMON.IS_SECRET.eq(true);
        case NORMAL_SHINY -> POKEMON.IS_SHINY.eq(true).and(POKEMON.IS_SECRET.eq(false));
        case NOT_SHINY -> POKEMON.IS_SHINY.eq(false).and(POKEMON.IS_SECRET.eq(false));
      };
    }

    static Condition getPokemonParticleCondition(Integer particleFilterType) {
      if (particleFilterType != null
              && particleFilterType == GtlRequestState.ANY_PARTICLE_FILTER_TYPE) {
        return DSL.condition(
                "cardinality({0}) > 0",
                POKEMON.PARTICLE_EFFECTS);
      }
      if (particleFilterType == null || particleFilterType < 0 || particleFilterType > 38) {
        return DSL.falseCondition();
      }
      return DSL.condition(
              "{0} @> ARRAY[{1}]::smallint[]",
              POKEMON.PARTICLE_EFFECTS,
              DSL.val((short) particleFilterType.intValue()));
    }

    static Condition getPokemonMoveCondition(Integer learnedMoveIndexId) {
      if (learnedMoveIndexId == null || !isValidGtlMoveIndexId(learnedMoveIndexId)) {
        return DSL.falseCondition();
      }
      return DSL.condition(
              "{0} @> ARRAY[{1}]::smallint[]",
              POKEMON.MOVES,
              DSL.val((short) learnedMoveIndexId.intValue()));
    }

    /**
     * Matches the client's global ability ID against the per-species ability slot
     * stored in {@code pokemon.ability}.
     */
    static Condition getPokemonAbilityCondition(Integer pokemonAbilityIndexId) {
      if (!isValidGtlAbilityIndexId(pokemonAbilityIndexId)) {
        return DSL.falseCondition();
      }
      Condition condition = DSL.falseCondition();
      for (PokemonDexData dexData : PokemonManager.getAllPokemonDexData()) {
        List<PokemonAbility> abilities = dexData.getPokemonAbilities();
        if (abilities == null) {
          continue;
        }
        for (int slot = 0; slot < abilities.size(); slot++) {
          PokemonAbility ability = abilities.get(slot);
          if (ability != null && ability.getAbilityIndexId() == pokemonAbilityIndexId) {
            condition = condition.or(
                    POKEMON.DEX_ID.eq(dexData.getPokemonIndexId())
                            .and(POKEMON.ABILITY.eq((short) slot)));
          }
        }
      }
      return condition;
    }

    static Condition getPokemonHiddenAbilityCondition(Integer hiddenAbilityFilterType) {
      return switch (hiddenAbilityFilterType) {
        case GtlRequestState.HIDDEN_ABILITY_FALSE -> POKEMON.HAS_HIDDEN_ABILITY.eq(false);
        case GtlRequestState.HIDDEN_ABILITY_TRUE -> POKEMON.HAS_HIDDEN_ABILITY.eq(true);
        default -> DSL.falseCondition();
      };
    }

    static Condition getPokemonAlphaCondition(Integer alphaFilterType) {
      return switch (alphaFilterType) {
        case GtlRequestState.ALPHA_NORMAL -> POKEMON.IS_ALPHA.eq(false);
        case GtlRequestState.ALPHA_ONLY -> POKEMON.IS_ALPHA.eq(true);
        case GtlRequestState.FATEFUL_ENCOUNTER -> DSL.falseCondition();
        default -> DSL.falseCondition();
      };
    }

    static boolean isValidGtlHiddenAbilityFilterType(Integer filterType) {
      return filterType == null
              || filterType == GtlRequestState.HIDDEN_ABILITY_FALSE
              || filterType == GtlRequestState.HIDDEN_ABILITY_TRUE;
    }

    static boolean isValidGtlAlphaFilterType(Integer filterType) {
      return filterType == null
              || filterType == GtlRequestState.ALPHA_NORMAL
              || filterType == GtlRequestState.ALPHA_ONLY
              || filterType == GtlRequestState.FATEFUL_ENCOUNTER;
    }

    static boolean isValidGtlMoveIndexId(Integer learnedMoveIndexId) {
      return learnedMoveIndexId == null
              || (learnedMoveIndexId > 0 && learnedMoveIndexId <= Short.MAX_VALUE);
    }

    static boolean isValidGtlAbilityIndexId(Integer pokemonAbilityIndexId) {
      return pokemonAbilityIndexId == null
              || (pokemonAbilityIndexId > 0 && pokemonAbilityIndexId <= Short.MAX_VALUE);
    }
}

