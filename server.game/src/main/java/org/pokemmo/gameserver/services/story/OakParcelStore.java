package org.pokemmo.gameserver.services.story;

import java.util.List;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.pokemmo.db.Database;
import org.pokemmo.db.jooq.tables.records.CharacterRecord;
import org.pokemmo.db.jooq.tables.records.InventoryRecord;
import org.pokemmo.db.jooq.tables.records.OwnedItemRecord;
import org.pokemmo.gameserver.game.item.ItemManager;
import org.pokemmo.gameserver.game.story.OakParcelCatalog;
import org.pokemmo.gameserver.game.story.OakParcelProgress;

import static org.pokemmo.db.jooq.Tables.CHARACTER;
import static org.pokemmo.db.jooq.Tables.INVENTORY;
import static org.pokemmo.db.jooq.Tables.OWNED_ITEM;

/** Parcel/reward mutations and checkpoint changes commit together, including recovery of a lost parcel. */
public final class OakParcelStore {
    public enum Action { PICKUP, DELIVER, RECEIVE_POKEDEX }
    public record Result(OakParcelProgress progress, InventoryRecord inventory, List<OwnedItemRecord> items, boolean changed) {
        public Result { items = List.copyOf(items); }
    }

    private final Database database;

    public OakParcelStore(Database database) { this.database = database; }

    public OakParcelProgress loadProgress(int accountId, long characterId) {
        requireIdentity(accountId, characterId);
        var row = database.ctx().select(CHARACTER.OAK_LAB_STATUS, CHARACTER.OAK_PARCEL_STATUS)
                .from(CHARACTER).where(CHARACTER.ID.eq(characterId))
                .and(CHARACTER.ACCOUNT_ID.eq(accountId)).fetchOne();
        if (row == null) throw new IllegalStateException("角色不存在或不属于当前账号");
        return new OakParcelProgress(row.value1(), row.value2());
    }

    public boolean hasParcel(int accountId, long characterId) {
        requireIdentity(accountId, characterId);
        return database.ctx().fetchExists(database.ctx().selectOne().from(OWNED_ITEM)
                .join(CHARACTER).on(OWNED_ITEM.OWNER_ID.eq(CHARACTER.ID))
                .join(INVENTORY).on(OWNED_ITEM.INVENTORY_ID.eq(INVENTORY.ID))
                .where(CHARACTER.ID.eq(characterId)).and(CHARACTER.ACCOUNT_ID.eq(accountId))
                .and(INVENTORY.NAME.eq("inventory"))
                .and(OWNED_ITEM.ITEM_INDEX_ID.eq(ItemManager.OAK_PARCEL_ITEM_ID)).and(OWNED_ITEM.ITEM_AMOUNT.gt((short) 0)));
    }

    public Result execute(int accountId, long characterId, Action action, long newItemId) {
        requireIdentity(accountId, characterId);
        if (action == null) throw new IllegalArgumentException("未指定包裹剧情操作");
        return database.ctx().transactionResult(configuration -> {
            DSLContext tx = DSL.using(configuration);
            CharacterRecord owner = tx.selectFrom(CHARACTER).where(CHARACTER.ID.eq(characterId))
                    .and(CHARACTER.ACCOUNT_ID.eq(accountId)).forUpdate().fetchOne();
            if (owner == null) throw new IllegalStateException("角色不存在或不属于当前账号");
            OakParcelProgress progress = new OakParcelProgress(owner.getOakLabStatus(), owner.getOakParcelStatus());
            StoryInventory inventory = new StoryInventory(tx, characterId);
            boolean change = shouldApply(progress, action, inventory.hasParcel());
            if (!change) return new Result(progress, inventory.inventory, inventory.rows, false);
            OakParcelProgress next;
            switch (action) {
                case PICKUP -> {
                    inventory.ensureParcel(newItemId);
                    next = new OakParcelProgress(progress.labStage(), (short) 1);
                }
                case DELIVER -> {
                    inventory.consumeParcel();
                    next = new OakParcelProgress((short) 5, (short) 2);
                }
                case RECEIVE_POKEDEX -> {
                    inventory.grant(OakParcelCatalog.BALL_ITEM_ID, OakParcelCatalog.BALL_COUNT, newItemId);
                    next = new OakParcelProgress((short) 6, (short) 2);
                }
                default -> throw new IllegalArgumentException("未知包裹剧情操作");
            }
            if (tx.update(CHARACTER).set(CHARACTER.OAK_LAB_STATUS, next.labStage())
                    .set(CHARACTER.OAK_PARCEL_STATUS, next.parcelStatus())
                    .where(CHARACTER.ID.eq(characterId)).and(CHARACTER.ACCOUNT_ID.eq(accountId))
                    .and(CHARACTER.OAK_LAB_STATUS.eq(progress.labStage()))
                    .and(CHARACTER.OAK_PARCEL_STATUS.eq(progress.parcelStatus())).execute() != 1) {
                throw new IllegalStateException("包裹剧情检查点保存失败，事务回滚");
            }
            return new Result(next, inventory.inventory, inventory.rows, true);
        });
    }

    static boolean shouldApply(OakParcelProgress progress, Action action, boolean hasParcel) {
        var phase = progress.phase();
        if (phase == OakParcelProgress.Phase.OPENING) throw new IllegalStateException("请先完成首次劲敌战斗");
        if (phase == OakParcelProgress.Phase.COMPLETE) return false;
        return switch (action) {
            case PICKUP -> phase == OakParcelProgress.Phase.PICKUP
                    || (phase == OakParcelProgress.Phase.DELIVER && !hasParcel);
            case DELIVER -> {
                if (phase == OakParcelProgress.Phase.PICKUP) throw new IllegalStateException("尚未领取大木的包裹");
                if (phase == OakParcelProgress.Phase.DELIVER && !hasParcel) {
                    throw new IllegalStateException("包裹不在主背包，请回常磐市商店补领");
                }
                yield phase == OakParcelProgress.Phase.DELIVER;
            }
            case RECEIVE_POKEDEX -> {
                if (phase != OakParcelProgress.Phase.POKEDEX) throw new IllegalStateException("请先把包裹交给大木博士");
                yield true;
            }
        };
    }

    private static void requireIdentity(int accountId, long characterId) {
        if (accountId <= 0 || characterId <= 0) throw new IllegalArgumentException("剧情角色归属无效");
    }
}
