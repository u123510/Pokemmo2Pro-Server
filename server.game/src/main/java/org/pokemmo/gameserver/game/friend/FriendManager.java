package org.pokemmo.gameserver.game.friend;

import lombok.extern.slf4j.Slf4j;
import org.pokemmo.gameserver.game.character.CharacterManager;
import org.pokemmo.gameserver.game.interact.GameInteractionType;
import org.pokemmo.gameserver.game.interact.InteractType;
import org.pokemmo.gameserver.game.script.InteractScript;
import org.pokemmo.gameserver.game.pool.GameSessionPool;
import org.pokemmo.gameserver.game.trade.TradeManager;
import org.pokemmo.gameserver.protocol.packets.s2c.SendFriendListPacket;
import org.pokemmo.gameserver.services.GameServerService;
import org.server.Session;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/** Coordinates pending friend requests independently from persisted friendships. */
@Slf4j
public final class FriendManager {
    private static final Map<Long, PendingFriendRequest> PENDING_BY_TARGET = new ConcurrentHashMap<>();
    private static final Map<Long, PendingFriendRequest> PENDING_BY_REQUESTER = new ConcurrentHashMap<>();
    private static final Object REQUEST_LOCK = new Object();
    private static final long REQUEST_TIMEOUT_MILLIS = 30_000L;
    private static final ScheduledExecutorService REQUEST_TIMEOUT_EXECUTOR =
            Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
                @Override
                public Thread newThread(Runnable runnable) {
                    Thread thread = new Thread(runnable, "friend-request-timeout");
                    thread.setDaemon(true);
                    return thread;
                }
            });

    private FriendManager() {
    }

    public record PendingFriendRequest(
            CharacterManager requester,
            CharacterManager target,
            InteractScript script) {
    }

    /** Registers a request and returns the exact interaction script sent to the target. */
    public static PendingFriendRequest registerRequest(
            CharacterManager requester, CharacterManager target) {
        long requesterId = characterId(requester);
        long targetId = characterId(target);
        if (requesterId <= 0 || targetId <= 0 || requesterId == targetId
                || !isUsable(requester) || !isUsable(target)
                || requester.getBattleManager() != null || target.getBattleManager() != null
                || TradeManager.isInTrade(requester) || TradeManager.isInTrade(target)
                || requester.getInteractManager().getInteractType() != InteractType.NONE
                || target.getInteractManager().getInteractType() != InteractType.NONE) {
            return null;
        }

        String requesterName = requester.getCharacterData().getPlayerEntity().getPlayerName();
        if (requesterName == null || requesterName.isBlank()
                || requesterName.length() > 32
                || requesterName.chars().anyMatch(Character::isISOControl)) {
            return null;
        }

        InteractScript script = new InteractScript(
                "Scene", GameInteractionType.REQUEST_FRIEND, 0, 0, 0);
        script.setInteractPlayerName(requesterName);
        PendingFriendRequest request = new PendingFriendRequest(requester, target, script);
        synchronized (REQUEST_LOCK) {
            if (PENDING_BY_TARGET.containsKey(targetId)
                    || PENDING_BY_REQUESTER.containsKey(requesterId)
                    || PENDING_BY_TARGET.containsKey(requesterId)
                    || PENDING_BY_REQUESTER.containsKey(targetId)
                    || TradeManager.isInTrade(requester) || TradeManager.isInTrade(target)) {
                return null;
            }
            PENDING_BY_TARGET.put(targetId, request);
            PENDING_BY_REQUESTER.put(requesterId, request);
            target.getInteractManager().setInteractType(InteractType.FRIEND_REQUEST);
            target.getInteractManager().setCurrentInteractScript(script);
            target.getInteractManager().setCurrentScript(null);
            target.getInteractManager().setLastInteractorEntityId(-1L);
        }

        REQUEST_TIMEOUT_EXECUTOR.schedule(
                () -> expireRequest(request), REQUEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        return request;
    }

    /** Client decision: 0 declines, 1 accepts. */
    public static boolean handleRequestDecision(
            CharacterManager target, byte type, GameServerService service) {
        long targetId = characterId(target);
        if (targetId <= 0 || service == null) {
            return false;
        }

        PendingFriendRequest request;
        synchronized (REQUEST_LOCK) {
            request = PENDING_BY_TARGET.remove(targetId);
            if (request != null) {
                PENDING_BY_REQUESTER.remove(characterId(request.requester()), request);
            }
        }
        if (request == null) {
            return false;
        }

        clearInteraction(target);
        if (type == 0) {
            return true;
        }
        if (type != 1) {
            return false;
        }

        CharacterManager requester = request.requester();
        long requesterId = characterId(requester);
        if (requesterId <= 0 || !isUsable(requester) || !isUsable(target)
                || requester.getBattleManager() != null || target.getBattleManager() != null
                || TradeManager.isInTrade(requester) || TradeManager.isInTrade(target)
                || requester.getInteractManager().getInteractType() != InteractType.NONE
                || target.getInteractManager().getInteractType() != InteractType.NONE) {
            return false;
        }

        if (!service.addFriendPair(requesterId, targetId)) {
            log.warn("好友请求接受后关系写入失败: requesterId={}, targetId={}", requesterId, targetId);
            return false;
        }
        send(requester, new SendFriendListPacket(service.getFriendList(requesterId)));
        send(target, new SendFriendListPacket(service.getFriendList(targetId)));
        return true;
    }

    /** Removes all pending requests involving a disconnected or replaced session. */
    public static void cancelFor(CharacterManager manager) {
        long managerId = characterId(manager);
        if (managerId <= 0) {
            return;
        }
        PendingFriendRequest request;
        synchronized (REQUEST_LOCK) {
            request = PENDING_BY_TARGET.remove(managerId);
            if (request == null) {
                request = PENDING_BY_REQUESTER.remove(managerId);
                if (request != null) {
                    PENDING_BY_TARGET.remove(characterId(request.target()), request);
                }
            } else {
                PENDING_BY_REQUESTER.remove(characterId(request.requester()), request);
            }
        }
        if (request == null) {
            return;
        }
        clearInteraction(request.target());
    }

    private static void expireRequest(PendingFriendRequest request) {
        long requesterId = characterId(request.requester());
        long targetId = characterId(request.target());
        boolean expired;
        synchronized (REQUEST_LOCK) {
            expired = PENDING_BY_TARGET.remove(targetId, request)
                    && PENDING_BY_REQUESTER.remove(requesterId, request);
        }
        if (!expired) {
            return;
        }
        clearInteraction(request.target());
        log.debug("好友请求超时清理: requesterId={}, targetId={}", requesterId, targetId);
    }

    private static void clearInteraction(CharacterManager manager) {
        if (manager == null || manager.getInteractManager() == null) {
            return;
        }
        manager.getInteractManager().setCurrentInteractScript(null);
        manager.getInteractManager().setCurrentScript(null);
        manager.getInteractManager().clearLastInteractorEntityId();
        manager.getInteractManager().setInteractType(InteractType.NONE);
    }

    private static long characterId(CharacterManager manager) {
        if (manager == null || manager.getCharacterData() == null
                || manager.getCharacterData().getPlayerEntity() == null) {
            return 0L;
        }
        return manager.getCharacterData().getPlayerEntity().getEntityGameId();
    }

    private static boolean isUsable(CharacterManager manager) {
        Session session = manager == null ? null : manager.getCharacterSession();
        return session != null && session.isActive()
                && GameSessionPool.getPlayerSessionInPool(characterId(manager)) == session
                && manager.getCharacterData() != null
                && manager.getCharacterData().getPlayerEntity() != null;
    }

    private static void send(CharacterManager manager, Object packet) {
        Session session = manager == null ? null : manager.getCharacterSession();
        if (session != null && session.isActive() && packet instanceof org.server.Packet value) {
            session.send(value);
        }
    }
}
