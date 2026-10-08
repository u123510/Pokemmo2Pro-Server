package org.pokemmo.gameserver.game.pool;

import org.pokemmo.gameserver.game.battle.BattleManager;
import org.pokemmo.gameserver.game.kick.KickType;
import org.pokemmo.gameserver.protocol.packets.s2c.SendKickInGamePacket;
import org.server.Session;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class GameSessionPool {
    //全服所有连接到游戏服的会话集合（含选角中与已进世界的会话）
    private static final Set<Session> allConnectedSessions = ConcurrentHashMap.newKeySet();
    //全服玩家的会话池
    private static final ConcurrentHashMap<Long, Session> playerSessionPool = new ConcurrentHashMap<>();
    //全服玩家的战斗管理器池
    private static final ConcurrentHashMap<Long, BattleManager> battleManagerPool = new ConcurrentHashMap<>();

    public static void registerSession(Session session) {
        if (session != null) {
            allConnectedSessions.add(session);
        }
    }

    public static void unregisterSession(Session session) {
        if (session != null) {
            allConnectedSessions.remove(session);
        }
    }

    public static Set<Session> getAllConnectedSessions() {
        return Collections.unmodifiableSet(allConnectedSessions);
    }

    public static void broadcastShutdown() {
        SendKickInGamePacket packet = new SendKickInGamePacket(KickType.SERVER_SHUTDOWN);
        Set<Session> targets = new HashSet<>(allConnectedSessions);
        targets.addAll(playerSessionPool.values());
        for (Session session : targets) {
            if (session != null && session.isActive()) {
                try {
                    session.send(packet);
                } catch (Exception ignored) {
                }
            }
        }
    }
    public static void addPlayerSessionInPool(long playerId, Session session) {
        playerSessionPool.put(playerId, session);
    }
    public static Session getPlayerSessionInPool(long playerId) {
        return playerSessionPool.get(playerId);
    }
    public static void removePlayerSessionInPool(long playerId) {
        playerSessionPool.remove(playerId);
    }
    public static void removePlayerSessionInPool(long playerId, Session expectedSession) {
        playerSessionPool.remove(playerId, expectedSession);
    }
    public static BattleManager getBattleManagerInPool(long hostPlayerId) {
        return battleManagerPool.get(hostPlayerId);
    }
    public static void addBattleManagerInPool(long hostPlayerId, BattleManager battleManager) {
        battleManagerPool.put(hostPlayerId, battleManager);
    }
    public static void removeBattleManagerInPool(long hostPlayerId) {
        battleManagerPool.remove(hostPlayerId);
    }

    public static void removeBattleManagerInPool(BattleManager battleManager) {
        if (battleManager == null) {
            return;
        }
        battleManagerPool.entrySet().removeIf(entry -> entry.getValue() == battleManager);
    }
}
