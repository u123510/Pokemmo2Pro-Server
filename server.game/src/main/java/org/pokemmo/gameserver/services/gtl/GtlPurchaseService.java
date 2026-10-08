package org.pokemmo.gameserver.services.gtl;

import com.google.inject.Inject;
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
public final class GtlPurchaseService {
  private final Database database;

  @Inject
  public GtlPurchaseService(Database database) {
    this.database = database;
  }



  public GtlPurchaseResult purchaseGtlListing(
          long buyerId, long listingId, short requestedAmount,
          SnowflakeIdGenerator idGenerator) {
    if (buyerId <= 0 || listingId <= 0 || requestedAmount <= 0) {
      return rejectGtlPurchase(buyerId, listingId, "invalid request arguments");
    }

    try {
      Record listing = database.ctx()
              .select(GtlSchema.GTL_LISTING_TYPE)
              .from(GtlSchema.GTL_LISTING)
              .where(GtlSchema.GTL_LISTING_ID.eq(listingId))
              .fetchOne();
      if (listing == null) {
        return rejectGtlPurchase(buyerId, listingId, "listing does not exist");
      }
      Short listingType = listing.get(GtlSchema.GTL_LISTING_TYPE);
      if (listingType != null
              && listingType == (short) GtlListingType.POKEMON.getType()) {
        return purchasePokemonGtlListing(buyerId, listingId, requestedAmount);
      }
      if (listingType != null
              && listingType == (short) GtlListingType.ITEM.getType()) {
        return purchaseItemGtlListing(buyerId, listingId, requestedAmount, idGenerator);
      }
      return rejectGtlPurchase(buyerId, listingId,
              "unsupported listing type: " + listingType);
    } catch (RuntimeException exception) {
      log.error("Failed to resolve GTL purchase type: buyerId={}, listingId={}, amount={}",
              buyerId, listingId, requestedAmount, exception);
      return GtlPurchaseResult.rejected();
    }
  }

  public GtlPurchaseResult purchasePokemonGtlListing(
          long buyerId, long listingId, short requestedAmount) {
    if (buyerId <= 0 || listingId <= 0 || requestedAmount != 1) {
      return rejectGtlPurchase(buyerId, listingId,
              "invalid request arguments (requestedAmount must be 1)");
    }

    try {
      return database.ctx().transactionResult(configuration -> {
        DSLContext transaction = DSL.using(configuration);
        Record listing = transaction
                .select()
                .from(GtlSchema.GTL_LISTING)
                .where(GtlSchema.GTL_LISTING_ID.eq(listingId))
                .forUpdate()
                .fetchOne();
        if (listing == null) {
          return rejectGtlPurchase(buyerId, listingId, "listing does not exist");
        }

        Long sellerId = listing.get(GtlSchema.GTL_SELLER_ID);
        Short listingType = listing.get(GtlSchema.GTL_LISTING_TYPE);
        Long pokemonId = listing.get(GtlSchema.GTL_OBJECT_ID);
        Integer unitPrice = listing.get(GtlSchema.GTL_UNIT_PRICE);
        Short listingAmount = listing.get(GtlSchema.GTL_AMOUNT);
        Short status = listing.get(GtlSchema.GTL_STATUS);
        Short soldAmount = listing.get(GtlSchema.GTL_SOLD_AMOUNT);
        Timestamp expiresAt = listing.get(GtlSchema.GTL_EXPIRES_AT);
        if (sellerId == null) {
          return rejectGtlPurchase(buyerId, listingId, "listing seller is null");
        }
        if (sellerId == buyerId) {
          return rejectGtlPurchase(buyerId, listingId, "buyer cannot purchase own listing");
        }
        if (listingType == null
                || listingType != (short) GtlListingType.POKEMON.getType()) {
          return rejectGtlPurchase(buyerId, listingId,
                  "listing type is not Pokemon: " + listingType);
        }
        if (pokemonId == null || unitPrice == null || unitPrice <= 0) {
          return rejectGtlPurchase(buyerId, listingId,
                  "invalid listing object or price: objectId=" + pokemonId
                          + ", unitPrice=" + unitPrice);
        }
        if (listingAmount == null || listingAmount != requestedAmount) {
          return rejectGtlPurchase(buyerId, listingId,
                  "listing amount mismatch: listingAmount=" + listingAmount
                          + ", requestedAmount=" + requestedAmount);
        }
        if (status == null || status != GtlSchema.GTL_STATUS_ACTIVE) {
          return rejectGtlPurchase(buyerId, listingId,
                  "listing is not active: status=" + status);
        }
        if (soldAmount == null || soldAmount != 0) {
          return rejectGtlPurchase(buyerId, listingId,
                  "listing has already been sold: soldAmount=" + soldAmount);
        }
        if (expiresAt == null) {
          return rejectGtlPurchase(buyerId, listingId, "listing expiration is null");
        }
        if (!expiresAt.toLocalDateTime().isAfter(LocalDateTime.now(ZoneOffset.UTC))) {
          return rejectGtlPurchase(buyerId, listingId,
                  "listing has expired: expiresAt=" + expiresAt);
        }

        CharacterRecord buyer = transaction
                .selectFrom(CHARACTER)
                .where(CHARACTER.ID.eq(buyerId))
                .forUpdate()
                .fetchOne();
        if (buyer == null) {
          return rejectGtlPurchase(buyerId, listingId, "buyer character does not exist");
        }
        if (buyer.getMoney() == null || buyer.getMoney() < unitPrice) {
          return rejectGtlPurchase(buyerId, listingId,
                  "insufficient money: money=" + buyer.getMoney()
                          + ", unitPrice=" + unitPrice);
        }

        short pcPosition = GtlPokemonSlotAllocator.findNextFreePcBoxPosition(
                transaction, buyerId, buyer.getPcBoxExpansionNumber());
        if (pcPosition < 0) {
          return rejectGtlPurchase(buyerId, listingId, "buyer PC is full");
        }

        PokemonRecord pokemon = transaction
                .selectFrom(POKEMON)
                .where(POKEMON.ID.eq(pokemonId))
                .and(POKEMON.TRAINER_ID.eq(sellerId))
                .and(POKEMON.CONTAINER_ID.eq(GtlSchema.AUCTION_CONTAINER_ID))
                .forUpdate()
                .fetchOne();
        if (pokemon == null) {
          return rejectGtlPurchase(buyerId, listingId,
                  "listed Pokemon is not owned by seller in auction container: sellerId="
                          + sellerId + ", pokemonId=" + pokemonId
                          + ", auctionContainerId=" + GtlSchema.AUCTION_CONTAINER_ID);
        }

        int remainingMoney = buyer.getMoney() - unitPrice;
        int updatedBuyer = transaction
                .update(CHARACTER)
                .set(CHARACTER.MONEY, remainingMoney)
                .where(CHARACTER.ID.eq(buyerId))
                .execute();
        int updatedPokemon = transaction
                .update(POKEMON)
                .set(POKEMON.TRAINER_ID, buyerId)
                .set(POKEMON.CONTAINER_ID, GtlSchema.PC_CONTAINER_ID)
                .set(POKEMON.CONTAINER_POSITION, pcPosition)
                .where(POKEMON.ID.eq(pokemonId))
                .and(POKEMON.TRAINER_ID.eq(sellerId))
                .and(POKEMON.CONTAINER_ID.eq(GtlSchema.AUCTION_CONTAINER_ID))
                .execute();
        int updatedListing = transaction
                .update(GtlSchema.GTL_LISTING)
                .set(GtlSchema.GTL_STATUS, GtlSchema.GTL_STATUS_SOLD)
                .set(GtlSchema.GTL_SOLD_AMOUNT, requestedAmount)
                .where(GtlSchema.GTL_LISTING_ID.eq(listingId))
                .and(GtlSchema.GTL_STATUS.eq(GtlSchema.GTL_STATUS_ACTIVE))
                .execute();
        int insertedHistory = insertGtlPurchaseHistory(
                transaction, listingId, buyerId, sellerId, listingType,
                pokemon.getItem(), pokemon.getDexId(), requestedAmount,
                unitPrice, unitPrice);
        if (updatedBuyer != 1 || updatedPokemon != 1 || updatedListing != 1
                || insertedHistory != 1) {
          throw new IllegalStateException("Failed to persist GTL purchase for listing " + listingId);
        }

        pokemon.setTrainerId(buyerId);
        pokemon.setContainerId(GtlSchema.PC_CONTAINER_ID);
        pokemon.setContainerPosition(pcPosition);
        return new GtlPurchaseResult(
                GtlActionResult.SUCCESS,
                remainingMoney,
                new PokemonData.Builder().setByRecord(pokemon).build(),
                null);
      });
    } catch (RuntimeException exception) {
      log.error("Failed to purchase Pokemon GTL listing: buyerId={}, listingId={}, amount={}",
              buyerId, listingId, requestedAmount, exception);
      return GtlPurchaseResult.rejected();
    }
  }

  public GtlPurchaseResult purchaseItemGtlListing(
          long buyerId, long listingId, short requestedAmount,
          SnowflakeIdGenerator idGenerator) {
    if (buyerId <= 0 || listingId <= 0 || requestedAmount <= 0) {
      return rejectGtlPurchase(buyerId, listingId, "invalid request arguments");
    }

    try {
      return database.ctx().transactionResult(configuration -> {
        DSLContext transaction = DSL.using(configuration);
        Record listing = transaction
                .select()
                .from(GtlSchema.GTL_LISTING)
                .where(GtlSchema.GTL_LISTING_ID.eq(listingId))
                .forUpdate()
                .fetchOne();
        if (listing == null) {
          return rejectGtlPurchase(buyerId, listingId, "listing does not exist");
        }

        Long sellerId = listing.get(GtlSchema.GTL_SELLER_ID);
        Short listingType = listing.get(GtlSchema.GTL_LISTING_TYPE);
        Long itemId = listing.get(GtlSchema.GTL_OBJECT_ID);
        Integer unitPrice = listing.get(GtlSchema.GTL_UNIT_PRICE);
        Short listingAmount = listing.get(GtlSchema.GTL_AMOUNT);
        Short status = listing.get(GtlSchema.GTL_STATUS);
        Short soldAmount = listing.get(GtlSchema.GTL_SOLD_AMOUNT);
        Timestamp expiresAt = listing.get(GtlSchema.GTL_EXPIRES_AT);
        if (sellerId == null) {
          return rejectGtlPurchase(buyerId, listingId, "listing seller is null");
        }
        if (sellerId == buyerId) {
          return rejectGtlPurchase(buyerId, listingId, "buyer cannot purchase own listing");
        }
        if (listingType == null
                || listingType != (short) GtlListingType.ITEM.getType()) {
          return rejectGtlPurchase(buyerId, listingId,
                  "listing type is not Item: " + listingType);
        }
        if (itemId == null || unitPrice == null || unitPrice <= 0) {
          return rejectGtlPurchase(buyerId, listingId,
                  "invalid listing object or price: objectId=" + itemId
                          + ", unitPrice=" + unitPrice);
        }
        if (listingAmount == null || listingAmount <= 0
                || soldAmount == null || soldAmount < 0
                || soldAmount >= listingAmount) {
          return rejectGtlPurchase(buyerId, listingId,
                  "invalid listing amount: amount=" + listingAmount
                          + ", soldAmount=" + soldAmount);
        }
        int remainingListingAmount = listingAmount - soldAmount;
        if (requestedAmount > remainingListingAmount) {
          return rejectGtlPurchase(buyerId, listingId,
                  "requested amount exceeds listing remainder: remaining="
                          + remainingListingAmount + ", requested=" + requestedAmount);
        }
        if (status == null || status != GtlSchema.GTL_STATUS_ACTIVE) {
          return rejectGtlPurchase(buyerId, listingId,
                  "listing is not active: status=" + status);
        }
        if (expiresAt == null) {
          return rejectGtlPurchase(buyerId, listingId, "listing expiration is null");
        }
        if (!expiresAt.toLocalDateTime().isAfter(LocalDateTime.now(ZoneOffset.UTC))) {
          return rejectGtlPurchase(buyerId, listingId,
                  "listing has expired: expiresAt=" + expiresAt);
        }

        CharacterRecord buyer = transaction
                .selectFrom(CHARACTER)
                .where(CHARACTER.ID.eq(buyerId))
                .forUpdate()
                .fetchOne();
        if (buyer == null || buyer.getMoney() == null || buyer.getMoney() < 0) {
          return rejectGtlPurchase(buyerId, listingId,
                  "buyer character does not exist or has invalid money");
        }

        OwnedItemRecord listedItem = transaction
                .selectFrom(OWNED_ITEM)
                .where(OWNED_ITEM.ITEM_ID.eq(itemId))
                .and(OWNED_ITEM.OWNER_ID.eq(sellerId))
                .and(OWNED_ITEM.INVENTORY_ID.eq(GtlSchema.VOID_INVENTORY_ID))
                .forUpdate()
                .fetchOne();
        if (listedItem == null || listedItem.getItemAmount() == null
                || listedItem.getItemAmount() != remainingListingAmount) {
          return rejectGtlPurchase(buyerId, listingId,
                  "listed item is not owned by seller in void inventory or amount mismatches"
                          + ": sellerId=" + sellerId + ", itemId=" + itemId);
        }

        Short itemIndexId = listedItem.getItemIndexId();
        if (itemIndexId == null || itemIndexId <= 0) {
          return rejectGtlPurchase(buyerId, listingId,
                  "listed item has an invalid item index: " + itemIndexId);
        }
        ItemData itemData = ItemManager.getItemData(itemIndexId);
        boolean wearableItem = SkinType.isWearableItemIndex(itemIndexId);
        if (itemData == null) {
          return rejectGtlPurchase(buyerId, listingId,
                  "item metadata is missing: itemIndexId=" + itemIndexId);
        }
        if (itemData.isBindAccount()
                || (!ItemManager.isTradeableForExchange(itemIndexId)
                && !wearableItem)) {
          return rejectGtlPurchase(buyerId, listingId,
                  "item is no longer tradeable: itemIndexId=" + itemIndexId);
        }
        if (requestedAmount < listedItem.getItemAmount() && idGenerator == null) {
          return rejectGtlPurchase(buyerId, listingId,
                  "partial item purchase requires an ID generator");
        }

        long totalPrice;
        try {
          totalPrice = Math.multiplyExact((long) unitPrice, requestedAmount);
        } catch (ArithmeticException exception) {
          return rejectGtlPurchase(buyerId, listingId, "purchase price overflow");
        }
        if (totalPrice > buyer.getMoney()) {
          return rejectGtlPurchase(buyerId, listingId,
                  "insufficient money: money=" + buyer.getMoney()
                          + ", totalPrice=" + totalPrice);
        }
        int remainingMoney = (int) (buyer.getMoney() - totalPrice);
        int updatedBuyer = transaction
                .update(CHARACTER)
                .set(CHARACTER.MONEY, remainingMoney)
                .where(CHARACTER.ID.eq(buyerId))
                .and(CHARACTER.MONEY.eq(buyer.getMoney()))
                .execute();
        if (updatedBuyer != 1) {
          throw new IllegalStateException(
                  "Failed to update buyer money for GTL item purchase " + listingId);
        }

        OwnedItemRecord purchasedItem;
        int updatedItem;
        if (requestedAmount == listedItem.getItemAmount()) {
          updatedItem = transaction
                  .update(OWNED_ITEM)
                  .set(OWNED_ITEM.OWNER_ID, buyerId)
                  .set(OWNED_ITEM.INVENTORY_ID, GtlSchema.MAIN_INVENTORY_ID)
                  .where(OWNED_ITEM.ITEM_ID.eq(itemId))
                  .and(OWNED_ITEM.OWNER_ID.eq(sellerId))
                  .and(OWNED_ITEM.INVENTORY_ID.eq(GtlSchema.VOID_INVENTORY_ID))
                  .execute();
          listedItem.setOwnerId(buyerId);
          listedItem.setInventoryId(GtlSchema.MAIN_INVENTORY_ID);
          purchasedItem = listedItem;
        } else {
          int remainingItemAmount = listedItem.getItemAmount() - requestedAmount;
          updatedItem = transaction
                  .update(OWNED_ITEM)
                  .set(OWNED_ITEM.ITEM_AMOUNT, (short) remainingItemAmount)
                  .where(OWNED_ITEM.ITEM_ID.eq(itemId))
                  .and(OWNED_ITEM.OWNER_ID.eq(sellerId))
                  .and(OWNED_ITEM.INVENTORY_ID.eq(GtlSchema.VOID_INVENTORY_ID))
                  .execute();
          if (updatedItem == 1) {
            purchasedItem = new OwnedItemRecord();
            purchasedItem.setItemId(idGenerator.nextId());
            purchasedItem.setOwnerId(buyerId);
            purchasedItem.setItemIndexId(itemIndexId);
            purchasedItem.setItemAmount(requestedAmount);
            purchasedItem.setInventoryId(GtlSchema.MAIN_INVENTORY_ID);
            purchasedItem.setColorId(listedItem.getColorId());
            purchasedItem.setItemRegionIndexId(listedItem.getItemRegionIndexId());
            purchasedItem.setPvpRewardLevel(listedItem.getPvpRewardLevel());
            purchasedItem.setPvpRewardSeason(listedItem.getPvpRewardSeason());
            purchasedItem.setPvpRewardTime(listedItem.getPvpRewardTime());
            if (transaction.insertInto(OWNED_ITEM).set(purchasedItem).execute() != 1) {
              throw new IllegalStateException(
                      "Failed to insert purchased GTL item for listing " + listingId);
            }
          } else {
            purchasedItem = null;
          }
        }
        if (updatedItem != 1 || purchasedItem == null) {
          throw new IllegalStateException(
                  "Failed to persist GTL item purchase for listing " + listingId);
        }

        short newSoldAmount = (short) (soldAmount + requestedAmount);
        short newStatus = newSoldAmount == listingAmount
                ? GtlSchema.GTL_STATUS_SOLD : GtlSchema.GTL_STATUS_ACTIVE;
        int updatedListing = transaction
                .update(GtlSchema.GTL_LISTING)
                .set(GtlSchema.GTL_STATUS, newStatus)
                .set(GtlSchema.GTL_SOLD_AMOUNT, newSoldAmount)
                .where(GtlSchema.GTL_LISTING_ID.eq(listingId))
                .and(GtlSchema.GTL_STATUS.eq(GtlSchema.GTL_STATUS_ACTIVE))
                .and(GtlSchema.GTL_SOLD_AMOUNT.eq(soldAmount))
                .execute();
        int insertedHistory = insertGtlPurchaseHistory(
                transaction, listingId, buyerId, sellerId, listingType,
                itemIndexId, (short) 0, requestedAmount, unitPrice, (int) totalPrice);
        if (updatedListing != 1 || insertedHistory != 1) {
          throw new IllegalStateException(
                  "Failed to mark GTL item listing as purchased: " + listingId);
        }

        return new GtlPurchaseResult(
                GtlActionResult.SUCCESS, remainingMoney, null, purchasedItem);
      });
    } catch (RuntimeException exception) {
      log.error("Failed to purchase ITEM GTL listing: buyerId={}, listingId={}, amount={}",
              buyerId, listingId, requestedAmount, exception);
      return GtlPurchaseResult.rejected();
    }
  }

  private GtlPurchaseResult rejectGtlPurchase(long buyerId, long listingId, String reason) {
    log.warn("Rejecting GTL purchase: buyerId={}, listingId={}, reason={}",
            buyerId, listingId, reason);
    return GtlPurchaseResult.rejected();
  }

  private int insertGtlPurchaseHistory(
          DSLContext transaction, long listingId, long buyerId, long sellerId,
          short listingType, short itemIndexId, short pokemonDexId, short amount,
          int unitPrice, int totalPrice) {
    return transaction
            .insertInto(GtlSchema.GTL_TRADE_HISTORY)
            .set(GtlSchema.GTL_HISTORY_LISTING_ID, listingId)
            .set(GtlSchema.GTL_HISTORY_BUYER_ID, buyerId)
            .set(GtlSchema.GTL_HISTORY_SELLER_ID, sellerId)
            .set(GtlSchema.GTL_HISTORY_LISTING_TYPE, listingType)
            .set(GtlSchema.GTL_HISTORY_ITEM_INDEX_ID, itemIndexId)
            .set(GtlSchema.GTL_HISTORY_POKEMON_DEX_ID, pokemonDexId)
            .set(GtlSchema.GTL_HISTORY_AMOUNT, (int) amount)
            .set(GtlSchema.GTL_HISTORY_UNIT_PRICE, unitPrice)
            .set(GtlSchema.GTL_HISTORY_TOTAL_PRICE, totalPrice)
            .set(GtlSchema.GTL_HISTORY_TRADED_AT,
                    Timestamp.valueOf(LocalDateTime.now(ZoneOffset.UTC)))
            .execute();
  }
}




