package org.pokemmo.gameserver.game.trade;

/** Immutable item snapshot retained in an active trade session. */
public record TradeItemOffer(long itemId, short itemIndexId, short amount, byte colorId) {
    public boolean isEmpty() {
        return itemId <= 0;
    }
}
