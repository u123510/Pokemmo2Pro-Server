package org.pokemmo.gameserver.services.shop;

import java.util.List;

import org.pokemmo.db.jooq.tables.records.OwnedItemRecord;
import org.pokemmo.gameserver.game.item.ItemData;
import org.pokemmo.gameserver.game.item.ItemManager;
import org.pokemmo.gameserver.game.item.ItemType;

final class ShopItemPolicy {
    // Leave room for framing, opcode and container metadata in the 16-bit frame.
    private static final int MAX_INVENTORY_BYTES = 65000;

    private ShopItemPolicy() {
    }

    static ItemData metadata(int id) {
        ItemData item = ItemManager.getItemData((short) id);
        if (item == null || item.isBindAccount()
                || item.getItemType() == ItemType.KEY_ITEMS
                || !ItemManager.isTradeableForExchange((short) id)
                || item.getItemMaxStackSize() <= 0) {
            throw new ShopRejectedException("该道具不存在或不允许在商店买卖");
        }
        return item;
    }

    static boolean plainStack(OwnedItemRecord item) {
        return unrestricted(item.getColorId()) && unrestricted(item.getItemRegionIndexId())
                && unrestricted(item.getPvpRewardLevel()) && unrestricted(item.getPvpRewardSeason());
    }

    static void requireSellable(OwnedItemRecord item) {
        metadata(Short.toUnsignedInt(item.getItemIndexId()));
        if (!unrestricted(item.getItemRegionIndexId())
                || !unrestricted(item.getPvpRewardLevel()) || !unrestricted(item.getPvpRewardSeason())) {
            throw new ShopRejectedException("地区限定或 PvP 奖励道具不能出售");
        }
    }

    static void checkInventory(List<OwnedItemRecord> rows) {
        if (rows.size() > 0xFFFF) {
            throw new ShopRejectedException("背包条目数量超过客户端上限");
        }
        int bytes = 0;
        for (OwnedItemRecord item : rows) {
            if (item.getItemId() == null || item.getItemIndexId() == null
                    || item.getItemAmount() == null || item.getItemAmount() <= 0) {
                throw new ShopRejectedException("背包存在无效道具记录，无法安全刷新");
            }
            // Normalize nullable legacy metadata only in the outgoing snapshot.
            if (item.getColorId() == null) item.setColorId((short) -1);
            if (item.getItemRegionIndexId() == null) item.setItemRegionIndexId((short) -1);
            if (item.getPvpRewardLevel() == null) item.setPvpRewardLevel((short) -1);
            if (item.getPvpRewardSeason() == null) item.setPvpRewardSeason((short) -1);
            boolean reward = item.getPvpRewardLevel() != -1;
            if (reward && item.getPvpRewardTime() == null) {
                throw new ShopRejectedException("PvP 奖励道具缺少时间信息");
            }
            bytes += 14 + (item.getColorId() != -1 ? 1 : 0)
                    + (item.getItemRegionIndexId() != -1 ? 1 : 0) + (reward ? 6 : 0);
        }
        if (bytes > MAX_INVENTORY_BYTES) {
            throw new ShopRejectedException("背包数据超过单次客户端刷新上限");
        }
    }

    private static boolean unrestricted(Short value) {
        return value == null || value == -1;
    }
}
