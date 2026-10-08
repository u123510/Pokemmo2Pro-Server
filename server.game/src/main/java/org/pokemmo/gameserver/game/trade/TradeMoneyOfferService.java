package org.pokemmo.gameserver.game.trade;

import lombok.extern.slf4j.Slf4j;
import org.pokemmo.db.jooq.tables.records.OwnedItemRecord;
import org.pokemmo.gameserver.game.character.CharacterManager;
import org.pokemmo.gameserver.game.item.ItemData;
import org.pokemmo.gameserver.game.item.ItemManager;
import org.pokemmo.gameserver.game.skin.SkinType;
import org.pokemmo.gameserver.protocol.packets.s2c.SendTradeItemPacket;
import org.pokemmo.gameserver.protocol.packets.s2c.SendTradeMoneyPacket;
import org.pokemmo.gameserver.services.GameServerService;

import static org.pokemmo.gameserver.game.trade.TradeSessionRegistry.activeSession;
import static org.pokemmo.gameserver.game.trade.TradeSessionRegistry.broadcast;
import static org.pokemmo.gameserver.game.trade.TradeSessionRegistry.characterId;

/** Validates and updates money and item offers without owning Pokemon slots. */
@Slf4j
final class TradeMoneyOfferService {
    boolean setMoney(CharacterManager manager, int money) {
        if (manager == null || manager.getCharacterData() == null) {
            return false;
        }
        Integer currentMoney = manager.getCharacterData().getMoney();
        TradeSession session = activeSession(manager);
        if (session == null || money < 0 || currentMoney == null || currentMoney < 0
                || money > currentMoney) {
            return false;
        }
        synchronized (session) {
            if (session.getState() != TradeState.OPEN) {
                return false;
            }
            TradeSession.Offer offer = session.offer(manager);
            if (offer.isLocked()) {
                return false;
            }
            offer.setMoney(money);
            session.resetConfirmations();
        }
        broadcast(session, new SendTradeMoneyPacket((byte) session.sideOf(manager), money));
        return true;
    }

    boolean setItem(CharacterManager manager, int slot, long itemId, short amount,
                    GameServerService service) {
        TradeSession session = activeSession(manager);
        if (session == null || service == null || slot < 0 || slot >= TradeSession.MAX_ITEMS || amount < 0) {
            return false;
        }
        if (itemId <= 0) {
            if (amount != 0) {
                return false;
            }
            TradeSession.Offer offer;
            synchronized (session) {
                offer = session.offer(manager);
                if (session.getState() != TradeState.OPEN || offer.isLocked()) {
                    return false;
                }
                offer.setItem(slot, null);
                session.resetConfirmations();
            }
            broadcast(session, new SendTradeItemPacket((byte) session.sideOf(manager), (short) slot, null));
            return true;
        }
        if (amount <= 0) {
            return false;
        }
        OwnedItemRecord item = service.getOwnedItem(characterId(manager), itemId);
        String rejectionReason = itemOfferRejectionReason(item, amount);
        if (rejectionReason != null) {
            log.warn("拒绝交易道具: characterId={}, slot={}, itemId={}, amount={}, reason={}",
                    characterId(manager), slot, itemId, amount, rejectionReason);
            return false;
        }
        TradeItemOffer tradeOffer;
        synchronized (session) {
            TradeSession.Offer offer = session.offer(manager);
            if (session.getState() != TradeState.OPEN || offer.isLocked()) {
                return false;
            }
            if (offer.containsItem(itemId, slot)) {
                return false;
            }
            tradeOffer = new TradeItemOffer(
                    item.getItemId(), item.getItemIndexId(), amount,
                    item.getColorId() == null ? (byte) -1 : item.getColorId().byteValue());
            offer.setItem(slot, tradeOffer);
            session.resetConfirmations();
        }
        broadcast(session, new SendTradeItemPacket(
                (byte) session.sideOf(manager), (short) slot, tradeOffer));
        return true;
    }

    boolean isItemOffered(CharacterManager manager, long itemId) {
        TradeSession session = activeSession(manager);
        if (session == null || itemId <= 0) {
            return false;
        }
        synchronized (session) {
            return session.offer(manager).getItems().stream()
                    .anyMatch(item -> item.itemId() == itemId);
        }
    }

    private String itemOfferRejectionReason(OwnedItemRecord item, short amount) {
        if (item == null) {
            return "owned_item not found in the owner's main inventory";
        }
        if (item.getItemId() == null || item.getItemId() <= 0) {
            return "owned_item has no valid item_id";
        }
        if (item.getItemIndexId() == null) {
            return "item_index_id is null";
        }
        if (item.getItemAmount() == null || amount <= 0 || amount > item.getItemAmount()) {
            return "amount is missing or exceeds the owned stack";
        }
        ItemData itemData = ItemManager.getItemData(item.getItemIndexId());
        if (itemData == null) {
            return "item metadata is missing for itemIndexId=" + item.getItemIndexId();
        }
        if (itemData.isBindAccount()) {
            return "item is account-bound: itemIndexId=" + item.getItemIndexId();
        }
        boolean wearable = SkinType.isWearableItemIndex(item.getItemIndexId());
        if (!ItemManager.isTradeableForExchange(item.getItemIndexId()) && !wearable) {
            return "item is not tradeable according to the client trade rules: itemIndexId="
                    + item.getItemIndexId();
        }
        return null;
    }
}
