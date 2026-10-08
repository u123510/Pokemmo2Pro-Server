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
final class GtlPokemonListingQuery {
  private final Database database;

  GtlPokemonListingQuery(Database database) {
    this.database = database;
  }

  GtlListingPage search(GtlSearchRequest request) {
    Long sellerId = request.sellerId();
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
    int pageIndex = request.pageIndex();
    int pageSize = request.pageSize();
    if (pageIndex < 0 || pageSize < 1 || pageSize > 100
            || (sellerId != null && sellerId <= 0)
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
      Condition condition = GtlSchema.GTL_LISTING_TYPE.eq((short) GtlListingType.POKEMON.getType());
      if (sellerId != null) {
        condition = condition
                .and(GtlSchema.GTL_SELLER_ID.eq(sellerId))
                .and(GtlSchema.GTL_STATUS.in(GtlSchema.GTL_STATUS_ACTIVE, GtlSchema.GTL_STATUS_SOLD));
      } else {
        condition = condition.and(GtlSchema.GTL_STATUS.eq(GtlSchema.GTL_STATUS_ACTIVE));
      }
      if (!pokemonDexIds.isEmpty()) {
        condition = condition.and(POKEMON.DEX_ID.in(pokemonDexIds));
      }
      if (genderFilterType != null) {
        condition = condition.and(GtlFilterConditionBuilder.getPokemonGenderCondition(genderFilterType));
      }
      condition = GtlFilterConditionBuilder.applyGtlPriceCondition(condition, minPrice, maxPrice);
      condition = GtlFilterConditionBuilder.applyGtlLevelCondition(condition, minLevel, maxLevel);
      condition = GtlFilterConditionBuilder.applyGtlEggYearCondition(
              condition, minEggYearTimestamp, maxEggYearTimestamp);
      condition = GtlFilterConditionBuilder.applyGtlIvCondition(
              condition, minIvLimitIndex, minIvLimit, maxIvLimitIndex, maxIvLimit);
      condition = GtlFilterConditionBuilder.applyGtlEvCondition(
              condition, minEvLimitIndex, minEvLimit, maxEvLimitIndex, maxEvLimit);
      if (ivMinMatchCount != null) {
        condition = condition.and(GtlFilterConditionBuilder.getPokemonIvMinMatchCountCondition(ivMinMatchCount));
      }
      if (hideFemaleOnlySpecies) {
        condition = condition.and(GtlFilterConditionBuilder.getPokemonExcludeFemaleOnlySpeciesCondition());
      }
      if (hideDitto) {
        condition = condition.and(GtlFilterConditionBuilder.getPokemonExcludeDittoCondition());
      }
      if (altFormUnlocked) {
        // The schema has no field for the client's form-unlock rarity bit.
        condition = condition.and(DSL.falseCondition());
      }
      if (eggGroupFilterType != null) {
        condition = condition.and(GtlFilterConditionBuilder.getPokemonEggGroupCondition(eggGroupFilterType));
      }
      if (pokemonNatureType != null) {
        condition = condition.and(GtlFilterConditionBuilder.getPokemonNatureCondition(pokemonNatureType));
      }
      if (shinyFilterType != null) {
        condition = condition.and(GtlFilterConditionBuilder.getPokemonShinyCondition(shinyFilterType));
      }
      if (particleFilterType != null) {
        condition = condition.and(GtlFilterConditionBuilder.getPokemonParticleCondition(particleFilterType));
      }
      if (learnedMoveIndexId != null) {
        condition = condition.and(GtlFilterConditionBuilder.getPokemonMoveCondition(learnedMoveIndexId));
      }
      if (hiddenAbilityFilterType != null) {
        condition = condition.and(GtlFilterConditionBuilder.getPokemonHiddenAbilityCondition(hiddenAbilityFilterType));
      }
      if (alphaFilterType != null) {
        condition = condition.and(GtlFilterConditionBuilder.getPokemonAlphaCondition(alphaFilterType));
      }
      if (pokemonAbilityIndexId != null) {
        condition = condition.and(GtlFilterConditionBuilder.getPokemonAbilityCondition(pokemonAbilityIndexId));
      }

      Integer totalListings = database.ctx()
              .selectCount()
              .from(GtlSchema.GTL_LISTING)
              .join(POKEMON)
              .on(POKEMON.ID.eq(GtlSchema.GTL_OBJECT_ID))
              .where(condition)
              .fetchOne(0, Integer.class);
      if (totalListings == null || totalListings == 0) {
        return GtlListingPage.empty();
      }

      Result<Record> records = database.ctx()
              .select()
              .from(GtlSchema.GTL_LISTING)
              .join(POKEMON)
              .on(POKEMON.ID.eq(GtlSchema.GTL_OBJECT_ID))
              .where(condition)
              .orderBy(GtlFilterConditionBuilder.getGtlSortFields(filterType))
              .limit(pageSize)
              .offset(pageIndex * pageSize)
              .fetch();

      List<GtlListingEntry> listings = records.stream()
              .map(record -> (GtlListingEntry) new GtlPokemonListing(
                      record.get(GtlSchema.GTL_LISTING_ID),
                      record.get(GtlSchema.GTL_UNIT_PRICE),
                      GtlListingMapper.toEpochSeconds(record.get(GtlSchema.GTL_CREATED_AT)),
                      GtlListingMapper.toEpochSeconds(record.get(GtlSchema.GTL_EXPIRES_AT)),
                      record.get(GtlSchema.GTL_AMOUNT),
                      record.get(GtlSchema.GTL_STATUS).byteValue(),
                      record.get(GtlSchema.GTL_SOLD_AMOUNT),
                      new PokemonData.Builder().setByRecord(record.into(POKEMON)).build()))
              .toList();
      return new GtlListingPage(totalListings, listings);
    } catch (RuntimeException exception) {
      log.error("Failed to load Pokemon GTL listings: sellerId={}, pokemonDexIds={}, filterType={}, genderFilterType={}, minPrice={}, maxPrice={}, minLevel={}, maxLevel={}, minEggYearTimestamp={}, maxEggYearTimestamp={}, minIvLimitIndex={}, minIvLimit={}, maxIvLimitIndex={}, maxIvLimit={}, minEvLimitIndex={}, minEvLimit={}, maxEvLimitIndex={}, maxEvLimit={}, ivMinMatchCount={}, hideFemaleOnlySpecies={}, hideDitto={}, altFormUnlocked={}, eggGroupFilterType={}, pokemonNatureType={}, shinyFilterType={}, particleFilterType={}, learnedMoveIndexId={}, pokemonAbilityIndexId={}, hiddenAbilityFilterType={}, alphaFilterType={}, pageIndex={}",
              sellerId, pokemonDexIds, filterType, genderFilterType, minPrice, maxPrice,
              minLevel, maxLevel, minEggYearTimestamp, maxEggYearTimestamp,
              minIvLimitIndex, minIvLimit, maxIvLimitIndex, maxIvLimit,
              minEvLimitIndex, minEvLimit, maxEvLimitIndex, maxEvLimit,
              ivMinMatchCount, hideFemaleOnlySpecies, hideDitto, altFormUnlocked,
              eggGroupFilterType, pokemonNatureType, shinyFilterType, particleFilterType,
              learnedMoveIndexId, pokemonAbilityIndexId, hiddenAbilityFilterType, alphaFilterType,
              pageIndex, exception);
      return GtlListingPage.empty();
    }
  }
}
