package org.pokemmo.gameserver.services.gtl;

import org.pokemmo.gameserver.game.gtl.GtlActionResult;
import org.pokemmo.gameserver.game.pokemon.PokemonData;

/** Outcome of cancelling a Pokemon GTL listing. */
public record GtlListingCancelResult(GtlActionResult result, PokemonData pokemon) {
    public static GtlListingCancelResult rejected() {
        return new GtlListingCancelResult(GtlActionResult.REJECTED, null);
    }
}
