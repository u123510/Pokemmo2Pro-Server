package org.pokemmo.gameserver.services.gtl;

import org.jooq.Record;
import org.pokemmo.gameserver.game.gtl.GtlItemListing;
import org.pokemmo.gameserver.game.gtl.GtlListingEntry;
import org.pokemmo.gameserver.game.gtl.GtlListingType;
import org.pokemmo.gameserver.game.gtl.GtlPokemonListing;
import org.pokemmo.gameserver.game.pokemon.PokemonData;

import java.sql.Timestamp;
import java.time.ZoneOffset;

import static org.pokemmo.db.jooq.Tables.OWNED_ITEM;
import static org.pokemmo.db.jooq.Tables.POKEMON;

final class GtlListingMapper {
    private GtlListingMapper() {
    }

    static GtlListingEntry mapGtlListing(Record record) {
      Short listingType = record.get(GtlSchema.GTL_LISTING_TYPE);
      if (listingType == null) {
        return null;
      }

      if (listingType == (short) GtlListingType.ITEM.getType()) {
        Short itemIndexId = record.get(OWNED_ITEM.ITEM_INDEX_ID);
        if (itemIndexId == null) {
          return null;
        }
        return new GtlItemListing(
                record.get(GtlSchema.GTL_LISTING_ID),
                record.get(GtlSchema.GTL_UNIT_PRICE),
                toEpochSeconds(record.get(GtlSchema.GTL_CREATED_AT)),
                toEpochSeconds(record.get(GtlSchema.GTL_EXPIRES_AT)),
                record.get(GtlSchema.GTL_AMOUNT),
                record.get(GtlSchema.GTL_STATUS).byteValue(),
                record.get(GtlSchema.GTL_SOLD_AMOUNT),
                itemIndexId,
                toClientItemColor(record.get(OWNED_ITEM.COLOR_ID)));
      }

      if (listingType == (short) GtlListingType.POKEMON.getType()
              && record.get(POKEMON.ID) != null) {
        return new GtlPokemonListing(
                record.get(GtlSchema.GTL_LISTING_ID),
                record.get(GtlSchema.GTL_UNIT_PRICE),
                toEpochSeconds(record.get(GtlSchema.GTL_CREATED_AT)),
                toEpochSeconds(record.get(GtlSchema.GTL_EXPIRES_AT)),
                record.get(GtlSchema.GTL_AMOUNT),
                record.get(GtlSchema.GTL_STATUS).byteValue(),
                record.get(GtlSchema.GTL_SOLD_AMOUNT),
                new PokemonData.Builder().setByRecord(record.into(POKEMON)).build());
      }
      return null;
    }

    static byte toClientItemColor(Short colorId) {
      if (colorId == null || colorId < 0) {
        return 0;
      }
      return (byte) Math.min(0xFF, colorId);
    }

    static int toEpochSeconds(Timestamp time) {
      return Math.toIntExact(time.toLocalDateTime().toEpochSecond(ZoneOffset.UTC));
    }
}


