package org.pokemmo.gameserver.services.shop;

import java.util.List;

import org.pokemmo.db.jooq.tables.records.InventoryRecord;
import org.pokemmo.db.jooq.tables.records.OwnedItemRecord;

/** A committed snapshot, a known rollback, or an indeterminate connection/commit failure. */
public record ShopTransactionResult(int status, String message, int money,
                                    InventoryRecord inventory, List<OwnedItemRecord> items) {
    public static final int SUCCESS = 0;
    public static final int REJECTED = 1;
    public static final int UNCERTAIN = 2;

    public ShopTransactionResult {
        items = List.copyOf(items);
    }

    static ShopTransactionResult rejected(String reason) {
        return new ShopTransactionResult(REJECTED, reason, 0, null, List.of());
    }

    static ShopTransactionResult uncertain() {
        return new ShopTransactionResult(UNCERTAIN,
                "交易结果暂无法确认，请重新打开商店核对金钱和背包，不要重复提交", 0, null, List.of());
    }
}
