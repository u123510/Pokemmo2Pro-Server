package org.pokemmo.gameserver.game.gtl;

import org.pokemmo.gameserver.game.pokemon.PokemonData;

public record GtlPokemonListing(
        long listingId,
        int unitPrice,
        int createdAtEpochSeconds,
        int expiresAtEpochSeconds,
        short amount,
        byte status,
        short soldAmount,
        PokemonData pokemon) implements GtlListingEntry {
    @Override
    public GtlListingType listingType() {
        return GtlListingType.POKEMON;
    }
}
