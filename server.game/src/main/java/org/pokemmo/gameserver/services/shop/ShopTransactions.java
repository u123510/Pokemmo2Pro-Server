package org.pokemmo.gameserver.services.shop;

import java.util.ArrayList;
import java.util.List;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.pokemmo.db.Database;
import org.pokemmo.db.jooq.tables.records.OwnedItemRecord;
import org.pokemmo.gameserver.game.shop.ShopDefinition;
import org.pokemmo.gameserver.game.shop.ShopRequest;
import org.pokemmo.gameserver.util.SnowflakeIdGenerator;

import static org.pokemmo.db.jooq.Tables.CHARACTER;
import static org.pokemmo.db.jooq.Tables.INVENTORY;
import static org.pokemmo.db.jooq.Tables.OWNED_ITEM;

/** Character and inventory writes commit together; no packets or online state are changed here. */
@Slf4j
public final class ShopTransactions {
    private final Database database;
    private final ShopPurchase purchase;
    private final ShopSale sale = new ShopSale();

    @Inject
    public ShopTransactions(Database database, SnowflakeIdGenerator ids) {
        this.database = database;
        this.purchase = new ShopPurchase(ids);
    }

    public ShopTransactionResult execute(long ownerId, ShopDefinition shop, ShopRequest request) {
        if (ownerId <= 0 || shop == null || request == null) {
            return ShopTransactionResult.rejected("商店交易缺少有效角色或请求");
        }
        return run(ownerId, shop, request);
    }

    public ShopTransactionResult readState(long ownerId) {
        if (ownerId <= 0) return ShopTransactionResult.rejected("角色编号无效");
        return run(ownerId, null, null);
    }

    private ShopTransactionResult run(long ownerId, ShopDefinition shop, ShopRequest request) {
        try {
            return database.ctx().transactionResult(configuration -> {
                DSLContext tx = DSL.using(configuration);
                var character = tx.selectFrom(CHARACTER).where(CHARACTER.ID.eq(ownerId))
                        .forUpdate().fetchOne();
                if (character == null || character.getMoney() == null || character.getMoney() < 0) {
                    throw new ShopRejectedException("角色不存在或金钱记录无效");
                }
                var inventory = tx.selectFrom(INVENTORY).where(INVENTORY.NAME.eq("inventory")).fetchOne();
                if (inventory == null || inventory.getId() == null
                        || inventory.getId() < 0 || inventory.getId() > 255) {
                    throw new ShopRejectedException("主背包记录无效");
                }
                short inventoryId = inventory.getId();
                List<OwnedItemRecord> rows = new ArrayList<>(tx.selectFrom(OWNED_ITEM)
                        .where(OWNED_ITEM.OWNER_ID.eq(ownerId))
                        .and(OWNED_ITEM.INVENTORY_ID.eq(inventoryId))
                        .orderBy(OWNED_ITEM.ITEM_ID.asc()).forUpdate().fetch());
                ShopItemPolicy.checkInventory(rows);
                int money = character.getMoney();
                if (request != null) {
                    money = request.action() == ShopRequest.BUY
                            ? purchase.apply(tx, ownerId, inventoryId, money, shop, request, rows)
                            : sale.apply(tx, ownerId, inventoryId, money, shop, request, rows);
                }
                ShopItemPolicy.checkInventory(rows);
                if (request != null && tx.update(CHARACTER).set(CHARACTER.MONEY, money)
                        .where(CHARACTER.ID.eq(ownerId))
                        .and(CHARACTER.MONEY.eq(character.getMoney())).execute() != 1) {
                    throw new ShopRejectedException("角色金钱更新失败");
                }
                return new ShopTransactionResult(ShopTransactionResult.SUCCESS,
                        request == null ? "资产已同步" : request.action() == ShopRequest.BUY ? "购买成功" : "出售成功",
                        money, inventory, rows);
            });
        } catch (ShopRejectedException exception) {
            log.debug("商店操作被拒绝: 角色编号={}, 原因={}", ownerId, exception.getMessage());
            return ShopTransactionResult.rejected(exception.getMessage());
        } catch (RuntimeException exception) {
            // A dropped connection during commit cannot safely be retried automatically.
            log.error("商店事务执行或提交失败: 角色编号={}, 请求={}", ownerId, request, exception);
            return request == null ? ShopTransactionResult.rejected("资产暂时无法读取，请稍后再试")
                    : ShopTransactionResult.uncertain();
        }
    }
}
