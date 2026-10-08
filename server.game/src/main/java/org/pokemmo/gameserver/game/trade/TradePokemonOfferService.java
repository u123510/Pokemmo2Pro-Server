package org.pokemmo.gameserver.game.trade;

import lombok.extern.slf4j.Slf4j;
import org.pokemmo.db.jooq.tables.records.ContainerRecord;
import org.pokemmo.gameserver.game.character.CharacterData;
import org.pokemmo.gameserver.game.character.CharacterManager;
import org.pokemmo.gameserver.game.container.PokemonContainerType;
import org.pokemmo.gameserver.game.pokemon.PokemonData;
import org.pokemmo.gameserver.protocol.packets.s2c.SendAddPokemonPacket;
import org.pokemmo.gameserver.protocol.packets.s2c.SendRemovePokemonPacket;
import org.pokemmo.gameserver.protocol.packets.s2c.SendTradePokemonPacket;
import org.pokemmo.gameserver.services.GameServerService;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.pokemmo.gameserver.game.trade.TradeSessionRegistry.activeSession;
import static org.pokemmo.gameserver.game.trade.TradeSessionRegistry.characterId;
import static org.pokemmo.gameserver.game.trade.TradeSessionRegistry.send;

/** Owns temporary TRADE-container Pokemon offers and their client synchronization. */
@Slf4j
final class TradePokemonOfferService {
    boolean setPokemon(CharacterManager manager, int containerId, short position,
                       short action, GameServerService service) {
        TradeSession session = activeSession(manager);
        if (session == null || service == null || manager == null) {
            return false;
        }
        if (action == 0 && containerId == PokemonContainerType.TRADE.getType()) {
            return removePokemonAt(manager, position);
        }
        if (containerId == PokemonContainerType.TRADE.getType()) {
            return false;
        }
        int capacity = containerCapacity(manager, containerId);
        if (position < 0 || position >= capacity || (action != 0 && action != 1)) {
            return false;
        }
        PokemonData pokemon = findPokemon(manager, containerId, position, service);
        if (pokemon == null || pokemon.getPokemonId() <= 0
                || !Long.valueOf(characterId(manager)).equals(pokemon.getTrainerId())) {
            return false;
        }
        if (action == 1) {
            short tradeSlot;
            synchronized (session) {
                tradeSlot = (short) firstFreePokemonSlot(session.offer(manager));
            }
            return offerPokemonAt(manager, containerId, position, tradeSlot, service);
        }
        return removePokemonById(manager, pokemon.getPokemonId());
    }

    /** Handles the official 0x09 source/target slot transfer packet. */
    boolean setPokemonPositionChange(CharacterManager manager,
                                     int sourceContainerId, short sourcePosition,
                                     int targetContainerId, short targetPosition,
                                     GameServerService service) {
        if (manager == null || service == null) {
            return false;
        }
        return setPokemonPositionChanges(manager,
                List.of(new GameServerService.PokemonPositionChange(
                        sourceContainerId, sourcePosition, targetContainerId, targetPosition)),
                service);
    }

    /**
     * Applies one official 0x09 batch atomically to the in-memory trade offer.
     * The database remains unchanged until both sides finally confirm.
     */
    boolean setPokemonPositionChanges(
            CharacterManager manager,
            List<GameServerService.PokemonPositionChange> changes,
            GameServerService service) {
        TradeSession session = activeSession(manager);
        if (session == null || service == null || changes == null || changes.isEmpty()
                || changes.size() > TradeSession.MAX_POKEMON * 2) {
            return false;
        }

        int tradeContainerId = PokemonContainerType.TRADE.getType();
        List<TradePokemonOffer> added = new ArrayList<>();
        List<Short> addedSlots = new ArrayList<>();
        List<TradePokemonOffer> removed = new ArrayList<>();
        Set<Long> touchedPokemonIds = new HashSet<>();
        synchronized (session) {
            if (session.getState() != TradeState.OPEN) {
                return false;
            }
            TradeSession.Offer offer = session.offer(manager);
            if (offer.isLocked()) {
                return false;
            }

            TradePokemonOffer[] working = offer.copyPokemonSlots();
            List<GameServerService.PokemonPositionChange> removals = new ArrayList<>();
            List<GameServerService.PokemonPositionChange> additions = new ArrayList<>();
            for (GameServerService.PokemonPositionChange change : changes) {
                if (change == null || change.oldPosition() < 0 || change.newPosition() < 0) {
                    return false;
                }
                if (change.newContainerId() == tradeContainerId
                        && isPersistentContainer(change.oldContainerId())) {
                    additions.add(change);
                    continue;
                }
                if (change.oldContainerId() == tradeContainerId
                        && isPersistentContainer(change.newContainerId())) {
                    removals.add(change);
                    continue;
                }
                return false;
            }

            for (GameServerService.PokemonPositionChange change : removals) {
                if (change.oldPosition() >= TradeSession.MAX_POKEMON) {
                    return false;
                }
                int targetCapacity = containerCapacity(manager, change.newContainerId());
                if (change.newPosition() >= targetCapacity) {
                    return false;
                }
                TradePokemonOffer pokemonOffer = working[change.oldPosition()];
                if (pokemonOffer == null || !touchedPokemonIds.add(pokemonOffer.pokemonId())) {
                    return false;
                }
                PokemonData targetPokemon = findPokemon(
                        manager, change.newContainerId(), change.newPosition(), service);
                if (targetPokemon != null && targetPokemon.getPokemonId() != pokemonOffer.pokemonId()) {
                    return false;
                }
                working[change.oldPosition()] = null;
                removed.add(pokemonOffer);
            }

            for (GameServerService.PokemonPositionChange change : additions) {
                if (change.newPosition() >= TradeSession.MAX_POKEMON) {
                    return false;
                }
                int sourceCapacity = containerCapacity(manager, change.oldContainerId());
                if (change.oldPosition() >= sourceCapacity) {
                    return false;
                }
                PokemonData pokemon = findPokemon(
                        manager, change.oldContainerId(), change.oldPosition(), service);
                if (pokemon == null || pokemon.getPokemonId() <= 0
                        || !Long.valueOf(characterId(manager)).equals(pokemon.getTrainerId())
                        || !touchedPokemonIds.add(pokemon.getPokemonId())
                        || containsPokemon(working, pokemon.getPokemonId())
                        || working[change.newPosition()] != null) {
                    return false;
                }
                TradePokemonOffer pokemonOffer = new TradePokemonOffer(
                        pokemon.getPokemonId(), change.oldContainerId(),
                        change.oldPosition(), pokemon);
                working[change.newPosition()] = pokemonOffer;
                added.add(pokemonOffer);
                addedSlots.add(change.newPosition());
            }

            offer.replacePokemonSlots(working);
            session.resetConfirmations();
        }

        for (int index = 0; index < added.size(); index++) {
            TradePokemonOffer pokemonOffer = added.get(index);
            short slot = addedSlots.get(index);
            sendRemoveFromPersistentContainer(manager, pokemonOffer.containerId(), pokemonOffer.pokemonId());
            send(manager, new SendAddPokemonPacket(
                    pokemonOffer.pokemon(), PokemonContainerType.TRADE, slot));
            send(session.other(manager), new SendTradePokemonPacket(slot, pokemonOffer.pokemon()));
        }
        for (TradePokemonOffer pokemonOffer : removed) {
            send(manager, new SendAddPokemonPacket(pokemonOffer.pokemon()));
        }
        return !added.isEmpty() || !removed.isEmpty();
    }

    boolean isPokemonPositionOffered(CharacterManager manager, int containerId,
                                     short position, GameServerService service) {
        TradeSession session = activeSession(manager);
        if (session == null || service == null || !isPersistentContainer(containerId) || position < 0) {
            return false;
        }
        PokemonData pokemon = findPokemon(manager, containerId, position, service);
        if (pokemon == null) {
            return false;
        }
        synchronized (session) {
            return session.offer(manager).findPokemonSlot(pokemon.getPokemonId()) >= 0;
        }
    }

    boolean isPokemonOffered(CharacterManager manager, long pokemonId) {
        TradeSession session = activeSession(manager);
        if (session == null || pokemonId <= 0) {
            return false;
        }
        synchronized (session) {
            return session.offer(manager).findPokemonSlot(pokemonId) >= 0;
        }
    }

    private boolean containsPokemon(TradePokemonOffer[] offers, long pokemonId) {
        for (TradePokemonOffer offer : offers) {
            if (offer != null && offer.pokemonId() == pokemonId) {
                return true;
            }
        }
        return false;
    }

    private boolean offerPokemonAt(CharacterManager manager, int containerId, short position,
                                   short tradeSlot, GameServerService service) {
        TradeSession session = activeSession(manager);
        if (session == null || !isPersistentContainer(containerId)
                || tradeSlot < 0 || tradeSlot >= TradeSession.MAX_POKEMON) {
            return false;
        }
        int capacity = containerCapacity(manager, containerId);
        if (position < 0 || position >= capacity) {
            return false;
        }
        PokemonData pokemon = findPokemon(manager, containerId, position, service);
        if (pokemon == null || pokemon.getPokemonId() <= 0
                || !Long.valueOf(characterId(manager)).equals(pokemon.getTrainerId())) {
            return false;
        }
        synchronized (session) {
            if (session.getState() != TradeState.OPEN) {
                return false;
            }
            TradeSession.Offer offer = session.offer(manager);
            if (offer.isLocked()) {
                return false;
            }
            int existingSlot = offer.findPokemonSlot(pokemon.getPokemonId());
            TradePokemonOffer current = offer.getPokemon(tradeSlot);
            if (existingSlot >= 0) {
                return existingSlot == tradeSlot;
            }
            if (current != null) {
                return false;
            }
            offer.setPokemon(tradeSlot,
                    new TradePokemonOffer(pokemon.getPokemonId(), containerId, position, pokemon));
            session.resetConfirmations();
        }
        sendRemoveFromPersistentContainer(manager, containerId, pokemon.getPokemonId());
        send(manager, new SendAddPokemonPacket(pokemon, PokemonContainerType.TRADE, tradeSlot));
        send(session.other(manager), new SendTradePokemonPacket(tradeSlot, pokemon));
        return true;
    }

    private boolean removePokemonById(CharacterManager manager, long pokemonId) {
        TradeSession session = activeSession(manager);
        if (session == null || pokemonId <= 0) {
            return false;
        }
        TradePokemonOffer removed;
        synchronized (session) {
            if (session.getState() != TradeState.OPEN) {
                return false;
            }
            TradeSession.Offer offer = session.offer(manager);
            if (offer.isLocked()) {
                return false;
            }
            int slot = offer.findPokemonSlot(pokemonId);
            if (slot < 0) {
                return false;
            }
            removed = offer.getPokemon(slot);
            offer.removePokemon(slot);
            session.resetConfirmations();
        }
        send(manager, new SendAddPokemonPacket(removed.pokemon()));
        return true;
    }

    private boolean removePokemonAt(CharacterManager manager, short tradeSlot) {
        TradeSession session = activeSession(manager);
        if (session == null || tradeSlot < 0 || tradeSlot >= TradeSession.MAX_POKEMON) {
            return false;
        }
        TradePokemonOffer removed;
        synchronized (session) {
            if (session.getState() != TradeState.OPEN) {
                return false;
            }
            TradeSession.Offer offer = session.offer(manager);
            if (offer.isLocked()) {
                return false;
            }
            removed = offer.getPokemon(tradeSlot);
            if (removed == null) {
                return false;
            }
            offer.removePokemon(tradeSlot);
            session.resetConfirmations();
        }
        send(manager, new SendAddPokemonPacket(removed.pokemon()));
        return true;
    }

    private PokemonData findPokemon(CharacterManager manager, int containerId, short position,
                                    GameServerService service) {
        if (containerId == PokemonContainerType.PARTY.getType()) {
            PokemonData[] party = manager.getPartyPokemons();
            return position < party.length ? party[position] : null;
        }
        if (containerId != PokemonContainerType.PC.getType()) {
            return null;
        }
        ContainerRecord pc = service.getContainerByType(PokemonContainerType.PC);
        if (pc == null) {
            return null;
        }
        return service.getCharacterContainerPokemons(characterId(manager), pc).stream()
                .filter(pokemon -> pokemon.getContainerPosition() == position)
                .findFirst().orElse(null);
    }

    private int containerCapacity(CharacterManager manager, int containerId) {
        if (containerId == PokemonContainerType.PARTY.getType()) {
            return PokemonContainerType.PARTY.getSize();
        }
        if (containerId == PokemonContainerType.PC.getType()) {
            CharacterData data = manager.getCharacterData();
            int expansion = data == null ? 0 : Math.max(0, data.getPcBoxExpansionNumber());
            return PokemonContainerType.PC.getSize() + expansion * 60;
        }
        return -1;
    }

    private int firstFreePokemonSlot(TradeSession.Offer offer) {
        for (int slot = 0; slot < TradeSession.MAX_POKEMON; slot++) {
            if (offer.getPokemon(slot) == null) {
                return slot;
            }
        }
        return -1;
    }

    private boolean isPersistentContainer(int containerId) {
        return containerId == PokemonContainerType.PC.getType()
                || containerId == PokemonContainerType.PARTY.getType();
    }

    private void sendRemoveFromPersistentContainer(
            CharacterManager manager, int containerId, long pokemonId) {
        if (!isPersistentContainer(containerId) || pokemonId <= 0) {
            log.warn("跳过非法宝可梦删除通知: characterId={}, containerId={}, pokemonId={}",
                    characterId(manager), containerId, pokemonId);
            return;
        }
        PokemonContainerType containerType = containerId == PokemonContainerType.PC.getType()
                ? PokemonContainerType.PC : PokemonContainerType.PARTY;
        send(manager, new SendRemovePokemonPacket(containerType, pokemonId));
    }
}
