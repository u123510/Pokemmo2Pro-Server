package org.pokemmo.gameserver.services.story;

import java.util.ArrayList;
import java.util.List;

import org.jooq.DSLContext;
import org.pokemmo.db.jooq.tables.records.InventoryRecord;
import org.pokemmo.db.jooq.tables.records.OwnedItemRecord;
import org.pokemmo.gameserver.game.item.ItemManager;

import static org.pokemmo.db.jooq.Tables.INVENTORY;
import static org.pokemmo.db.jooq.Tables.OWNED_ITEM;

/** Owner-scoped inventory operations inside the caller's character transaction. */
final class StoryInventory {
    private final DSLContext tx;
    private final long ownerId;
    final InventoryRecord inventory;
    final List<OwnedItemRecord> rows;

    StoryInventory(DSLContext tx, long ownerId) {
        this.tx = tx;
        this.ownerId = ownerId;
        inventory = tx.selectFrom(INVENTORY).where(INVENTORY.NAME.eq("inventory")).fetchOne();
        if (inventory == null || inventory.getId() == null || inventory.getId() < 0 || inventory.getId() > 255) {
            throw new IllegalStateException("主背包尚未就绪");
        }
        rows = new ArrayList<>(tx.selectFrom(OWNED_ITEM).where(OWNED_ITEM.OWNER_ID.eq(ownerId))
                .and(OWNED_ITEM.INVENTORY_ID.eq(inventory.getId())).orderBy(OWNED_ITEM.ITEM_ID)
                .forUpdate().fetch());
        checkCapacity();
    }

    boolean hasParcel() {
        return rows.stream().anyMatch(row -> row.getItemIndexId() == ItemManager.OAK_PARCEL_ITEM_ID);
    }

    void ensureParcel(long objectId) {
        if (!hasParcel()) addStack(objectId, ItemManager.OAK_PARCEL_ITEM_ID, (short) 1, (short) 0);
        checkCapacity();
    }

    void consumeParcel() {
        OwnedItemRecord parcel = rows.stream().filter(row -> row.getItemIndexId() == ItemManager.OAK_PARCEL_ITEM_ID)
                .findFirst().orElseThrow(() -> new IllegalStateException("背包没有大木的包裹，请回常磐市商店补领"));
        short amount = parcel.getItemAmount();
        if (amount == 1) {
            if (tx.deleteFrom(OWNED_ITEM).where(OWNED_ITEM.ITEM_ID.eq(parcel.getItemId()))
                    .and(OWNED_ITEM.OWNER_ID.eq(ownerId)).and(OWNED_ITEM.INVENTORY_ID.eq(inventory.getId()))
                    .and(OWNED_ITEM.ITEM_INDEX_ID.eq(ItemManager.OAK_PARCEL_ITEM_ID))
                    .and(OWNED_ITEM.ITEM_AMOUNT.eq(amount)).execute() != 1) {
                throw new IllegalStateException("剧情包裹回收失败");
            }
            rows.remove(parcel);
        } else changeAmount(parcel, (short) (amount - 1));
    }

    void grant(short itemId, short amount, long objectId) {
        var metadata = ItemManager.getItemData(itemId);
        if (metadata == null || amount <= 0 || metadata.getItemMaxStackSize() < amount) {
            throw new IllegalArgumentException("剧情奖励道具或数量无效");
        }
        int remaining = amount;
        for (OwnedItemRecord row : rows) {
            if (remaining == 0) break;
            if (row.getItemIndexId() != itemId || row.getColorId() != -1 || row.getItemRegionIndexId() != -1
                    || row.getPvpRewardLevel() != -1 || row.getPvpRewardSeason() != -1) continue;
            int addition = Math.min(remaining, Math.max(0, metadata.getItemMaxStackSize() - row.getItemAmount()));
            if (addition > 0) {
                changeAmount(row, (short) (row.getItemAmount() + addition));
                remaining -= addition;
            }
        }
        if (remaining > 0) addStack(objectId, itemId, (short) remaining, (short) -1);
        checkCapacity();
    }

    private void changeAmount(OwnedItemRecord row, short next) {
        if (tx.update(OWNED_ITEM).set(OWNED_ITEM.ITEM_AMOUNT, next)
                .where(OWNED_ITEM.ITEM_ID.eq(row.getItemId())).and(OWNED_ITEM.OWNER_ID.eq(ownerId))
                .and(OWNED_ITEM.INVENTORY_ID.eq(inventory.getId()))
                .and(OWNED_ITEM.ITEM_AMOUNT.eq(row.getItemAmount())).execute() != 1) {
            throw new IllegalStateException("剧情道具堆叠发生变化，请重试");
        }
        row.setItemAmount(next);
    }

    private void addStack(long objectId, short itemId, short amount, short region) {
        if (objectId <= 0) throw new IllegalArgumentException("剧情道具编号无效");
        OwnedItemRecord row = new OwnedItemRecord();
        row.setItemId(objectId);
        row.setOwnerId(ownerId);
        row.setInventoryId(inventory.getId());
        row.setItemIndexId(itemId);
        row.setItemAmount(amount);
        row.setColorId((short) -1);
        row.setItemRegionIndexId(region);
        row.setPvpRewardLevel((short) -1);
        row.setPvpRewardSeason((short) -1);
        if (tx.insertInto(OWNED_ITEM).set(row).execute() != 1) throw new IllegalStateException("剧情道具发放失败");
        rows.add(row);
    }

    private void checkCapacity() {
        int bytes = 4;
        for (OwnedItemRecord row : rows) {
            if (row.getItemId() == null || row.getItemId() <= 0 || row.getItemIndexId() == null
                    || row.getItemAmount() == null || row.getItemAmount() <= 0) {
                throw new IllegalStateException("背包存在无效道具，无法同步剧情奖励");
            }
            if (row.getColorId() == null) row.setColorId((short) -1);
            if (row.getItemRegionIndexId() == null) row.setItemRegionIndexId((short) -1);
            if (row.getPvpRewardLevel() == null) row.setPvpRewardLevel((short) -1);
            if (row.getPvpRewardSeason() == null) row.setPvpRewardSeason((short) -1);
            boolean reward = row.getPvpRewardLevel() != -1;
            if (reward && row.getPvpRewardTime() == null) throw new IllegalStateException("背包奖励道具缺少时间数据");
            bytes += 14 + (row.getColorId() == -1 ? 0 : 1) + (row.getItemRegionIndexId() == -1 ? 0 : 1) + (reward ? 6 : 0);
            if (bytes > 65000) throw new IllegalStateException("背包数据超过客户端单包容量，暂不能发放剧情奖励");
        }
        if (rows.size() > 65535) throw new IllegalStateException("背包道具条目过多");
    }
}
