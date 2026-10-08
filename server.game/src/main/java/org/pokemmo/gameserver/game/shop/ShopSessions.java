package org.pokemmo.gameserver.game.shop;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import io.netty.util.AttributeKey;
import org.pokemmo.gameserver.game.character.CharacterManager;
import org.pokemmo.gameserver.game.interact.InteractType;
import org.pokemmo.gameserver.protocol.GameProtocol;
import org.pokemmo.gameserver.protocol.packets.s2c.SendItemShopPacket;
import org.pokemmo.gameserver.protocol.packets.s2c.SendShopControlPacket;
import org.server.Session;

/** Per-connection capabilities/quotes and centralized idempotent shop cleanup. */
public final class ShopSessions {
    private static final AttributeKey<Integer> VERSION = AttributeKey.valueOf("npc_shop_protocol_version");
    private static final AttributeKey<ShopSession> QUOTE = AttributeKey.valueOf("npc_shop_quote");
    private static final Set<Session> ACTIVE = ConcurrentHashMap.newKeySet();

    private ShopSessions() {
    }

    public static boolean supported(Session session) {
        return Integer.valueOf(1).equals(session.attr(VERSION).get());
    }

    public static void negotiate(Session session) {
        session.attr(VERSION).set(1);
        session.send(SendShopControlPacket.capabilities());
    }

    static ShopSession get(Session session) {
        return session.attr(QUOTE).get();
    }

    static void open(Session session, ShopSession quote) {
        session.attr(QUOTE).set(quote);
        ACTIVE.add(session);
        quote.manager.getInteractManager().setInteractType(InteractType.SHOP);
    }

    public static boolean close(Session session, String reason, boolean notifyClient) {
        if (session == null) return false;
        while (true) {
            ShopSession existing = get(session);
            CharacterManager owner = existing == null
                    ? session.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get() : existing.manager;
            if (owner == null) return false;
            // Also serialize with an opening shop that has not installed its quote yet.
            synchronized (owner.getInteractManager()) {
                ShopSession quote = get(session);
                if (quote == null) return false;
                if (quote.manager != owner) continue;
                session.attr(QUOTE).set(null);
                ACTIVE.remove(session);
                if (owner.getCharacterSession() == session
                        && owner.getInteractManager().getInteractType() == InteractType.SHOP) {
                    owner.getInteractManager().setInteractType(InteractType.NONE);
                }
                if (notifyClient && session.isActive()) {
                    session.send(SendShopControlPacket.closed(quote.quoteId, reason), SendItemShopPacket.closed());
                }
                return true;
            }
        }
    }

    public static void closeRequested(Session session, long quoteId) {
        ShopSession quote = get(session);
        if (quote == null) return;
        synchronized (quote.manager.getInteractManager()) {
            if (get(session) == quote && quote.quoteId == quoteId) {
                close(session, "商店已关闭", true);
            }
        }
    }

    static int closeOutdated(long version) {
        int count = 0;
        for (Session session : ACTIVE) {
            ShopSession quote = get(session);
            if (quote == null) continue;
            synchronized (quote.manager.getInteractManager()) {
                if (get(session) == quote && quote.catalogVersion != version
                        && close(session, "店铺配置已更新，请重新打开商店", true)) {
                    count++;
                }
            }
        }
        return count;
    }

    public static int closeForNpc(long npcId) {
        int count = 0;
        for (Session session : ACTIVE) {
            ShopSession quote = get(session);
            if (quote == null || quote.npcId != npcId) continue;
            synchronized (quote.manager.getInteractManager()) {
                if (get(session) == quote && close(session, "店员已被删除，商店已关闭", true)) count++;
            }
        }
        return count;
    }
}
