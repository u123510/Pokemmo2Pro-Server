package org.pokemmo.gameserver.services.trade;

import org.jooq.DSLContext;
import org.pokemmo.db.jooq.tables.records.OwnedItemRecord;
import org.pokemmo.gameserver.game.item.ItemData;
import org.pokemmo.gameserver.game.item.ItemManager;
import org.pokemmo.gameserver.game.skin.SkinType;
import org.pokemmo.gameserver.game.trade.TradeItemOffer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.pokemmo.db.jooq.Tables.OWNED_ITEM;
import static org.pokemmo.db.jooq.Tables.POKEMON;

/** Validates request shape and locks owned assets inside the trade transaction. */
public final class TradeValidationService {
    public String requestRejectionReason(long firstId, long secondId,
                                         int firstMoney, int secondMoney,
                                         List<Long> firstPokemonIds, List<Long> secondPokemonIds,
                                         List<TradeItemOffer> firstItems,
                                         List<TradeItemOffer> secondItems) {
        if (firstId <= 0 || secondId <= 0 || firstId == secondId) {
            return "角色 ID 非法或双方相同";
        }
        if (firstMoney < 0 || secondMoney < 0) {
            return "报价金钱不能为负数";
        }
        if (firstPokemonIds == null || secondPokemonIds == null) {
            return "宝可梦报价列表为空引用";
        }
        if (firstPokemonIds.size() > 6 || secondPokemonIds.size() > 6) {
            return "宝可梦报价超过 6 个槽位";
        }
        if (firstItems == null || secondItems == null) {
            return "道具报价列表为空引用";
        }
        if (firstItems.size() > 6 || secondItems.size() > 6) {
            return "道具报价超过 6 个槽位";
        }
        if (!distinctPokemonIds(firstPokemonIds, secondPokemonIds)) {
            return "宝可梦报价包含空 ID、非法 ID 或重复 ID";
        }
        if (!distinctItemIds(firstItems, secondItems)) {
            return "道具报价包含空项、非法 ID 或重复 ID";
        }
        return null;
    }

    public List<OwnedItemRecord> lockItems(
            DSLContext tx, long ownerId, List<TradeItemOffer> offers, short inventoryId) {
        Set<Long> ids = new HashSet<>();
        List<OwnedItemRecord> locked = new ArrayList<>(offers.size());
        for (TradeItemOffer offer : offers) {
            if (offer == null || offer.itemId() <= 0 || !ids.add(offer.itemId())) {
                return null;
            }
            OwnedItemRecord item = tx.selectFrom(OWNED_ITEM)
                    .where(OWNED_ITEM.ITEM_ID.eq(offer.itemId()))
                    .and(OWNED_ITEM.OWNER_ID.eq(ownerId))
                    .and(OWNED_ITEM.INVENTORY_ID.eq(inventoryId))
                    .forUpdate()
                    .fetchOne();
            if (!isValidTradeItem(offer, item)) {
                return null;
            }
            locked.add(item);
        }
        return locked;
    }

    public boolean partyWouldRemain(DSLContext tx, long ownerId, List<Long> outgoingIds,
                                    int partyContainerId) {
        return tx.fetchCount(tx.selectFrom(POKEMON).where(POKEMON.TRAINER_ID.eq(ownerId))
                .and(POKEMON.CONTAINER_ID.eq(partyContainerId))
                .and(outgoingIds.isEmpty() ? org.jooq.impl.DSL.trueCondition() : POKEMON.ID.notIn(outgoingIds))) > 0;
    }

    private boolean isValidTradeItem(TradeItemOffer offer, OwnedItemRecord item) {
        if (offer == null || offer.itemId() <= 0 || item == null || item.getItemIndexId() == null
                || item.getItemAmount() == null || offer.amount() <= 0
                || offer.amount() > item.getItemAmount()
                || offer.itemIndexId() != item.getItemIndexId()
                || !sameColor(offer, item)) {
            return false;
        }
        ItemData itemData = ItemManager.getItemData(item.getItemIndexId());
        boolean wearable = SkinType.isWearableItemIndex(item.getItemIndexId());
        return itemData != null && !itemData.isBindAccount()
                && (ItemManager.isTradeableForExchange(item.getItemIndexId()) || wearable);
    }

    private boolean sameColor(TradeItemOffer offer, OwnedItemRecord item) {
        if (item.getColorId() == null) {
            return offer.colorId() == -1;
        }
        return offer.colorId() == (byte) item.getColorId().shortValue();
    }

    private boolean distinctPokemonIds(List<Long> first, List<Long> second) {
        Set<Long> ids = new HashSet<>();
        return first.stream().allMatch(id -> id != null && id > 0 && ids.add(id))
                && second.stream().allMatch(id -> id != null && id > 0 && ids.add(id));
    }

    private boolean distinctItemIds(List<TradeItemOffer> first, List<TradeItemOffer> second) {
        Set<Long> ids = new HashSet<>();
        return first.stream().allMatch(item -> item != null && item.itemId() > 0 && ids.add(item.itemId()))
                && second.stream().allMatch(item -> item != null && item.itemId() > 0 && ids.add(item.itemId()));
    }
}
