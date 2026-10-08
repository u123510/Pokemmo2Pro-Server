package org.pokemmo.gameserver.game.trade;

import lombok.extern.slf4j.Slf4j;
import org.pokemmo.gameserver.game.character.CharacterManager;
import org.pokemmo.gameserver.game.character.PlayerVisibilityService;
import org.pokemmo.gameserver.game.interact.InteractType;
import org.pokemmo.gameserver.protocol.packets.s2c.SendTradeStatusPacket;
import org.pokemmo.gameserver.protocol.packets.s2c.SendTradeWindowPacket;

import static org.pokemmo.gameserver.game.trade.TradeSessionRegistry.ACTIVE_BY_CHARACTER;
import static org.pokemmo.gameserver.game.trade.TradeSessionRegistry.PENDING_BY_TARGET;
import static org.pokemmo.gameserver.game.trade.TradeSessionRegistry.PENDING_TARGET_BY_REQUESTER;
import static org.pokemmo.gameserver.game.trade.TradeSessionRegistry.REQUEST_LOCK;
import static org.pokemmo.gameserver.game.trade.TradeSessionRegistry.characterId;
import static org.pokemmo.gameserver.game.trade.TradeSessionRegistry.clearInteraction;
import static org.pokemmo.gameserver.game.trade.TradeSessionRegistry.isUsable;
import static org.pokemmo.gameserver.game.trade.TradeSessionRegistry.sameLocation;
import static org.pokemmo.gameserver.game.trade.TradeSessionRegistry.schedulePendingExpiry;
import static org.pokemmo.gameserver.game.trade.TradeSessionRegistry.send;

/** Owns pending trade requests and creates a session after target acceptance. */
@Slf4j
public final class TradeRequestRegistry {
    public boolean register(CharacterManager requester, CharacterManager target) {
        long requesterId = characterId(requester);
        long targetId = characterId(target);
        if (requesterId <= 0 || targetId <= 0 || requesterId == targetId
                || !isUsable(requester) || !isUsable(target)
                || !sameLocation(requester, target) || !PlayerVisibilityService.mutuallyVisible(requester, target)
                || requester.getBattleManager() != null || target.getBattleManager() != null
                || requester.getInteractManager().getInteractType() != InteractType.NONE
                || target.getInteractManager().getInteractType() != InteractType.NONE) {
            return false;
        }
        synchronized (REQUEST_LOCK) {
            if (ACTIVE_BY_CHARACTER.containsKey(requesterId)
                    || ACTIVE_BY_CHARACTER.containsKey(targetId)
                    || PENDING_TARGET_BY_REQUESTER.containsKey(requesterId)
                    || PENDING_TARGET_BY_REQUESTER.containsKey(targetId)
                    || PENDING_BY_TARGET.containsKey(requesterId)
                    || PENDING_BY_TARGET.containsKey(targetId) || !PlayerVisibilityService.mutuallyVisible(requester, target)) {
                return false;
            }
            PENDING_BY_TARGET.put(targetId, requester);
            PENDING_TARGET_BY_REQUESTER.put(requesterId, target);
            target.getInteractManager().setInteractType(InteractType.TRADE_REQUEST);
        }
        schedulePendingExpiry(() -> expire(requester, target));
        return true;
    }

    /** Client request decision: {@code 0} declines and {@code 1} accepts. */
    public boolean decide(CharacterManager target, byte type) {
        long targetId = characterId(target);
        if (targetId <= 0) {
            return false;
        }
        CharacterManager requester;
        synchronized (REQUEST_LOCK) {
            requester = PENDING_BY_TARGET.remove(targetId);
            if (requester != null) {
                PENDING_TARGET_BY_REQUESTER.remove(characterId(requester), target);
            }
        }
        if (requester == null) {
            return false;
        }
        clearInteraction(target);
        if (type == 0) {
            send(requester, new SendTradeStatusPacket(TradeStatusType.CANCEL));
            return true;
        }
        if (type != 1) {
            send(requester, new SendTradeStatusPacket(TradeStatusType.CANCEL));
            send(target, new SendTradeStatusPacket(TradeStatusType.CANCEL));
            return false;
        }

        long requesterId = characterId(requester);
        if (requesterId <= 0 || !isUsable(requester) || !isUsable(target)
                || !sameLocation(requester, target) || !PlayerVisibilityService.mutuallyVisible(requester, target)
                || requester.getBattleManager() != null || target.getBattleManager() != null
                || requester.getInteractManager().getInteractType() != InteractType.NONE
                || ACTIVE_BY_CHARACTER.containsKey(requesterId)
                || ACTIVE_BY_CHARACTER.containsKey(targetId)) {
            send(requester, new SendTradeStatusPacket(TradeStatusType.CANCEL));
            send(target, new SendTradeStatusPacket(TradeStatusType.CANCEL));
            return false;
        }

        TradeSession session = new TradeSession(requester, target);
        synchronized (REQUEST_LOCK) {
            if (ACTIVE_BY_CHARACTER.containsKey(requesterId)
                    || ACTIVE_BY_CHARACTER.containsKey(targetId) || !PlayerVisibilityService.mutuallyVisible(requester, target)) {
                send(requester, new SendTradeStatusPacket(TradeStatusType.CANCEL));
                return false;
            }
            ACTIVE_BY_CHARACTER.put(requesterId, session);
            ACTIVE_BY_CHARACTER.put(targetId, session);
            requester.setTradeSession(session);
            target.setTradeSession(session);
        }

        send(requester, new SendTradeWindowPacket((byte) 3, (byte) 0,
                target.getCharacterData().getPlayerEntity().getPlayerName()));
        send(target, new SendTradeWindowPacket((byte) 3, (byte) 1,
                requester.getCharacterData().getPlayerEntity().getPlayerName()));
        return true;
    }

    void cancelPendingFor(CharacterManager manager) {
        long managerId = characterId(manager);
        if (managerId <= 0) {
            return;
        }
        CharacterManager requester;
        CharacterManager target;
        synchronized (REQUEST_LOCK) {
            requester = PENDING_BY_TARGET.remove(managerId);
            if (requester != null) {
                PENDING_TARGET_BY_REQUESTER.remove(characterId(requester), manager);
            }
            target = PENDING_TARGET_BY_REQUESTER.remove(managerId);
            if (target != null) {
                PENDING_BY_TARGET.remove(characterId(target), manager);
            }
        }
        if (requester != null) {
            clearInteraction(manager);
            send(requester, new SendTradeStatusPacket(TradeStatusType.CANCEL));
        }
        if (target != null) {
            clearInteraction(target);
            send(target, new SendTradeStatusPacket(TradeStatusType.CANCEL));
        }
    }

    private void expire(CharacterManager requester, CharacterManager target) {
        long requesterId = characterId(requester);
        long targetId = characterId(target);
        if (requesterId <= 0 || targetId <= 0) {
            return;
        }
        boolean expired = false;
        synchronized (REQUEST_LOCK) {
            if (PENDING_BY_TARGET.get(targetId) == requester
                    && PENDING_TARGET_BY_REQUESTER.get(requesterId) == target) {
                PENDING_BY_TARGET.remove(targetId, requester);
                PENDING_TARGET_BY_REQUESTER.remove(requesterId, target);
                expired = true;
            }
        }
        if (!expired) {
            return;
        }
        clearInteraction(target);
        send(requester, new SendTradeStatusPacket(TradeStatusType.CANCEL));
        send(target, new SendTradeStatusPacket(TradeStatusType.CANCEL));
        log.debug("交易请求超时取消: requesterId={}, targetId={}", requesterId, targetId);
    }
}
