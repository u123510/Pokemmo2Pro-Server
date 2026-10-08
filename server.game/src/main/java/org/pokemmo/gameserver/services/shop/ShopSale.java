package org.pokemmo.gameserver.services.shop;

import java.util.List;

import org.jooq.DSLContext;
import org.pokemmo.db.jooq.tables.records.OwnedItemRecord;
import org.pokemmo.gameserver.game.shop.ShopDefinition;
import org.pokemmo.gameserver.game.shop.ShopItem;
import org.pokemmo.gameserver.game.shop.ShopRequest;

import static org.pokemmo.db.jooq.Tables.OWNED_ITEM;

final class ShopSale {
    int apply(DSLContext tx, long ownerId, short inventoryId, int money, ShopDefinition shop,
              ShopRequest request, List<OwnedItemRecord> rows) {
        OwnedItemRecord item = rows.stream()
                .filter(row -> row.getItemId() == request.targetId()).findFirst().orElse(null);
        if (item == null || item.getItemAmount() < request.amount()) {
            throw new ShopRejectedException("出售道具不属于当前角色主背包或数量不足");
        }
        ShopItem offer = shop.item(Short.toUnsignedInt(item.getItemIndexId()));
        if (!shop.sellEnabled() || offer == null || offer.sellPrice() == null) {
            throw new ShopRejectedException("该店铺不回收这件道具");
        }
        ShopItemPolicy.requireSellable(item);
        long nextMoney = money + Math.multiplyExact((long) offer.sellPrice(), request.amount());
        if (nextMoney > Integer.MAX_VALUE) {
            throw new ShopRejectedException("出售后金钱超过上限");
        }
        short previousAmount = item.getItemAmount();
        short remaining = (short) (previousAmount - request.amount());
        int changed;
        if (remaining == 0) {
            changed = tx.deleteFrom(OWNED_ITEM)
                    .where(OWNED_ITEM.ITEM_ID.eq(item.getItemId()))
                    .and(OWNED_ITEM.OWNER_ID.eq(ownerId))
                    .and(OWNED_ITEM.INVENTORY_ID.eq(inventoryId))
                    .and(OWNED_ITEM.ITEM_AMOUNT.eq(previousAmount)).execute();
        } else {
            changed = tx.update(OWNED_ITEM).set(OWNED_ITEM.ITEM_AMOUNT, remaining)
                    .where(OWNED_ITEM.ITEM_ID.eq(item.getItemId()))
                    .and(OWNED_ITEM.OWNER_ID.eq(ownerId))
                    .and(OWNED_ITEM.INVENTORY_ID.eq(inventoryId))
                    .and(OWNED_ITEM.ITEM_AMOUNT.eq(previousAmount)).execute();
        }
        if (changed != 1) {
            throw new ShopRejectedException("出售时道具数量发生变化，请重试");
        }
        if (remaining == 0) rows.remove(item);
        else item.setItemAmount(remaining);
        return (int) nextMoney;
    }
}
