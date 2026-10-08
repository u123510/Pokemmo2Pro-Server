package org.pokemmo.gameserver.services.shop;

import java.util.List;

import org.jooq.DSLContext;
import org.pokemmo.db.jooq.tables.records.OwnedItemRecord;
import org.pokemmo.gameserver.game.shop.ShopDefinition;
import org.pokemmo.gameserver.game.shop.ShopItem;
import org.pokemmo.gameserver.game.shop.ShopRequest;
import org.pokemmo.gameserver.util.SnowflakeIdGenerator;

import static org.pokemmo.db.jooq.Tables.OWNED_ITEM;

final class ShopPurchase {
    private final SnowflakeIdGenerator ids;

    ShopPurchase(SnowflakeIdGenerator ids) {
        this.ids = ids;
    }

    int apply(DSLContext tx, long ownerId, short inventoryId, int money, ShopDefinition shop,
              ShopRequest request, List<OwnedItemRecord> rows) {
        ShopItem offer = shop.item((int) request.targetId());
        if (!shop.buyEnabled() || offer == null || offer.buyPrice() == null) {
            throw new ShopRejectedException("该店铺不出售这件商品");
        }
        int stackLimit = ShopItemPolicy.metadata(offer.itemId()).getItemMaxStackSize();
        long total = Math.multiplyExact((long) offer.buyPrice(), request.amount());
        if (total > money) {
            throw new ShopRejectedException("金钱不足");
        }
        int remaining = request.amount();
        for (OwnedItemRecord row : rows) {
            if (remaining == 0) break;
            if (Short.toUnsignedInt(row.getItemIndexId()) != offer.itemId() || !ShopItemPolicy.plainStack(row)) {
                continue;
            }
            int addition = Math.min(remaining, Math.max(0, stackLimit - row.getItemAmount()));
            if (addition == 0) continue;
            short previousAmount = row.getItemAmount();
            short nextAmount = (short) (previousAmount + addition);
            if (tx.update(OWNED_ITEM).set(OWNED_ITEM.ITEM_AMOUNT, nextAmount)
                    .where(OWNED_ITEM.ITEM_ID.eq(row.getItemId()))
                    .and(OWNED_ITEM.OWNER_ID.eq(ownerId))
                    .and(OWNED_ITEM.INVENTORY_ID.eq(inventoryId))
                    .and(OWNED_ITEM.ITEM_AMOUNT.eq(previousAmount)).execute() != 1) {
                throw new ShopRejectedException("购买时背包堆叠发生变化，请重试");
            }
            row.setItemAmount(nextAmount);
            remaining -= addition;
        }
        while (remaining > 0) {
            int amount = Math.min(remaining, stackLimit);
            OwnedItemRecord created = new OwnedItemRecord();
            created.setItemId(ids.nextId());
            created.setOwnerId(ownerId);
            created.setInventoryId(inventoryId);
            created.setItemIndexId((short) offer.itemId());
            created.setItemAmount((short) amount);
            created.setColorId((short) -1);
            created.setItemRegionIndexId((short) -1);
            created.setPvpRewardLevel((short) -1);
            created.setPvpRewardSeason((short) -1);
            if (tx.insertInto(OWNED_ITEM).set(created).execute() != 1) {
                throw new ShopRejectedException("购买道具写入失败");
            }
            rows.add(created);
            remaining -= amount;
        }
        return (int) (money - total);
    }
}
