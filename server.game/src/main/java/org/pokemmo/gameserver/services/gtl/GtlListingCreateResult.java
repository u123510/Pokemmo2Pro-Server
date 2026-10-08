package org.pokemmo.gameserver.services.gtl;

import org.pokemmo.gameserver.game.gtl.GtlActionResult;

/** Outcome of creating a GTL listing after its transaction commits. */
public record GtlListingCreateResult(GtlActionResult result, int remainingMoney) {
    public static GtlListingCreateResult rejected() {
        return new GtlListingCreateResult(GtlActionResult.REJECTED, -1);
    }
}
