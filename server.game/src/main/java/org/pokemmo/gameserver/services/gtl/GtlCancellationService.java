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
public final class GtlCancellationService {
  private final Database database;

  @Inject
  public GtlCancellationService(Database database) {
    this.database = database;
  }

  public GtlListingCancelResult cancelPokemonGtlListing(long sellerId, long listingId) {
    if (sellerId <= 0 || listingId <= 0) {
      return rejectGtlListingCancellation(sellerId, listingId, "invalid request arguments");
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
          return rejectGtlListingCancellation(sellerId, listingId, "listing does not exist");
        }

        Long listingSellerId = listing.get(GtlSchema.GTL_SELLER_ID);
        Short listingType = listing.get(GtlSchema.GTL_LISTING_TYPE);
        Long pokemonId = listing.get(GtlSchema.GTL_OBJECT_ID);
        Short status = listing.get(GtlSchema.GTL_STATUS);
        Short soldAmount = listing.get(GtlSchema.GTL_SOLD_AMOUNT);
        Integer originalContainerId = listing.get(GtlSchema.GTL_ORIGINAL_CONTAINER_ID);
        Short originalContainerPosition = listing.get(GtlSchema.GTL_ORIGINAL_CONTAINER_POSITION);
        if (listingSellerId == null || listingSellerId != sellerId) {
          return rejectGtlListingCancellation(sellerId, listingId,
                  "listing is owned by another character: sellerId=" + listingSellerId);
        }
        if (listingType == null
                || listingType != (short) GtlListingType.POKEMON.getType()) {
          return rejectGtlListingCancellation(sellerId, listingId,
                  "listing type is not Pokemon: " + listingType);
        }
        if (pokemonId == null) {
          return rejectGtlListingCancellation(sellerId, listingId, "listing objectId is null");
        }
        if (status == null || status != GtlSchema.GTL_STATUS_ACTIVE) {
          return rejectGtlListingCancellation(sellerId, listingId,
                  "listing is not active: status=" + status);
        }
        if (soldAmount == null || soldAmount != 0) {
          return rejectGtlListingCancellation(sellerId, listingId,
                  "listing has already been sold: soldAmount=" + soldAmount);
        }

        CharacterRecord seller = transaction
                .selectFrom(CHARACTER)
                .where(CHARACTER.ID.eq(sellerId))
                .forUpdate()
                .fetchOne();
        if (seller == null) {
          return rejectGtlListingCancellation(sellerId, listingId,
                  "seller character does not exist");
        }

        PokemonRecord pokemon = transaction
                .selectFrom(POKEMON)
                .where(POKEMON.ID.eq(pokemonId))
                .and(POKEMON.TRAINER_ID.eq(sellerId))
                .and(POKEMON.CONTAINER_ID.eq(GtlSchema.AUCTION_CONTAINER_ID))
                .forUpdate()
                .fetchOne();
        if (pokemon == null) {
          return rejectGtlListingCancellation(sellerId, listingId,
                  "listed Pokemon is not owned by seller in auction container: pokemonId="
                          + pokemonId + ", auctionContainerId=" + GtlSchema.AUCTION_CONTAINER_ID);
        }

        int pcCapacity = PokemonContainerType.PC.getSize()
                + Math.max(0, seller.getPcBoxExpansionNumber() == null
                ? 0 : seller.getPcBoxExpansionNumber()) * 60;
        short targetPosition = -1;
        if (originalContainerId != null
                && originalContainerId == GtlSchema.PC_CONTAINER_ID
                && originalContainerPosition != null
                && GtlPokemonSlotAllocator.isValidPokemonPosition(GtlSchema.PC_CONTAINER_ID, originalContainerPosition, pcCapacity)) {
          boolean originalSlotOccupied = transaction.fetchExists(
                  transaction.selectOne()
                          .from(POKEMON)
                          .where(POKEMON.TRAINER_ID.eq(sellerId))
                          .and(POKEMON.CONTAINER_ID.eq(GtlSchema.PC_CONTAINER_ID))
                          .and(POKEMON.CONTAINER_POSITION.eq(originalContainerPosition)));
          if (!originalSlotOccupied) {
            targetPosition = originalContainerPosition;
          }
        }
        if (targetPosition < 0) {
          targetPosition = GtlPokemonSlotAllocator.findNextFreePcBoxPosition(
                  transaction, sellerId, seller.getPcBoxExpansionNumber());
        }
        if (targetPosition < 0) {
          return rejectGtlListingCancellation(sellerId, listingId, "seller PC is full");
        }

        int updatedPokemon = transaction
                .update(POKEMON)
                .set(POKEMON.CONTAINER_ID, GtlSchema.PC_CONTAINER_ID)
                .set(POKEMON.CONTAINER_POSITION, targetPosition)
                .where(POKEMON.ID.eq(pokemonId))
                .and(POKEMON.TRAINER_ID.eq(sellerId))
                .and(POKEMON.CONTAINER_ID.eq(GtlSchema.AUCTION_CONTAINER_ID))
                .execute();
        int updatedListing = transaction
                .update(GtlSchema.GTL_LISTING)
                .set(GtlSchema.GTL_STATUS, GtlSchema.GTL_STATUS_CANCELLED)
                .where(GtlSchema.GTL_LISTING_ID.eq(listingId))
                .and(GtlSchema.GTL_SELLER_ID.eq(sellerId))
                .and(GtlSchema.GTL_STATUS.eq(GtlSchema.GTL_STATUS_ACTIVE))
                .execute();
        if (updatedPokemon != 1 || updatedListing != 1) {
          throw new IllegalStateException("Failed to persist GTL cancellation for listing " + listingId);
        }

        pokemon.setContainerId(GtlSchema.PC_CONTAINER_ID);
        pokemon.setContainerPosition(targetPosition);
        return new GtlListingCancelResult(
                GtlActionResult.SUCCESS,
                new PokemonData.Builder().setByRecord(pokemon).build());
      });
    } catch (RuntimeException exception) {
      log.error("Failed to cancel Pokemon GTL listing: sellerId={}, listingId={}",
              sellerId, listingId, exception);
      return GtlListingCancelResult.rejected();
    }
  }

  private GtlListingCancelResult rejectGtlListingCancellation(
          long sellerId, long listingId, String reason) {
    log.warn("Rejecting Pokemon GTL cancellation: sellerId={}, listingId={}, reason={}",
            sellerId, listingId, reason);
    return GtlListingCancelResult.rejected();
  }
}




