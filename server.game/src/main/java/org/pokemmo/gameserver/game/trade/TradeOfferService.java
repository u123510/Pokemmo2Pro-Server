package org.pokemmo.gameserver.game.trade;

import org.pokemmo.gameserver.game.character.CharacterManager;
import org.pokemmo.gameserver.services.GameServerService;

import java.util.List;

/** Public trade-offer API backed by focused money/item and Pokemon collaborators. */
public final class TradeOfferService {
    private final TradeMoneyOfferService moneyOffers = new TradeMoneyOfferService();
    private final TradePokemonOfferService pokemonOffers = new TradePokemonOfferService();

    public boolean setMoney(CharacterManager manager, int money) {
        return moneyOffers.setMoney(manager, money);
    }

    public boolean setPokemon(CharacterManager manager, int containerId, short position,
                              short action, GameServerService service) {
        return pokemonOffers.setPokemon(manager, containerId, position, action, service);
    }

    public boolean setPokemonPositionChange(CharacterManager manager,
                                            int sourceContainerId, short sourcePosition,
                                            int targetContainerId, short targetPosition,
                                            GameServerService service) {
        return pokemonOffers.setPokemonPositionChange(manager, sourceContainerId, sourcePosition,
                targetContainerId, targetPosition, service);
    }

    public boolean setPokemonPositionChanges(
            CharacterManager manager,
            List<GameServerService.PokemonPositionChange> changes,
            GameServerService service) {
        return pokemonOffers.setPokemonPositionChanges(manager, changes, service);
    }

    public boolean setItem(CharacterManager manager, int slot, long itemId, short amount,
                           GameServerService service) {
        return moneyOffers.setItem(manager, slot, itemId, amount, service);
    }

    public boolean isPokemonPositionOffered(CharacterManager manager, int containerId,
                                            short position, GameServerService service) {
        return pokemonOffers.isPokemonPositionOffered(manager, containerId, position, service);
    }

    public boolean isPokemonOffered(CharacterManager manager, long pokemonId) {
        return pokemonOffers.isPokemonOffered(manager, pokemonId);
    }

    public boolean isItemOffered(CharacterManager manager, long itemId) {
        return moneyOffers.isItemOffered(manager, itemId);
    }
}
