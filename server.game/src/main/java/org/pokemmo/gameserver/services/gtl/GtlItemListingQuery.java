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
final class GtlItemListingQuery {
  private final Database database;

  GtlItemListingQuery(Database database) {
    this.database = database;
  }

  GtlListingPage search(GtlSearchRequest request) {
    Long sellerId = request.sellerId();
    List<Short> itemIndexIds = request.itemIndexIds();
    GtlFilterType filterType = request.filterType();
    Integer minPrice = request.minPrice();
    Integer maxPrice = request.maxPrice();
    Integer itemFashionSlot = request.itemFashionSlot();
    int pageIndex = request.pageIndex();
    int pageSize = request.pageSize();
    if (pageIndex < 0 || pageSize < 1 || pageSize > 100
            || (sellerId != null && sellerId <= 0)
            || filterType == null
            || itemIndexIds == null
            || !GtlFilterConditionBuilder.isValidGtlPriceRange(minPrice, maxPrice)
            || !GtlFilterConditionBuilder.isValidGtlFashionSlot(itemFashionSlot)
            || itemIndexIds.stream().anyMatch(itemIndexId ->
                    itemIndexId == null || itemIndexId <= 0)) {
      return GtlListingPage.empty();
    }

    try {
      Condition condition = GtlSchema.GTL_LISTING_TYPE.eq((short) GtlListingType.ITEM.getType());
      if (sellerId != null) {
        condition = condition
                .and(GtlSchema.GTL_SELLER_ID.eq(sellerId))
                .and(GtlSchema.GTL_STATUS.in(GtlSchema.GTL_STATUS_ACTIVE, GtlSchema.GTL_STATUS_SOLD));
      } else {
        condition = condition.and(GtlSchema.GTL_STATUS.eq(GtlSchema.GTL_STATUS_ACTIVE));
      }
      if (!itemIndexIds.isEmpty()) {
        condition = condition.and(OWNED_ITEM.ITEM_INDEX_ID.in(itemIndexIds));
      }
      if (itemFashionSlot != null) {
        condition = condition.and(GtlFilterConditionBuilder.getGtlFashionItemCondition(itemFashionSlot));
      }
      condition = GtlFilterConditionBuilder.applyGtlPriceCondition(condition, minPrice, maxPrice);

      Integer totalListings = database.ctx()
              .selectCount()
              .from(GtlSchema.GTL_LISTING)
              .join(OWNED_ITEM)
              .on(OWNED_ITEM.ITEM_ID.eq(GtlSchema.GTL_OBJECT_ID))
              .where(condition)
              .fetchOne(0, Integer.class);
      if (totalListings == null || totalListings == 0) {
        return GtlListingPage.empty();
      }

      Result<Record> records = database.ctx()
              .select()
              .from(GtlSchema.GTL_LISTING)
              .join(OWNED_ITEM)
              .on(OWNED_ITEM.ITEM_ID.eq(GtlSchema.GTL_OBJECT_ID))
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
      log.error("Failed to load ITEM GTL listings: sellerId={}, itemIndexIds={}, filterType={}, minPrice={}, maxPrice={}, itemFashionSlot={}, pageIndex={}",
              sellerId, itemIndexIds, filterType, minPrice, maxPrice,
              itemFashionSlot, pageIndex, exception);
      return GtlListingPage.empty();
    }
  }
}
