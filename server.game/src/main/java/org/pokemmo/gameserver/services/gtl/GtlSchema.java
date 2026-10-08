package org.pokemmo.gameserver.services.gtl;

import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;

import java.sql.Timestamp;

/** Shared GTL table metadata and protocol limits for the GTL domain services. */
final class GtlSchema {
    static final int PC_CONTAINER_ID = 0;
    static final int PARTY_CONTAINER_ID = 1;
    static final int AUCTION_CONTAINER_ID = 6;
    static final short VOID_INVENTORY_ID = 0;
    static final short MAIN_INVENTORY_ID = 1;
    static final int GTL_MINIMUM_LISTING_PRICE = 1_000;
    static final int GTL_MINIMUM_LISTING_FEE = 1_000;
    static final int GTL_MAXIMUM_LISTING_FEE = 25_000;
    static final int GTL_LISTING_DURATION_DAYS = 14;
    static final short GTL_STATUS_ACTIVE = 0;
    static final short GTL_STATUS_SOLD = 1;
    static final short GTL_STATUS_CANCELLED = 2;
    static final short GTL_STATUS_CLAIMED = 3;
    static final int GTL_MAX_CLAIM_LISTINGS = 0xFF;
    static final int GTL_MAX_HISTORY_ENTRIES = 0xFF;
    static final short DITTO_DEX_ID = 132;

    static final Table<Record> GTL_LISTING = DSL.table(DSL.name("gtl_listing"));
    static final Field<Long> GTL_LISTING_ID =
            DSL.field(DSL.name("gtl_listing", "listing_id"), Long.class);
    static final Field<Long> GTL_SELLER_ID =
            DSL.field(DSL.name("gtl_listing", "seller_id"), Long.class);
    static final Field<Short> GTL_LISTING_TYPE =
            DSL.field(DSL.name("gtl_listing", "listing_type"), Short.class);
    static final Field<Long> GTL_OBJECT_ID =
            DSL.field(DSL.name("gtl_listing", "object_id"), Long.class);
    static final Field<Integer> GTL_UNIT_PRICE =
            DSL.field(DSL.name("gtl_listing", "unit_price"), Integer.class);
    static final Field<Short> GTL_AMOUNT =
            DSL.field(DSL.name("gtl_listing", "amount"), Short.class);
    static final Field<Integer> GTL_ORIGINAL_CONTAINER_ID =
            DSL.field(DSL.name("gtl_listing", "original_container_id"), Integer.class);
    static final Field<Short> GTL_ORIGINAL_CONTAINER_POSITION =
            DSL.field(DSL.name("gtl_listing", "original_container_position"), Short.class);
    static final Field<Short> GTL_STATUS =
            DSL.field(DSL.name("gtl_listing", "status"), Short.class);
    static final Field<Short> GTL_SOLD_AMOUNT =
            DSL.field(DSL.name("gtl_listing", "sold_amount"), Short.class);
    static final Field<Timestamp> GTL_CREATED_AT =
            DSL.field(DSL.name("gtl_listing", "created_at"), Timestamp.class);
    static final Field<Timestamp> GTL_EXPIRES_AT =
            DSL.field(DSL.name("gtl_listing", "expires_at"), Timestamp.class);

    static final Table<Record> GTL_TRADE_HISTORY =
            DSL.table(DSL.name("gtl_trade_history"));
    static final Field<Long> GTL_HISTORY_ID =
            DSL.field(DSL.name("gtl_trade_history", "history_id"), Long.class);
    static final Field<Long> GTL_HISTORY_LISTING_ID =
            DSL.field(DSL.name("gtl_trade_history", "listing_id"), Long.class);
    static final Field<Long> GTL_HISTORY_BUYER_ID =
            DSL.field(DSL.name("gtl_trade_history", "buyer_id"), Long.class);
    static final Field<Long> GTL_HISTORY_SELLER_ID =
            DSL.field(DSL.name("gtl_trade_history", "seller_id"), Long.class);
    static final Field<Short> GTL_HISTORY_LISTING_TYPE =
            DSL.field(DSL.name("gtl_trade_history", "listing_type"), Short.class);
    static final Field<Short> GTL_HISTORY_ITEM_INDEX_ID =
            DSL.field(DSL.name("gtl_trade_history", "item_index_id"), Short.class);
    static final Field<Short> GTL_HISTORY_POKEMON_DEX_ID =
            DSL.field(DSL.name("gtl_trade_history", "pokemon_dex_id"), Short.class);
    static final Field<Integer> GTL_HISTORY_AMOUNT =
            DSL.field(DSL.name("gtl_trade_history", "amount"), Integer.class);
    static final Field<Integer> GTL_HISTORY_UNIT_PRICE =
            DSL.field(DSL.name("gtl_trade_history", "unit_price"), Integer.class);
    static final Field<Integer> GTL_HISTORY_TOTAL_PRICE =
            DSL.field(DSL.name("gtl_trade_history", "total_price"), Integer.class);
    static final Field<Timestamp> GTL_HISTORY_TRADED_AT =
            DSL.field(DSL.name("gtl_trade_history", "traded_at"), Timestamp.class);

    private GtlSchema() {
    }

    static int calculateListingFee(long totalPrice) {
        long percentageFee = Math.max(0, totalPrice) * 5 / 100;
        return (int) Math.min(
                GTL_MAXIMUM_LISTING_FEE,
                Math.max(GTL_MINIMUM_LISTING_FEE, percentageFee));
    }
}


