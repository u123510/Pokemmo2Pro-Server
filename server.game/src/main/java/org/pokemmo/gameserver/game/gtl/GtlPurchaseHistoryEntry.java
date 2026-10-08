package org.pokemmo.gameserver.game.gtl;

/**
 * One completed GTL purchase as rendered by the client's recent-trade view.
 */
public record GtlPurchaseHistoryEntry(
        long tradedAtEpochMillis,
        short itemIndexId,
        short pokemonDexId,
        int amount,
        int totalPrice) {
}
