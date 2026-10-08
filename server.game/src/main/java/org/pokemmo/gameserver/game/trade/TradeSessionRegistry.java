package org.pokemmo.gameserver.game.trade;

import org.pokemmo.gameserver.game.character.CharacterData;
import org.pokemmo.gameserver.game.character.CharacterManager;
import org.pokemmo.gameserver.game.interact.InteractType;
import org.server.Packet;
import org.server.Session;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/** Shared in-memory indexes and connection helpers for two-player trade sessions. */
final class TradeSessionRegistry {
    static final Map<Long, CharacterManager> PENDING_BY_TARGET = new ConcurrentHashMap<>();
    static final Map<Long, CharacterManager> PENDING_TARGET_BY_REQUESTER = new ConcurrentHashMap<>();
    static final Map<Long, TradeSession> ACTIVE_BY_CHARACTER = new ConcurrentHashMap<>();
    static final Object REQUEST_LOCK = new Object();
    private static final ScheduledExecutorService REQUEST_TIMEOUT_EXECUTOR =
            Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
                @Override
                public Thread newThread(Runnable runnable) {
                    Thread thread = new Thread(runnable, "trade-request-timeout");
                    thread.setDaemon(true);
                    return thread;
                }
            });

    private TradeSessionRegistry() {
    }

    static void schedulePendingExpiry(Runnable task) {
        REQUEST_TIMEOUT_EXECUTOR.schedule(task, 30, TimeUnit.SECONDS);
    }

    static TradeSession activeSession(CharacterManager manager) {
        long id = characterId(manager);
        TradeSession session = ACTIVE_BY_CHARACTER.get(id);
        return session != null && session.contains(manager) ? session : null;
    }

    static void close(TradeSession session) {
        if (!session.markClosed()) {
            return;
        }
        synchronized (REQUEST_LOCK) {
            ACTIVE_BY_CHARACTER.remove(characterId(session.getRequester()), session);
            ACTIVE_BY_CHARACTER.remove(characterId(session.getTarget()), session);
        }
        session.getRequester().setTradeSession(null);
        session.getTarget().setTradeSession(null);
        clearInteraction(session.getRequester());
        clearInteraction(session.getTarget());
    }

    static void broadcast(TradeSession session, Packet packet) {
        send(session.getRequester(), packet);
        send(session.getTarget(), packet);
    }

    static void clearInteraction(CharacterManager manager) {
        if (manager == null || manager.getInteractManager() == null) {
            return;
        }
        manager.getInteractManager().setInteractType(InteractType.NONE);
        manager.getInteractManager().setCurrentInteractScript(null);
        manager.getInteractManager().setCurrentScript(null);
        manager.getInteractManager().clearLastInteractorEntityId();
    }

    static void send(CharacterManager manager, Packet packet) {
        if (isSessionActive(manager)) {
            manager.getCharacterSession().send(packet);
        }
    }

    static boolean isSessionActive(CharacterManager manager) {
        Session session = manager == null ? null : manager.getCharacterSession();
        return session != null && session.getChannel() != null && session.isActive();
    }

    static boolean isUsable(CharacterManager manager) {
        return manager != null && manager.getCharacterData() != null
                && manager.getCharacterData().getPlayerEntity() != null
                && isSessionActive(manager);
    }

    static boolean sameLocation(CharacterManager first, CharacterManager second) {
        if (first == null || second == null || first.getCharacterData() == null
                || second.getCharacterData() == null
                || first.getCharacterData().getPlayerEntity() == null
                || second.getCharacterData().getPlayerEntity() == null) {
            return false;
        }
        CharacterData firstData = first.getCharacterData();
        CharacterData secondData = second.getCharacterData();
        return firstData.getChannel() == secondData.getChannel()
                && firstData.getPlayerEntity().getRegionIndexId()
                == secondData.getPlayerEntity().getRegionIndexId()
                && firstData.getPlayerEntity().getMapHeaderIdOrGbaMapGroupId()
                == secondData.getPlayerEntity().getMapHeaderIdOrGbaMapGroupId()
                && firstData.getPlayerEntity().getGbaMapId()
                == secondData.getPlayerEntity().getGbaMapId();
    }

    static long characterId(CharacterManager manager) {
        if (manager == null || manager.getCharacterData() == null
                || manager.getCharacterData().getPlayerEntity() == null) {
            return -1;
        }
        return manager.getCharacterData().getPlayerEntity().getEntityGameId();
    }
}
