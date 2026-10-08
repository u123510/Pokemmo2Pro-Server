package org.pokemmo.gameserver.services.gtl;

import org.pokemmo.db.Database;
import org.pokemmo.gameserver.util.SnowflakeIdGenerator;

import java.util.List;

/**
 * Short-term compatibility facade for legacy GTL callers.
 *
 * <p>New code should inject the focused GTL domain services directly. This
 * class intentionally contains no database or business logic.</p>
 */
public final class GtlService {
    private final GtlListingService listingService;
    private final GtlPurchaseHistoryQuery purchaseHistoryQuery;
    private final GtlPurchaseService purchaseService;
    private final GtlClaimService claimService;
    private final GtlCancellationService cancellationService;

    public GtlService(Database database) {
        this.listingService = new GtlListingService(database);
        this.purchaseHistoryQuery = new GtlPurchaseHistoryQuery(database);
        this.purchaseService = new GtlPurchaseService(database);
        this.claimService = new GtlClaimService(database);
        this.cancellationService = new GtlCancellationService(database);
    }

    public GtlListingCreateResult createPokemonGtlListing(
            long characterId, long pokemonId, int unitPrice, short amount) {
        return listingService.createPokemonGtlListing(characterId, pokemonId, unitPrice, amount);
    }

    public GtlListingCreateResult createItemGtlListing(
            long characterId, long itemId, int unitPrice, short amount,
            SnowflakeIdGenerator idGenerator) {
        return listingService.createItemGtlListing(
                characterId, itemId, unitPrice, amount, idGenerator);
    }

    public GtlPurchaseResult purchaseGtlListing(
            long buyerId, long listingId, short amount,
            SnowflakeIdGenerator idGenerator) {
        return purchaseService.purchaseGtlListing(buyerId, listingId, amount, idGenerator);
    }

    public GtlPurchaseResult purchasePokemonGtlListing(
            long buyerId, long listingId, short amount) {
        return purchaseService.purchasePokemonGtlListing(buyerId, listingId, amount);
    }

    public GtlPurchaseResult purchaseItemGtlListing(
            long buyerId, long listingId, short amount,
            SnowflakeIdGenerator idGenerator) {
        return purchaseService.purchaseItemGtlListing(
                buyerId, listingId, amount, idGenerator);
    }

    public List<org.pokemmo.gameserver.game.gtl.GtlPurchaseHistoryEntry> getGtlPurchaseHistory(long buyerId) {
        return purchaseHistoryQuery.getPurchaseHistory(buyerId);
    }

    public GtlClaimResult claimGtlListings(
            long sellerId, List<Long> listingIds) {
        return claimService.claimGtlListings(sellerId, listingIds);
    }

    public GtlListingCancelResult cancelPokemonGtlListing(
            long sellerId, long listingId) {
        return cancellationService.cancelPokemonGtlListing(sellerId, listingId);
    }
}
