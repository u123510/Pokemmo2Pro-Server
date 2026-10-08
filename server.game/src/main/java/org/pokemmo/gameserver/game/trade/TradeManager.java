package org.pokemmo.gameserver.game.trade;

import org.pokemmo.gameserver.game.character.CharacterManager;
import org.pokemmo.gameserver.services.GameServerService;

import java.util.List;

/**
 * Compatibility facade for existing packets and world code.
 *
 * <p>New code should depend on the focused trade collaborators rather than
 * extending this legacy static entry point.</p>
 */
public final class TradeManager {
    private static final TradeRequestRegistry REQUESTS = new TradeRequestRegistry();
    private static final TradeOfferService OFFERS = new TradeOfferService();
    private static final TradeSettlementCoordinator SETTLEMENTS =
            new TradeSettlementCoordinator(REQUESTS);

    private TradeManager() {
    }

    public static boolean registerRequest(CharacterManager requester, CharacterManager target) {
        return REQUESTS.register(requester, target);
    }

    public static boolean handleRequestDecision(CharacterManager target, byte type) {
        return REQUESTS.decide(target, type);
    }

    public static boolean setMoney(CharacterManager manager, int money) {
        return OFFERS.setMoney(manager, money);
    }

    public static boolean setPokemon(CharacterManager manager, int containerId, short position,
                                     short action, GameServerService service) {
        return OFFERS.setPokemon(manager, containerId, position, action, service);
    }

    public static boolean setPokemonPositionChange(CharacterManager manager,
                                                   int sourceContainerId, short sourcePosition,
                                                   int targetContainerId, short targetPosition,
                                                   GameServerService service) {
        return OFFERS.setPokemonPositionChange(manager, sourceContainerId, sourcePosition,
                targetContainerId, targetPosition, service);
    }

    public static boolean setPokemonPositionChanges(
            CharacterManager manager,
            List<GameServerService.PokemonPositionChange> changes,
            GameServerService service) {
        return OFFERS.setPokemonPositionChanges(manager, changes, service);
    }

    public static boolean setItem(CharacterManager manager, int slot, long itemId, short amount,
                                  GameServerService service) {
        return OFFERS.setItem(manager, slot, itemId, amount, service);
    }

    public static boolean setLock(CharacterManager manager, byte action, GameServerService service) {
        return SETTLEMENTS.setLock(manager, action, service);
    }

    public static void cancelFor(CharacterManager manager) {
        SETTLEMENTS.cancel(manager);
    }

    public static void cancelPendingFor(CharacterManager manager) {
        REQUESTS.cancelPendingFor(manager);
    }

    public static boolean isPokemonPositionOffered(CharacterManager manager, int containerId,
                                                    short position, GameServerService service) {
        return OFFERS.isPokemonPositionOffered(manager, containerId, position, service);
    }

    public static boolean isPokemonOffered(CharacterManager manager, long pokemonId) {
        return OFFERS.isPokemonOffered(manager, pokemonId);
    }

    public static boolean isItemOffered(CharacterManager manager, long itemId) {
        return OFFERS.isItemOffered(manager, itemId);
    }

    public static boolean isInTrade(CharacterManager manager) {
        return TradeSessionRegistry.activeSession(manager) != null;
    }
}
