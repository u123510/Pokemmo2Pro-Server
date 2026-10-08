package org.pokemmo.gameserver.services.gtl;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.jooq.Record;
import org.jooq.Record5;
import org.jooq.Result;
import org.pokemmo.db.Database;
import org.pokemmo.gameserver.game.gtl.GtlPurchaseHistoryEntry;

import java.sql.Timestamp;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public final class GtlPurchaseHistoryQuery {
    private final Database database;

    @Inject
    public GtlPurchaseHistoryQuery(Database database) {
        this.database = database;
    }

    public List<GtlPurchaseHistoryEntry> getPurchaseHistory(long buyerId) {
        if (buyerId <= 0) {
            return List.of();
        }

        try {
            Result<Record5<Short, Short, Integer, Integer, Timestamp>> rows = database.ctx()
                    .select(
                            GtlSchema.GTL_HISTORY_ITEM_INDEX_ID,
                            GtlSchema.GTL_HISTORY_POKEMON_DEX_ID,
                            GtlSchema.GTL_HISTORY_AMOUNT,
                            GtlSchema.GTL_HISTORY_TOTAL_PRICE,
                            GtlSchema.GTL_HISTORY_TRADED_AT)
                    .from(GtlSchema.GTL_TRADE_HISTORY)
                    .where(GtlSchema.GTL_HISTORY_BUYER_ID.eq(buyerId))
                    .orderBy(GtlSchema.GTL_HISTORY_TRADED_AT.desc(), GtlSchema.GTL_HISTORY_ID.desc())
                    .limit(GtlSchema.GTL_MAX_HISTORY_ENTRIES)
                    .fetch();
            List<GtlPurchaseHistoryEntry> entries = new ArrayList<>(rows.size());
            for (Record row : rows) {
                Short itemIndexId = row.get(GtlSchema.GTL_HISTORY_ITEM_INDEX_ID);
                Short pokemonDexId = row.get(GtlSchema.GTL_HISTORY_POKEMON_DEX_ID);
                Integer amount = row.get(GtlSchema.GTL_HISTORY_AMOUNT);
                Integer totalPrice = row.get(GtlSchema.GTL_HISTORY_TOTAL_PRICE);
                Timestamp tradedAt = row.get(GtlSchema.GTL_HISTORY_TRADED_AT);
                if (itemIndexId == null || pokemonDexId == null || amount == null
                        || totalPrice == null || tradedAt == null || amount <= 0
                        || totalPrice <= 0) {
                    log.warn("Ignoring malformed GTL purchase history row for buyerId={}", buyerId);
                    continue;
                }
                entries.add(new GtlPurchaseHistoryEntry(
                        tradedAt.toLocalDateTime().toInstant(ZoneOffset.UTC).toEpochMilli(),
                        itemIndexId, pokemonDexId, amount, totalPrice));
            }
            return entries;
        } catch (RuntimeException exception) {
            log.error("Failed to load GTL purchase history: buyerId={}", buyerId, exception);
            return List.of();
        }
    }
}
