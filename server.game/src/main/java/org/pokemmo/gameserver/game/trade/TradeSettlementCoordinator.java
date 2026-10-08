package org.pokemmo.gameserver.game.trade;

import lombok.extern.slf4j.Slf4j;
import org.pokemmo.db.jooq.tables.records.ContainerRecord;
import org.pokemmo.db.jooq.tables.records.InventoryRecord;
import org.pokemmo.gameserver.game.character.CharacterData;
import org.pokemmo.gameserver.game.character.CharacterManager;
import org.pokemmo.gameserver.game.character.UpdateCharacterSelector;
import org.pokemmo.gameserver.game.container.PokemonContainerType;
import org.pokemmo.gameserver.game.pokemon.PokemonData;
import org.pokemmo.gameserver.protocol.packets.s2c.SendAddPokemonPacket;
import org.pokemmo.gameserver.protocol.packets.s2c.SendInventoryPacket;
import org.pokemmo.gameserver.protocol.packets.s2c.SendPokemonContainerPacket;
import org.pokemmo.gameserver.protocol.packets.s2c.SendTradeStatusPacket;
import org.pokemmo.gameserver.protocol.packets.s2c.SendUpdatePlayerInfo;
import org.pokemmo.gameserver.services.GameServerService;

import java.util.Arrays;
import java.util.List;

import static org.pokemmo.gameserver.game.trade.TradeSessionRegistry.ACTIVE_BY_CHARACTER;
import static org.pokemmo.gameserver.game.trade.TradeSessionRegistry.activeSession;
import static org.pokemmo.gameserver.game.trade.TradeSessionRegistry.broadcast;
import static org.pokemmo.gameserver.game.trade.TradeSessionRegistry.characterId;
import static org.pokemmo.gameserver.game.trade.TradeSessionRegistry.close;
import static org.pokemmo.gameserver.game.trade.TradeSessionRegistry.send;
import static org.pokemmo.gameserver.game.trade.TradeSessionRegistry.isSessionActive;

/** Commits confirmed trades, restores cancelled offers, and refreshes both online players. */
@Slf4j
public final class TradeSettlementCoordinator {
    private final TradeRequestRegistry requests;

    TradeSettlementCoordinator(TradeRequestRegistry requests) {
        this.requests = requests;
    }

    public boolean setLock(CharacterManager manager, byte action, GameServerService service) {
        TradeSession session = activeSession(manager);
        if (session == null || service == null) {
            return false;
        }
        if (action == 0) {
            return cancelSession(session);
        }

        boolean commit;
        TradeStatusType statusToSend = null;
        synchronized (session) {
            if (session.getState() != TradeState.OPEN) {
                return false;
            }
            TradeSession.Offer offer = session.offer(manager);
            if (action == 1) {
                offer.setLocked(true);
                offer.setConfirmed(false);
                statusToSend = TradeStatusType.LOCKED;
            } else if (action == 2) {
                TradeSession.Offer otherOffer = session.offer(session.other(manager));
                if (!offer.isLocked() || !otherOffer.isLocked()) {
                    return false;
                }
                offer.setConfirmed(true);
                statusToSend = TradeStatusType.FINAL_CONFIRM;
            } else {
                return false;
            }
            commit = session.bothConfirmed();
            if (commit) {
                session.setState(TradeState.COMMITTING);
            }
        }
        if (statusToSend != null) {
            send(session.other(manager), new SendTradeStatusPacket(statusToSend));
        }
        if (!commit) {
            return true;
        }

        GameServerService.TradeCompletionResult completion = commit(session, service);
        boolean committed = completion.success();
        synchronized (session) {
            session.setState(committed ? TradeState.COMPLETED : TradeState.CANCELLED);
        }
        if (!committed) {
            restoreAllPokemon(session);
            sendStatus(session, TradeStatusType.CANCEL);
            close(session);
            refreshOnlineState(session.getRequester(), service);
            refreshOnlineState(session.getTarget(), service);
            return false;
        }
        log.info("交易提交成功: firstId={}, secondId={}, firstReceived={}, secondReceived={}",
                characterId(session.getRequester()), characterId(session.getTarget()),
                pokemonPlacementSummary(completion.firstReceived()),
                pokemonPlacementSummary(completion.secondReceived()));
        sendStatus(session, TradeStatusType.COMPLETE);
        sendReceivedPokemon(session.getRequester(), completion.firstReceived());
        sendReceivedPokemon(session.getTarget(), completion.secondReceived());
        close(session);
        refreshOnlineState(session.getRequester(), service);
        refreshOnlineState(session.getTarget(), service);
        return true;
    }

    public void cancel(CharacterManager manager) {
        requests.cancelPendingFor(manager);
        TradeSession session = ACTIVE_BY_CHARACTER.get(characterId(manager));
        if (session != null) {
            cancelSession(session);
        }
    }

    private GameServerService.TradeCompletionResult commit(
            TradeSession session, GameServerService service) {
        TradeSession.Offer first = session.offer(0);
        TradeSession.Offer second = session.offer(1);
        long firstId = characterId(session.getRequester());
        long secondId = characterId(session.getTarget());
        log.warn("交易提交前报价: firstId={}, secondId={}, firstMoney={}, secondMoney={}, "
                        + "firstPokemonOffers={}, secondPokemonOffers={}, firstItems={}, secondItems={}",
                firstId, secondId, first.getMoney(), second.getMoney(),
                pokemonOfferSummary(first), pokemonOfferSummary(second),
                first.getItems(), second.getItems());
        return service.completeTradeResult(firstId, secondId,
                first.getMoney(), second.getMoney(), first.getPokemonIds(), second.getPokemonIds(),
                first.getItems(), second.getItems(),
                session.getRequester().getSnowflakeIdGenerator());
    }

    private boolean cancelSession(TradeSession session) {
        synchronized (session) {
            TradeState state = session.getState();
            if (state == TradeState.COMMITTING || state == TradeState.COMPLETED
                    || state == TradeState.CANCELLED) {
                return false;
            }
            session.setState(TradeState.CANCELLED);
        }
        restoreAllPokemon(session);
        sendStatus(session, TradeStatusType.CANCEL);
        close(session);
        return true;
    }

    private void sendStatus(TradeSession session, TradeStatusType status) {
        broadcast(session, new SendTradeStatusPacket(status));
    }

    private void restoreAllPokemon(TradeSession session) {
        restorePokemonOffers(session, session.getRequester());
        restorePokemonOffers(session, session.getTarget());
    }

    private void restorePokemonOffers(TradeSession session, CharacterManager manager) {
        for (TradePokemonOffer pokemonOffer : session.offer(manager).getPokemonOffers()) {
            send(manager, new SendAddPokemonPacket(pokemonOffer.pokemon()));
        }
    }

    private void sendReceivedPokemon(CharacterManager manager, List<PokemonData> pokemons) {
        if (pokemons == null) {
            return;
        }
        for (PokemonData pokemon : pokemons) {
            if (pokemon != null) {
                send(manager, new SendAddPokemonPacket(pokemon));
            }
        }
    }

    private List<String> pokemonPlacementSummary(List<PokemonData> pokemons) {
        if (pokemons == null) {
            return List.of();
        }
        return pokemons.stream()
                .filter(pokemon -> pokemon != null)
                .map(pokemon -> pokemon.getPokemonId() + "@"
                        + pokemon.getTrainerId() + ":"
                        + pokemon.getContainerId() + ":"
                        + pokemon.getContainerPosition())
                .toList();
    }

    private List<String> pokemonOfferSummary(TradeSession.Offer offer) {
        return offer.getPokemonOffers().stream()
                .map(pokemon -> pokemon.pokemonId() + "@" + pokemon.containerId()
                        + ":" + pokemon.position())
                .toList();
    }

    private void refreshOnlineState(CharacterManager manager, GameServerService service) {
        if (!isSessionActive(manager) || service == null) {
            return;
        }
        long id = characterId(manager);
        try {
            CharacterData refreshed = service.getCharacter(id);
            CharacterData current = manager.getCharacterData();
            if (refreshed != null && current != null) {
                current.setMoney(refreshed.getMoney());
                current.setPcBoxExpansionNumber(refreshed.getPcBoxExpansionNumber());
            }

            ContainerRecord party = service.getContainerByType(PokemonContainerType.PARTY);
            if (party != null) {
                List<PokemonData> partyPokemons = service.getCharacterContainerPokemons(id, party);
                Arrays.fill(manager.getPartyPokemons(), null);
                for (PokemonData pokemon : partyPokemons) {
                    short position = pokemon.getContainerPosition();
                    if (position >= 0 && position < manager.getPartyPokemons().length) {
                        manager.getPartyPokemons()[position] = pokemon;
                    }
                }
                manager.getCharacterSession().send(new SendPokemonContainerPacket(party, partyPokemons));
            }

            ContainerRecord pc = service.getContainerByType(PokemonContainerType.PC);
            if (pc != null) {
                manager.getCharacterSession().send(new SendPokemonContainerPacket(
                        pc, service.getCharacterContainerPokemons(id, pc)));
            }

            InventoryRecord inventory = service.getInventory();
            if (inventory != null) {
                manager.getCharacterSession().send(new SendInventoryPacket(
                        inventory, service.getItemsByContainerAndCharacter(id, inventory)));
            }
            if (current != null) {
                UpdateCharacterSelector selector = new UpdateCharacterSelector.Builder()
                        .setRefreshMoney(true)
                        .setRefreshBoxInfo(true)
                        .setCharacterData(current)
                        .build();
                manager.getCharacterSession().send(new SendUpdatePlayerInfo(selector));
            }
        } catch (RuntimeException exception) {
            log.warn("交易完成后刷新在线角色状态失败: characterId={}", id, exception);
        }
    }
}
