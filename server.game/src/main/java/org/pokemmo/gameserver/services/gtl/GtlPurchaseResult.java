package org.pokemmo.gameserver.services.gtl;

import org.pokemmo.db.jooq.tables.records.OwnedItemRecord;
import org.pokemmo.gameserver.game.gtl.GtlActionResult;
import org.pokemmo.gameserver.game.pokemon.PokemonData;

/** Outcome of purchasing a GTL listing after ownership has been transferred. */
public record GtlPurchaseResult(
        GtlActionResult result,
        int remainingMoney,
        PokemonData pokemon,
        OwnedItemRecord item) {
    public GtlPurchaseResult(GtlActionResult result, int remainingMoney, PokemonData pokemon) {
        this(result, remainingMoney, pokemon, null);
    }

    public static GtlPurchaseResult rejected() {
        return new GtlPurchaseResult(GtlActionResult.REJECTED, -1, null, null);
    }
}
