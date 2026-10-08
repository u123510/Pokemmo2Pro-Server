package org.pokemmo.gameserver.services.inventory;

import org.pokemmo.db.Database;
import org.pokemmo.db.jooq.tables.records.InventoryRecord;
import org.pokemmo.db.jooq.tables.records.OwnedItemRecord;
import org.pokemmo.gameserver.game.item.ItemData;
import org.pokemmo.gameserver.game.item.ItemManager;

import java.util.List;

import static org.pokemmo.db.jooq.Tables.INVENTORY;
import static org.pokemmo.db.jooq.Tables.OWNED_ITEM;

public final class InventoryService {
    private final Database database;

    public InventoryService(Database database) {
        this.database = database;
    }

public OwnedItemRecord getOwnedItem(long characterId, long ownedItemId) {
    InventoryRecord inventory = getInventory();
    if (characterId <= 0 || ownedItemId <= 0 || inventory == null) {
      return null;
    }
    return database.ctx()
        .selectFrom(OWNED_ITEM)
        .where(OWNED_ITEM.ITEM_ID.eq(ownedItemId))
        .and(OWNED_ITEM.OWNER_ID.eq(characterId))
        .and(OWNED_ITEM.INVENTORY_ID.eq(inventory.getId()))
        .fetchOne();
  }

public OwnedItemRecord getOwnedItemByIndex(long characterId, short itemIndexId) {
    InventoryRecord inventory = getInventory();
    if (characterId <= 0 || inventory == null) {
      return null;
    }
    return database.ctx()
        .selectFrom(OWNED_ITEM)
        .where(OWNED_ITEM.OWNER_ID.eq(characterId))
        .and(OWNED_ITEM.ITEM_INDEX_ID.eq(itemIndexId))
        .and(OWNED_ITEM.INVENTORY_ID.eq(inventory.getId()))
        .fetchAny();
  }

public List<InventoryRecord> getInventories() {
    return database.ctx()
        .select().from(INVENTORY)
        .fetchInto(InventoryRecord.class);
  }

public List<OwnedItemRecord> getItemsByContainerAndCharacter(long characterId, InventoryRecord inventory) {
    return database.ctx()
        .select().from(OWNED_ITEM)
        .where(OWNED_ITEM.OWNER_ID.eq(characterId))
        .and(OWNED_ITEM.INVENTORY_ID.eq(inventory.getId()))
        .fetchInto(OwnedItemRecord.class);
  }

public OwnedItemRecord addInventoryItem(long characterId, short itemIndexId, short amount, long itemId) {
    InventoryRecord inventory = getInventory();
    if (inventory == null || amount <= 0) {
      return null;
    }

    OwnedItemRecord existing = database.ctx()
        .selectFrom(OWNED_ITEM)
        .where(OWNED_ITEM.OWNER_ID.eq(characterId))
        .and(OWNED_ITEM.ITEM_INDEX_ID.eq(itemIndexId))
        .and(OWNED_ITEM.INVENTORY_ID.eq(inventory.getId()))
        .and(OWNED_ITEM.COLOR_ID.eq((short) -1))
        .and(OWNED_ITEM.ITEM_REGION_INDEX_ID.eq((short) -1))
        .and(OWNED_ITEM.PVP_REWARD_LEVEL.eq((short) -1))
        .and(OWNED_ITEM.PVP_REWARD_SEASON.eq((short) -1))
        .fetchOne();
    if (existing != null) {
      int totalAmount = existing.getItemAmount() + amount;
      if (totalAmount > Short.MAX_VALUE) {
        return null;
      }
      database.ctx()
          .update(OWNED_ITEM)
          .set(OWNED_ITEM.ITEM_AMOUNT, (short) totalAmount)
          .where(OWNED_ITEM.ITEM_ID.eq(existing.getItemId()))
          .execute();
      existing.setItemAmount((short) totalAmount);
      return existing;
    }

    OwnedItemRecord created = new OwnedItemRecord();
    created.setItemId(itemId);
    created.setOwnerId(characterId);
    created.setItemIndexId(itemIndexId);
    created.setItemAmount(amount);
    created.setInventoryId(inventory.getId());
    created.setColorId((short) -1);
    created.setItemRegionIndexId((short) -1);
    created.setPvpRewardLevel((short) -1);
    created.setPvpRewardSeason((short) -1);
    database.ctx()
        .insertInto(OWNED_ITEM)
        .set(created)
        .execute();
    return created;
  }

public boolean removeInventoryItem(long characterId, long itemId, short amount) {
    InventoryRecord inventory = getInventory();
    if (inventory == null || itemId <= 0 || amount <= 0) {
      return false;
    }

    OwnedItemRecord existing = database.ctx()
        .selectFrom(OWNED_ITEM)
        .where(OWNED_ITEM.ITEM_ID.eq(itemId))
        .and(OWNED_ITEM.OWNER_ID.eq(characterId))
        .and(OWNED_ITEM.INVENTORY_ID.eq(inventory.getId()))
        .fetchOne();
    if (existing == null || existing.getItemAmount() == null
        || existing.getItemAmount() < amount) {
      return false;
    }

    ItemData itemData = ItemManager.getItemData(existing.getItemIndexId());
    if (itemData == null || !itemData.isDestoryable() || ItemManager.isStoryBound(existing.getItemIndexId())) {
      return false;
    }

    int remainingAmount = existing.getItemAmount() - amount;
    if (remainingAmount == 0) {
      return database.ctx()
          .deleteFrom(OWNED_ITEM)
          .where(OWNED_ITEM.ITEM_ID.eq(itemId))
          .and(OWNED_ITEM.OWNER_ID.eq(characterId))
          .and(OWNED_ITEM.INVENTORY_ID.eq(inventory.getId()))
          .execute() == 1;
    }

    return database.ctx()
        .update(OWNED_ITEM)
        .set(OWNED_ITEM.ITEM_AMOUNT, (short) remainingAmount)
        .where(OWNED_ITEM.ITEM_ID.eq(itemId))
        .and(OWNED_ITEM.OWNER_ID.eq(characterId))
        .and(OWNED_ITEM.INVENTORY_ID.eq(inventory.getId()))
        .execute() == 1;
  }

public boolean removeStoryItem(long characterId, long itemId, short amount) {
    InventoryRecord inventory = getInventory();
    if (inventory == null || characterId <= 0 || itemId <= 0 || amount <= 0) {
      return false;
    }

    OwnedItemRecord existing = database.ctx()
        .selectFrom(OWNED_ITEM)
        .where(OWNED_ITEM.ITEM_ID.eq(itemId))
        .and(OWNED_ITEM.OWNER_ID.eq(characterId))
        .and(OWNED_ITEM.INVENTORY_ID.eq(inventory.getId()))
        .fetchOne();
    if (existing == null || existing.getItemAmount() == null
        || existing.getItemAmount() < amount) {
      return false;
    }

    int remainingAmount = existing.getItemAmount() - amount;
    if (remainingAmount == 0) {
      return database.ctx()
          .deleteFrom(OWNED_ITEM)
          .where(OWNED_ITEM.ITEM_ID.eq(itemId))
          .and(OWNED_ITEM.OWNER_ID.eq(characterId))
          .and(OWNED_ITEM.INVENTORY_ID.eq(inventory.getId()))
          .execute() == 1;
    }

    return database.ctx()
        .update(OWNED_ITEM)
        .set(OWNED_ITEM.ITEM_AMOUNT, (short) remainingAmount)
        .where(OWNED_ITEM.ITEM_ID.eq(itemId))
        .and(OWNED_ITEM.OWNER_ID.eq(characterId))
        .and(OWNED_ITEM.INVENTORY_ID.eq(inventory.getId()))
        .execute() == 1;
  }

public InventoryRecord getInventory() {
    return database.ctx()
        .select().from(INVENTORY)
        .where(INVENTORY.NAME.eq("inventory"))
        .fetchOneInto(InventoryRecord.class);
  }

}
