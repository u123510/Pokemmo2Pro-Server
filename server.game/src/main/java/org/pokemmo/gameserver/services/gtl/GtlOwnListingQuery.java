package org.pokemmo.gameserver.services.gtl;

import lombok.extern.slf4j.Slf4j;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Record5;
import org.jooq.Result;
import org.jooq.SortField;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.pokemmo.db.Database;
import org.pokemmo.db.jooq.tables.records.CharacterRecord;
import org.pokemmo.db.jooq.tables.records.OwnedItemRecord;
import org.pokemmo.db.jooq.tables.records.PokemonRecord;
import org.pokemmo.gameserver.game.gtl.GtlActionResult;
import org.pokemmo.gameserver.game.gtl.GtlFilterType;
import org.pokemmo.gameserver.game.gtl.GtlGenderFilterType;
import org.pokemmo.gameserver.game.gtl.GtlItemListing;
import org.pokemmo.gameserver.game.gtl.GtlListingEntry;
import org.pokemmo.gameserver.game.gtl.GtlListingPage;
import org.pokemmo.gameserver.game.gtl.GtlListingType;
import org.pokemmo.gameserver.game.gtl.GtlPokemonListing;
import org.pokemmo.gameserver.game.gtl.GtlPurchaseHistoryEntry;
import org.pokemmo.gameserver.game.gtl.GtlRequestState;
import org.pokemmo.gameserver.game.gtl.GtlShinyFilterType;
import org.pokemmo.gameserver.game.item.ItemData;
import org.pokemmo.gameserver.game.item.ItemManager;
import org.pokemmo.gameserver.game.particleEffectType.ParticleEffectType;
import org.pokemmo.gameserver.game.pokemon.PokemonData;
import org.pokemmo.gameserver.game.pokemon.PokemonAbility;
import org.pokemmo.gameserver.game.pokemon.PokemonDexData;
import org.pokemmo.gameserver.game.pokemon.PokemonEggGroupType;
import org.pokemmo.gameserver.game.pokemon.PokemonManager;
import org.pokemmo.gameserver.game.pokemon.PokemonNatureType;
import org.pokemmo.gameserver.game.container.PokemonContainerType;
import org.pokemmo.gameserver.game.skin.SkinType;
import org.pokemmo.gameserver.services.GameServerService;
import org.pokemmo.gameserver.util.SnowflakeIdGenerator;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.pokemmo.db.jooq.Tables.CHARACTER;
import static org.pokemmo.db.jooq.Tables.OWNED_ITEM;
import static org.pokemmo.db.jooq.Tables.POKEMON;

@Slf4j
final class GtlOwnListingQuery {
  private final Database database;

  GtlOwnListingQuery(Database database) {
    this.database = database;
  }

  GtlListingPage search(GtlSearchRequest request) {
    long sellerId = request.sellerId() == null ? 0L : request.sellerId();
    List<Short> pokemonDexIds = request.pokemonDexIds();
    GtlFilterType filterType = request.filterType();
    GtlGenderFilterType genderFilterType = request.genderFilterType();
    Integer minPrice = request.minPrice();
    Integer maxPrice = request.maxPrice();
    Integer minLevel = request.minLevel();
    Integer maxLevel = request.maxLevel();
    PokemonEggGroupType eggGroupFilterType = request.eggGroupFilterType();
    PokemonNatureType pokemonNatureType = request.pokemonNatureType();
    GtlShinyFilterType shinyFilterType = request.shinyFilterType();
    Integer particleFilterType = request.particleFilterType();
    Integer learnedMoveIndexId = request.learnedMoveIndexId();
    Integer hiddenAbilityFilterType = request.hiddenAbilityFilterType();
    Integer alphaFilterType = request.alphaFilterType();
    Integer minIvLimitIndex = request.minIvLimitIndex();
    Integer minIvLimit = request.minIvLimit();
    Integer maxIvLimitIndex = request.maxIvLimitIndex();
    Integer maxIvLimit = request.maxIvLimit();
    Integer ivMinMatchCount = request.ivMinMatchCount();
    boolean hideFemaleOnlySpecies = request.hideFemaleOnlySpecies();
    Long minEggYearTimestamp = request.minEggYearTimestamp();
    Long maxEggYearTimestamp = request.maxEggYearTimestamp();
    Integer minEvLimitIndex = request.minEvLimitIndex();
    Integer minEvLimit = request.minEvLimit();
    Integer maxEvLimitIndex = request.maxEvLimitIndex();
    Integer maxEvLimit = request.maxEvLimit();
    Integer pokemonAbilityIndexId = request.pokemonAbilityIndexId();
    boolean hideDitto = request.hideDitto();
    boolean altFormUnlocked = request.altFormUnlocked();
    Integer itemFashionSlot = request.itemFashionSlot();
    int pageIndex = request.pageIndex();
    int pageSize = request.pageSize();
    if (sellerId <= 0 || pageIndex < 0 || pageSize < 1 || pageSize > 100
            || filterType == null
            || pokemonDexIds == null
            || !GtlFilterConditionBuilder.isValidGtlPriceRange(minPrice, maxPrice)
            || !GtlFilterConditionBuilder.isValidGtlLevelRange(minLevel, maxLevel)
            || !GtlFilterConditionBuilder.isValidGtlIvLimit(minIvLimitIndex, minIvLimit)
            || !GtlFilterConditionBuilder.isValidGtlIvLimit(maxIvLimitIndex, maxIvLimit)
            || !GtlFilterConditionBuilder.isValidGtlIvLimitRange(minIvLimitIndex, minIvLimit, maxIvLimitIndex, maxIvLimit)
            || !GtlFilterConditionBuilder.isValidGtlIvMinMatchCount(ivMinMatchCount)
             || !GtlFilterConditionBuilder.isValidGtlEvLimit(minEvLimitIndex, minEvLimit)
             || !GtlFilterConditionBuilder.isValidGtlEvLimit(maxEvLimitIndex, maxEvLimit)
             || !GtlFilterConditionBuilder.isValidGtlEvLimitRange(
                     minEvLimitIndex, minEvLimit, maxEvLimitIndex, maxEvLimit)
             || !GtlFilterConditionBuilder.isValidGtlEggYearRange(minEggYearTimestamp, maxEggYearTimestamp)
             || !GtlFilterConditionBuilder.isValidGtlAbilityIndexId(pokemonAbilityIndexId)
              || !GtlFilterConditionBuilder.isValidGtlFashionSlot(itemFashionSlot)
              || pokemonDexIds.stream().anyMatch(dexId -> dexId == null || dexId <= 0)) {
      return GtlListingPage.empty();
    }
    if (!GtlFilterConditionBuilder.isValidGtlMoveIndexId(learnedMoveIndexId)) {
      return GtlListingPage.empty();
    }
    if (!GtlFilterConditionBuilder.isValidGtlHiddenAbilityFilterType(hiddenAbilityFilterType)
            || !GtlFilterConditionBuilder.isValidGtlAlphaFilterType(alphaFilterType)) {
      return GtlListingPage.empty();
    }

    try {
      Condition condition = GtlSchema.GTL_SELLER_ID.eq(sellerId)
              .and(GtlSchema.GTL_STATUS.in(GtlSchema.GTL_STATUS_ACTIVE, GtlSchema.GTL_STATUS_SOLD));
      if (itemFashionSlot != null) {
        condition = condition.and(
                GtlSchema.GTL_LISTING_TYPE.eq((short) GtlListingType.POKEMON.getType())
                        .or(GtlSchema.GTL_LISTING_TYPE.eq((short) GtlListingType.ITEM.getType())
                                .and(GtlFilterConditionBuilder.getGtlFashionItemCondition(itemFashionSlot))));
      }
      if (!pokemonDexIds.isEmpty()) {
        condition = condition.and(
                GtlSchema.GTL_LISTING_TYPE.eq((short) GtlListingType.ITEM.getType())
                        .or(POKEMON.DEX_ID.in(pokemonDexIds)));
      }
      if (genderFilterType != null) {
        condition = condition.and(
                GtlSchema.GTL_LISTING_TYPE.eq((short) GtlListingType.ITEM.getType())
                        .or(GtlFilterConditionBuilder.getPokemonGenderCondition(genderFilterType)));
      }
      condition = GtlFilterConditionBuilder.applyGtlPriceCondition(condition, minPrice, maxPrice);
      if (minEggYearTimestamp != null || maxEggYearTimestamp != null) {
        condition = condition.and(
                GtlSchema.GTL_LISTING_TYPE.eq((short) GtlListingType.ITEM.getType())
                        .or(GtlFilterConditionBuilder.getPokemonEggYearCondition(
                                minEggYearTimestamp, maxEggYearTimestamp)));
      }
      if (minLevel != null) {
        condition = condition.and(
                GtlSchema.GTL_LISTING_TYPE.eq((short) GtlListingType.ITEM.getType())
                        .or(POKEMON.LEVEL_VALUE.ge((short) minLevel.intValue())));
      }
      if (maxLevel != null) {
        condition = condition.and(
                GtlSchema.GTL_LISTING_TYPE.eq((short) GtlListingType.ITEM.getType())
                        .or(POKEMON.LEVEL_VALUE.le((short) maxLevel.intValue())));
      }
      condition = condition.and(
              GtlSchema.GTL_LISTING_TYPE.eq((short) GtlListingType.ITEM.getType())
                      .or(GtlFilterConditionBuilder.getPokemonIvCondition(
                              minIvLimitIndex, minIvLimit, maxIvLimitIndex, maxIvLimit)));
      condition = condition.and(
              GtlSchema.GTL_LISTING_TYPE.eq((short) GtlListingType.ITEM.getType())
                      .or(GtlFilterConditionBuilder.getPokemonEvCondition(
                              minEvLimitIndex, minEvLimit, maxEvLimitIndex, maxEvLimit)));
      if (ivMinMatchCount != null) {
        condition = condition.and(
                GtlSchema.GTL_LISTING_TYPE.eq((short) GtlListingType.ITEM.getType())
                        .or(GtlFilterConditionBuilder.getPokemonIvMinMatchCountCondition(ivMinMatchCount)));
      }
      if (hideFemaleOnlySpecies) {
        condition = condition.and(
                GtlSchema.GTL_LISTING_TYPE.eq((short) GtlListingType.ITEM.getType())
                        .or(GtlFilterConditionBuilder.getPokemonExcludeFemaleOnlySpeciesCondition()));
      }
      if (hideDitto) {
        condition = condition.and(
                GtlSchema.GTL_LISTING_TYPE.eq((short) GtlListingType.ITEM.getType())
                        .or(GtlFilterConditionBuilder.getPokemonExcludeDittoCondition()));
      }
      if (altFormUnlocked) {
        // Item listings do not carry Pokemon form state and remain searchable.
        condition = condition.and(
                GtlSchema.GTL_LISTING_TYPE.eq((short) GtlListingType.ITEM.getType())
                        .or(DSL.falseCondition()));
      }
      if (eggGroupFilterType != null) {
        condition = condition.and(
                GtlSchema.GTL_LISTING_TYPE.eq((short) GtlListingType.ITEM.getType())
                        .or(GtlFilterConditionBuilder.getPokemonEggGroupCondition(eggGroupFilterType)));
      }
      if (pokemonNatureType != null) {
        condition = condition.and(
                GtlSchema.GTL_LISTING_TYPE.eq((short) GtlListingType.ITEM.getType())
                        .or(GtlFilterConditionBuilder.getPokemonNatureCondition(pokemonNatureType)));
      }
      if (shinyFilterType != null) {
        condition = condition.and(
                GtlSchema.GTL_LISTING_TYPE.eq((short) GtlListingType.ITEM.getType())
                        .or(GtlFilterConditionBuilder.getPokemonShinyCondition(shinyFilterType)));
      }
      if (particleFilterType != null) {
        condition = condition.and(
                GtlSchema.GTL_LISTING_TYPE.eq((short) GtlListingType.ITEM.getType())
                        .or(GtlFilterConditionBuilder.getPokemonParticleCondition(particleFilterType)));
      }
      if (learnedMoveIndexId != null) {
        condition = condition.and(
                GtlSchema.GTL_LISTING_TYPE.eq((short) GtlListingType.ITEM.getType())
                        .or(GtlFilterConditionBuilder.getPokemonMoveCondition(learnedMoveIndexId)));
      }
      if (hiddenAbilityFilterType != null) {
        condition = condition.and(
                GtlSchema.GTL_LISTING_TYPE.eq((short) GtlListingType.ITEM.getType())
                        .or(GtlFilterConditionBuilder.getPokemonHiddenAbilityCondition(hiddenAbilityFilterType)));
      }
      if (alphaFilterType != null) {
        condition = condition.and(
                GtlSchema.GTL_LISTING_TYPE.eq((short) GtlListingType.ITEM.getType())
                        .or(GtlFilterConditionBuilder.getPokemonAlphaCondition(alphaFilterType)));
      }
      if (pokemonAbilityIndexId != null) {
        condition = condition.and(
                GtlSchema.GTL_LISTING_TYPE.eq((short) GtlListingType.ITEM.getType())
                        .or(GtlFilterConditionBuilder.getPokemonAbilityCondition(pokemonAbilityIndexId)));
      }

      var pokemonJoinCondition = POKEMON.ID.eq(GtlSchema.GTL_OBJECT_ID)
              .and(GtlSchema.GTL_LISTING_TYPE.eq((short) GtlListingType.POKEMON.getType()));
      var itemJoinCondition = OWNED_ITEM.ITEM_ID.eq(GtlSchema.GTL_OBJECT_ID)
              .and(GtlSchema.GTL_LISTING_TYPE.eq((short) GtlListingType.ITEM.getType()));

      Integer totalListings = database.ctx()
              .selectCount()
              .from(GtlSchema.GTL_LISTING)
              .leftJoin(POKEMON)
              .on(pokemonJoinCondition)
              .leftJoin(OWNED_ITEM)
              .on(itemJoinCondition)
              .where(condition)
              .fetchOne(0, Integer.class);
      if (totalListings == null || totalListings == 0) {
        return GtlListingPage.empty();
      }

      Result<Record> records = database.ctx()
              .select()
              .from(GtlSchema.GTL_LISTING)
              .leftJoin(POKEMON)
              .on(pokemonJoinCondition)
              .leftJoin(OWNED_ITEM)
              .on(itemJoinCondition)
              .where(condition)
              .orderBy(GtlFilterConditionBuilder.getGtlSortFields(filterType))
              .limit(pageSize)
              .offset(pageIndex * pageSize)
              .fetch();

      List<GtlListingEntry> listings = records.stream()
              .map(GtlListingMapper::mapGtlListing)
              .filter(Objects::nonNull)
              .toList();
      return new GtlListingPage(totalListings, listings);
    } catch (RuntimeException exception) {
      log.error("Failed to load own GTL listings: sellerId={}, pokemonDexIds={}, filterType={}, genderFilterType={}, minPrice={}, maxPrice={}, minLevel={}, maxLevel={}, minEggYearTimestamp={}, maxEggYearTimestamp={}, minIvLimitIndex={}, minIvLimit={}, maxIvLimitIndex={}, maxIvLimit={}, minEvLimitIndex={}, minEvLimit={}, maxEvLimitIndex={}, maxEvLimit={}, ivMinMatchCount={}, hideFemaleOnlySpecies={}, hideDitto={}, altFormUnlocked={}, itemFashionSlot={}, eggGroupFilterType={}, pokemonNatureType={}, shinyFilterType={}, particleFilterType={}, learnedMoveIndexId={}, pokemonAbilityIndexId={}, hiddenAbilityFilterType={}, alphaFilterType={}, pageIndex={}",
              sellerId, pokemonDexIds, filterType, genderFilterType, minPrice, maxPrice,
              minLevel, maxLevel, minEggYearTimestamp, maxEggYearTimestamp,
              minIvLimitIndex, minIvLimit, maxIvLimitIndex, maxIvLimit,
              minEvLimitIndex, minEvLimit, maxEvLimitIndex, maxEvLimit,
              ivMinMatchCount, hideFemaleOnlySpecies, hideDitto, altFormUnlocked,
              itemFashionSlot,
              eggGroupFilterType, pokemonNatureType, shinyFilterType, particleFilterType,
              learnedMoveIndexId, pokemonAbilityIndexId, hiddenAbilityFilterType, alphaFilterType,
              pageIndex, exception);
      return GtlListingPage.empty();
    }
  }
}
