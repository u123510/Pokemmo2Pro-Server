package org.pokemmo.gameserver.game.gtl;

public record GtlItemListing(
        long listingId,
        int unitPrice,
        int createdAtEpochSeconds,
        int expiresAtEpochSeconds,
        short amount,
        byte status,
        short soldAmount,
        short itemIndexId,
        byte colorId) implements GtlListingEntry {
    @Override
    public GtlListingType listingType() {
        return GtlListingType.ITEM;
    }
}
