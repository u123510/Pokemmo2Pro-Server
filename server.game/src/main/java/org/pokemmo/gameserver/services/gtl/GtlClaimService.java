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
public final class GtlClaimService {
  private final Database database;

  @Inject
  public GtlClaimService(Database database) {
    this.database = database;
  }

  public GtlClaimResult claimGtlListings(
          long sellerId, List<Long> listingIds) {
    if (sellerId <= 0 || listingIds == null || listingIds.isEmpty()
            || listingIds.size() > GtlSchema.GTL_MAX_CLAIM_LISTINGS
            || listingIds.stream().anyMatch(id -> id == null || id <= 0)
            || new HashSet<>(listingIds).size() != listingIds.size()) {
      return rejectGtlClaim(sellerId, listingIds, "invalid request arguments");
    }

    List<Long> sortedListingIds = listingIds.stream().sorted().toList();
    try {
      return database.ctx().transactionResult(configuration -> {
        DSLContext transaction = DSL.using(configuration);
        List<Record> listings = new ArrayList<>(sortedListingIds.size());
        for (long listingId : sortedListingIds) {
          Record listing = transaction
                  .select()
                  .from(GtlSchema.GTL_LISTING)
                  .where(GtlSchema.GTL_LISTING_ID.eq(listingId))
                  .forUpdate()
                  .fetchOne();
          if (listing == null) {
            return rejectGtlClaim(sellerId, listingIds,
                    "listing does not exist: listingId=" + listingId);
          }
          listings.add(listing);
        }

        CharacterRecord seller = transaction
                .selectFrom(CHARACTER)
                .where(CHARACTER.ID.eq(sellerId))
                .forUpdate()
                .fetchOne();
        if (seller == null || seller.getMoney() == null || seller.getMoney() < 0) {
          return rejectGtlClaim(sellerId, listingIds,
                  "seller character does not exist or has invalid money");
        }

        long totalProceeds = 0;
        for (Record listing : listings) {
          Long listingSellerId = listing.get(GtlSchema.GTL_SELLER_ID);
          Short listingType = listing.get(GtlSchema.GTL_LISTING_TYPE);
          Integer unitPrice = listing.get(GtlSchema.GTL_UNIT_PRICE);
          Short listingAmount = listing.get(GtlSchema.GTL_AMOUNT);
          Short status = listing.get(GtlSchema.GTL_STATUS);
          Short soldAmount = listing.get(GtlSchema.GTL_SOLD_AMOUNT);
          Long listingId = listing.get(GtlSchema.GTL_LISTING_ID);
          if (listingSellerId == null || listingSellerId.longValue() != sellerId) {
            return rejectGtlClaim(sellerId, listingIds,
                    "listing is owned by another character: listingId=" + listingId);
          }
          if (listingType == null
                  || (listingType != (short) GtlListingType.POKEMON.getType()
                  && listingType != (short) GtlListingType.ITEM.getType())) {
            return rejectGtlClaim(sellerId, listingIds,
                    "unsupported listing type: listingId=" + listingId
                            + ", listingType=" + listingType);
          }
          if (unitPrice == null || unitPrice <= 0
                  || listingAmount == null || listingAmount <= 0
                  || soldAmount == null || soldAmount <= 0
                  || soldAmount > listingAmount) {
            return rejectGtlClaim(sellerId, listingIds,
                    "invalid listing amount or price: listingId=" + listingId);
          }
          if (status == null || status != GtlSchema.GTL_STATUS_SOLD) {
            return rejectGtlClaim(sellerId, listingIds,
                    "listing is not awaiting claim: listingId=" + listingId
                            + ", status=" + status);
          }

          try {
            totalProceeds = Math.addExact(
                    totalProceeds, Math.multiplyExact((long) unitPrice, soldAmount));
          } catch (ArithmeticException exception) {
            return rejectGtlClaim(sellerId, listingIds,
                    "listing proceeds overflow: listingId=" + listingId);
          }
        }

        long newMoney;
        try {
          newMoney = Math.addExact(seller.getMoney().longValue(), totalProceeds);
        } catch (ArithmeticException exception) {
          return rejectGtlClaim(sellerId, listingIds, "seller money overflow");
        }
        if (newMoney > Integer.MAX_VALUE) {
          return rejectGtlClaim(sellerId, listingIds,
                  "seller money exceeds protocol limit: money=" + newMoney);
        }

        int updatedCharacter = transaction
                .update(CHARACTER)
                .set(CHARACTER.MONEY, (int) newMoney)
                .where(CHARACTER.ID.eq(sellerId))
                .execute();
        if (updatedCharacter != 1) {
          throw new IllegalStateException(
                  "Failed to update seller money while claiming GTL listings");
        }

        for (Record listing : listings) {
          Long listingId = listing.get(GtlSchema.GTL_LISTING_ID);
          int updatedListing = transaction
                  .update(GtlSchema.GTL_LISTING)
                  .set(GtlSchema.GTL_STATUS, GtlSchema.GTL_STATUS_CLAIMED)
                  .where(GtlSchema.GTL_LISTING_ID.eq(listingId))
                  .and(GtlSchema.GTL_SELLER_ID.eq(sellerId))
                  .and(GtlSchema.GTL_STATUS.eq(GtlSchema.GTL_STATUS_SOLD))
                  .execute();
          if (updatedListing != 1) {
            throw new IllegalStateException(
                    "Failed to mark GTL listing as claimed: listingId=" + listingId);
          }
        }

        return new GtlClaimResult(
                GtlActionResult.SUCCESS,
                (int) newMoney,
                totalProceeds,
                listings.size());
      });
    } catch (RuntimeException exception) {
      log.error("Failed to claim GTL listings: sellerId={}, listingIds={}",
              sellerId, listingIds, exception);
      return GtlClaimResult.rejected();
    }
  }

  private GtlClaimResult rejectGtlClaim(
          long sellerId, List<Long> listingIds, String reason) {
    log.warn("Rejecting GTL claim: sellerId={}, listingIds={}, reason={}",
            sellerId, listingIds, reason);
    return GtlClaimResult.rejected();
  }
}


