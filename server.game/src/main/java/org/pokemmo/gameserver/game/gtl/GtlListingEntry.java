package org.pokemmo.gameserver.game.gtl;

public interface GtlListingEntry {
    long listingId();

    GtlListingType listingType();

    int unitPrice();

    int createdAtEpochSeconds();

    int expiresAtEpochSeconds();

    short amount();

    byte status();

    short soldAmount();
}
