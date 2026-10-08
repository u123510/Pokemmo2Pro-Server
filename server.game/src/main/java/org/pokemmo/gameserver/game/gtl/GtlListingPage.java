package org.pokemmo.gameserver.game.gtl;

import java.util.List;

public record GtlListingPage(int totalListings, List<GtlListingEntry> listings) {
    public GtlListingPage {
        listings = List.copyOf(listings);
    }

    public static GtlListingPage empty() {
        return new GtlListingPage(0, List.of());
    }
}
