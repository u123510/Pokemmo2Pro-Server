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
public final class GtlListingService {
  private final Database database;

  @Inject
  public GtlListingService(Database database) {
    this.database = database;
  }

public GtlListingCreateResult createPokemonGtlListing(
          long characterId, long pokemonId, int unitPrice, short amount) {
    if (characterId <= 0 || pokemonId <= 0 || unitPrice < GtlSchema.GTL_MINIMUM_LISTING_PRICE || amount != 1) {
      return GtlListingCreateResult.rejected();
    }

    try {
      return database.ctx().transactionResult(configuration -> {
        DSLContext transaction = DSL.using(configuration);
        CharacterRecord character = transaction
                .selectFrom(CHARACTER)
                .where(CHARACTER.ID.eq(characterId))
                .forUpdate()
                .fetchOne();
        if (character == null) {
          return GtlListingCreateResult.rejected();
        }

        PokemonRecord pokemon = transaction
                .selectFrom(POKEMON)
                .where(POKEMON.ID.eq(pokemonId))
                .and(POKEMON.TRAINER_ID.eq(characterId))
                .and(POKEMON.CONTAINER_ID.eq(GtlSchema.PC_CONTAINER_ID))
                .forUpdate()
                .fetchOne();
        if (pokemon == null) {
          return GtlListingCreateResult.rejected();
        }

        boolean alreadyListed = transaction.fetchExists(
                transaction.selectOne()
                        .from(GtlSchema.GTL_LISTING)
                        .where(GtlSchema.GTL_LISTING_TYPE.eq((short) GtlListingType.POKEMON.getType()))
                        .and(GtlSchema.GTL_OBJECT_ID.eq(pokemonId))
                        .and(GtlSchema.GTL_STATUS.eq(GtlSchema.GTL_STATUS_ACTIVE))
        );
        if (alreadyListed) {
          return GtlListingCreateResult.rejected();
        }

        int listingFee = GtlSchema.calculateListingFee(unitPrice);
        int currentMoney = character.getMoney();
        if (currentMoney < listingFee) {
          return GtlListingCreateResult.rejected();
        }

        LocalDateTime createdAt = LocalDateTime.now(ZoneOffset.UTC);
        int inserted = transaction
                .insertInto(GtlSchema.GTL_LISTING)
                .set(GtlSchema.GTL_SELLER_ID, characterId)
                .set(GtlSchema.GTL_LISTING_TYPE, (short) GtlListingType.POKEMON.getType())
                .set(GtlSchema.GTL_OBJECT_ID, pokemonId)
                .set(GtlSchema.GTL_UNIT_PRICE, unitPrice)
                .set(GtlSchema.GTL_AMOUNT, amount)
                .set(GtlSchema.GTL_ORIGINAL_CONTAINER_ID, pokemon.getContainerId())
                .set(GtlSchema.GTL_ORIGINAL_CONTAINER_POSITION, pokemon.getContainerPosition())
                .set(GtlSchema.GTL_STATUS, GtlSchema.GTL_STATUS_ACTIVE)
                .set(GtlSchema.GTL_SOLD_AMOUNT, (short) 0)
                .set(GtlSchema.GTL_CREATED_AT, Timestamp.valueOf(createdAt))
                .set(GtlSchema.GTL_EXPIRES_AT, Timestamp.valueOf(
                        createdAt.plusDays(GtlSchema.GTL_LISTING_DURATION_DAYS)))
                .execute();
        if (inserted != 1) {
          throw new IllegalStateException("Failed to insert GTL listing for Pokemon " + pokemonId);
        }

        int remainingMoney = currentMoney - listingFee;
        int updatedCharacter = transaction
                .update(CHARACTER)
                .set(CHARACTER.MONEY, remainingMoney)
                .where(CHARACTER.ID.eq(characterId))
                .execute();
        int updatedPokemon = transaction
                .update(POKEMON)
                .set(POKEMON.CONTAINER_ID, GtlSchema.AUCTION_CONTAINER_ID)
                .set(POKEMON.CONTAINER_POSITION, (short) 0)
                .where(POKEMON.ID.eq(pokemonId))
                .and(POKEMON.TRAINER_ID.eq(characterId))
                .and(POKEMON.CONTAINER_ID.eq(GtlSchema.PC_CONTAINER_ID))
                .execute();
        if (updatedCharacter != 1 || updatedPokemon != 1) {
          throw new IllegalStateException("Failed to persist GTL listing state for Pokemon " + pokemonId);
        }

        return new GtlListingCreateResult(GtlActionResult.SUCCESS, remainingMoney);
      });
    } catch (RuntimeException exception) {
      log.error("Failed to create Pokemon GTL listing: characterId={}, pokemonId={}, price={}",
              characterId, pokemonId, unitPrice, exception);
      return GtlListingCreateResult.rejected();
    }
  }

  public GtlListingCreateResult createItemGtlListing(
          long characterId,
          long itemId,
          int unitPrice,
          short amount,
          SnowflakeIdGenerator idGenerator) {
    if (characterId <= 0 || itemId <= 0 || unitPrice < GtlSchema.GTL_MINIMUM_LISTING_PRICE
            || amount <= 0 || idGenerator == null) {
      return rejectItemGtlListing(characterId, itemId,
              "invalid request arguments: unitPrice=" + unitPrice + ", amount=" + amount);
    }

    try {
      return database.ctx().transactionResult(configuration -> {
        DSLContext transaction = DSL.using(configuration);
        CharacterRecord character = transaction
                .selectFrom(CHARACTER)
                .where(CHARACTER.ID.eq(characterId))
                .forUpdate()
                .fetchOne();
        if (character == null) {
          return rejectItemGtlListing(characterId, itemId, "character does not exist");
        }

        OwnedItemRecord ownedItem = transaction
                .selectFrom(OWNED_ITEM)
                .where(OWNED_ITEM.ITEM_ID.eq(itemId))
                .and(OWNED_ITEM.OWNER_ID.eq(characterId))
                .and(OWNED_ITEM.INVENTORY_ID.eq(GtlSchema.MAIN_INVENTORY_ID))
                .forUpdate()
                .fetchOne();
        if (ownedItem == null || ownedItem.getItemAmount() == null
                || ownedItem.getItemAmount() < amount) {
          return rejectItemGtlListing(characterId, itemId,
                  "item is not in the main inventory or amount is insufficient");
        }

        ItemData itemData = ItemManager.getItemData(ownedItem.getItemIndexId());
        if (itemData == null) {
          return rejectItemGtlListing(characterId, itemId,
                  "item metadata is missing: itemIndexId="
                          + ownedItem.getItemIndexId());
        }
        if (itemData.isBindAccount()) {
          return rejectItemGtlListing(characterId, itemId,
                  "item is account-bound: itemIndexId=" + ownedItem.getItemIndexId());
        }
        boolean wearableItem = SkinType.isWearableItemIndex(ownedItem.getItemIndexId());
        if (!ItemManager.isTradeableForExchange(ownedItem.getItemIndexId()) && !wearableItem) {
          return rejectItemGtlListing(characterId, itemId,
                  "item is not tradeable according to the client trade rules: itemIndexId="
                          + ownedItem.getItemIndexId());
        }

        boolean alreadyListed = transaction.fetchExists(
                transaction.selectOne()
                        .from(GtlSchema.GTL_LISTING)
                        .where(GtlSchema.GTL_LISTING_TYPE.eq((short) GtlListingType.ITEM.getType()))
                        .and(GtlSchema.GTL_OBJECT_ID.eq(itemId))
                        .and(GtlSchema.GTL_STATUS.eq(GtlSchema.GTL_STATUS_ACTIVE))
        );
        if (alreadyListed) {
          return rejectItemGtlListing(characterId, itemId, "item is already listed");
        }

        int listingFee = GtlSchema.calculateListingFee((long) unitPrice * amount);
        int currentMoney = character.getMoney();
        if (currentMoney < listingFee) {
          return rejectItemGtlListing(characterId, itemId,
                  "insufficient money for listing fee: money=" + currentMoney
                          + ", fee=" + listingFee);
        }

        long listedItemId = itemId;
        int updatedInventoryItem;
        if (ownedItem.getItemAmount() == amount) {
          updatedInventoryItem = transaction
                  .update(OWNED_ITEM)
                  .set(OWNED_ITEM.INVENTORY_ID, GtlSchema.VOID_INVENTORY_ID)
                  .where(OWNED_ITEM.ITEM_ID.eq(itemId))
                  .and(OWNED_ITEM.OWNER_ID.eq(characterId))
                  .and(OWNED_ITEM.INVENTORY_ID.eq(GtlSchema.MAIN_INVENTORY_ID))
                  .execute();
        } else {
          listedItemId = idGenerator.nextId();
          short remainingAmount = (short) (ownedItem.getItemAmount() - amount);
          updatedInventoryItem = transaction
                  .update(OWNED_ITEM)
                  .set(OWNED_ITEM.ITEM_AMOUNT, remainingAmount)
                  .where(OWNED_ITEM.ITEM_ID.eq(itemId))
                  .and(OWNED_ITEM.OWNER_ID.eq(characterId))
                  .and(OWNED_ITEM.INVENTORY_ID.eq(GtlSchema.MAIN_INVENTORY_ID))
                  .execute();

          OwnedItemRecord listedItem = new OwnedItemRecord();
          listedItem.setItemId(listedItemId);
          listedItem.setOwnerId(characterId);
          listedItem.setItemIndexId(ownedItem.getItemIndexId());
          listedItem.setItemAmount(amount);
          listedItem.setInventoryId(GtlSchema.VOID_INVENTORY_ID);
          listedItem.setColorId(ownedItem.getColorId());
          listedItem.setItemRegionIndexId(ownedItem.getItemRegionIndexId());
          listedItem.setPvpRewardLevel(ownedItem.getPvpRewardLevel());
          listedItem.setPvpRewardSeason(ownedItem.getPvpRewardSeason());
          listedItem.setPvpRewardTime(ownedItem.getPvpRewardTime());
          int insertedItem = transaction
                  .insertInto(OWNED_ITEM)
                  .set(listedItem)
                  .execute();
          if (insertedItem != 1) {
            throw new IllegalStateException(
                    "Failed to split inventory item for GTL listing " + itemId);
          }
        }

        LocalDateTime createdAt = LocalDateTime.now(ZoneOffset.UTC);
        int insertedListing = transaction
                .insertInto(GtlSchema.GTL_LISTING)
                .set(GtlSchema.GTL_SELLER_ID, characterId)
                .set(GtlSchema.GTL_LISTING_TYPE, (short) GtlListingType.ITEM.getType())
                .set(GtlSchema.GTL_OBJECT_ID, listedItemId)
                .set(GtlSchema.GTL_UNIT_PRICE, unitPrice)
                .set(GtlSchema.GTL_AMOUNT, amount)
                .set(GtlSchema.GTL_ORIGINAL_CONTAINER_ID, (int) GtlSchema.MAIN_INVENTORY_ID)
                .set(GtlSchema.GTL_ORIGINAL_CONTAINER_POSITION, (short) 0)
                .set(GtlSchema.GTL_STATUS, GtlSchema.GTL_STATUS_ACTIVE)
                .set(GtlSchema.GTL_SOLD_AMOUNT, (short) 0)
                .set(GtlSchema.GTL_CREATED_AT, Timestamp.valueOf(createdAt))
                .set(GtlSchema.GTL_EXPIRES_AT, Timestamp.valueOf(
                        createdAt.plusDays(GtlSchema.GTL_LISTING_DURATION_DAYS)))
                .execute();

        int remainingMoney = currentMoney - listingFee;
        int updatedCharacter = transaction
                .update(CHARACTER)
                .set(CHARACTER.MONEY, remainingMoney)
                .where(CHARACTER.ID.eq(characterId))
                .execute();
        if (updatedInventoryItem != 1 || insertedListing != 1 || updatedCharacter != 1) {
          throw new IllegalStateException(
                  "Failed to persist ITEM GTL listing state for item " + itemId);
        }

        return new GtlListingCreateResult(GtlActionResult.SUCCESS, remainingMoney);
      });
    } catch (RuntimeException exception) {
      log.error("Failed to create ITEM GTL listing: characterId={}, itemId={}, price={}, amount={}",
              characterId, itemId, unitPrice, amount, exception);
      return GtlListingCreateResult.rejected();
    }
  }

  private GtlListingCreateResult rejectItemGtlListing(
          long characterId, long itemId, String reason) {
    log.warn("Rejecting ITEM GTL listing: characterId={}, itemId={}, reason={}",
            characterId, itemId, reason);
    return GtlListingCreateResult.rejected();
  }
}


