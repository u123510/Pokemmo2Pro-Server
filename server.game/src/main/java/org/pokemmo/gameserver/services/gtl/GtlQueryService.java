package org.pokemmo.gameserver.services.gtl;

import com.google.inject.Inject;
import org.pokemmo.db.Database;
import org.pokemmo.gameserver.game.gtl.GtlListingPage;

/** Coordinates focused GTL listing queries through the canonical request API. */
public final class GtlQueryService {
    private final GtlPokemonListingQuery pokemonListingQuery;
    private final GtlItemListingQuery itemListingQuery;
    private final GtlOwnListingQuery ownListingQuery;

    @Inject
    public GtlQueryService(Database database) {
        this.pokemonListingQuery = new GtlPokemonListingQuery(database);
        this.itemListingQuery = new GtlItemListingQuery(database);
        this.ownListingQuery = new GtlOwnListingQuery(database);
    }

    /** Canonical entry point for new GTL query callers. */
    public GtlListingPage search(GtlSearchRequest request) {
        if (request == null || request.listType() == null) {
            return GtlListingPage.empty();
        }
        return switch (request.listType()) {
            case ITEM -> itemListingQuery.search(request);
            case OWN_LISTINGS -> ownListingQuery.search(request);
            case MONSTER -> pokemonListingQuery.search(request);
        };
    }
}
