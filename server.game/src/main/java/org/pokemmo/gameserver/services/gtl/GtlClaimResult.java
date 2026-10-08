package org.pokemmo.gameserver.services.gtl;

import org.pokemmo.gameserver.game.gtl.GtlActionResult;

/** Outcome of claiming proceeds from completed GTL listings. */
public record GtlClaimResult(
        GtlActionResult result,
        int remainingMoney,
        long claimedAmount,
        int claimedListings) {
    public static GtlClaimResult rejected() {
        return new GtlClaimResult(GtlActionResult.REJECTED, -1, 0, 0);
    }
}
