package org.pokemmo.gameserver.game.battle;

import lombok.extern.slf4j.Slf4j;
import org.pokemmo.gameserver.game.character.CharacterData;
import org.pokemmo.gameserver.game.character.CharacterManager;
import org.pokemmo.gameserver.game.character.PlayerVisibilityService;
import org.pokemmo.gameserver.game.interact.GameInteractionType;
import org.pokemmo.gameserver.game.interact.InteractType;
import org.pokemmo.gameserver.game.pokemon.PokemonData;
import org.pokemmo.gameserver.game.pool.GameSessionPool;
import org.pokemmo.gameserver.game.script.InteractScript;
import org.pokemmo.gameserver.game.trade.TradeManager;
import org.server.Session;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/** Coordinates pending player-versus-player battle requests. */
@Slf4j
public final class BattleRequestManager {
    private static final Map<Long, PendingBattleRequest> PENDING_BY_TARGET = new ConcurrentHashMap<>();
    private static final Map<Long, PendingBattleRequest> PENDING_BY_REQUESTER = new ConcurrentHashMap<>();
    private static final Object REQUEST_LOCK = new Object();
    private static final long REQUEST_TIMEOUT_MILLIS = 30_000L;
    private static final ScheduledExecutorService REQUEST_TIMEOUT_EXECUTOR =
            Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
                @Override
                public Thread newThread(Runnable runnable) {
                    Thread thread = new Thread(runnable, "battle-request-timeout");
                    thread.setDaemon(true);
                    return thread;
                }
            });

    private BattleRequestManager() {
    }

    public record PendingBattleRequest(CharacterManager requester,
                                       CharacterManager target,
                                       BattleFormatType battleFormatType,
                                       InteractScript script) {
    }

    public static PendingBattleRequest registerRequest(CharacterManager requester,
                                                        CharacterManager target,
                                                        BattleFormatType battleFormatType) {
        long requesterId = characterId(requester);
        long targetId = characterId(target);
        if (battleFormatType != BattleFormatType.SINGLE_BATTLE
                || requesterId <= 0 || targetId <= 0 || requesterId == targetId
                || !isUsable(requester) || !isUsable(target)
                || !sameLocation(requester, target) || !PlayerVisibilityService.mutuallyVisible(requester, target)
                || !hasAliveParty(requester) || !hasAliveParty(target)
                || isBusy(requester) || isBusy(target)) {
            return null;
        }

        String requesterName = requester.getCharacterData().getPlayerEntity().getPlayerName();
        if (!isValidPlayerName(requesterName)) {
            return null;
        }

        InteractScript script = new InteractScript("Scene", GameInteractionType.REQUEST_BATTLE, 0, 0, 0);
        script.setInteractPlayerName(requesterName);
        PendingBattleRequest request = new PendingBattleRequest(requester, target, battleFormatType, script);
        synchronized (REQUEST_LOCK) {
            if (PENDING_BY_TARGET.containsKey(targetId)
                    || PENDING_BY_REQUESTER.containsKey(requesterId)
                    || PENDING_BY_TARGET.containsKey(requesterId)
                    || PENDING_BY_REQUESTER.containsKey(targetId)
                    || !PlayerVisibilityService.mutuallyVisible(requester, target)) {
                return null;
            }
            PENDING_BY_TARGET.put(targetId, request);
            PENDING_BY_REQUESTER.put(requesterId, request);
            target.getInteractManager().setInteractType(InteractType.BATTLE_REQUEST);
            target.getInteractManager().setCurrentInteractScript(script);
            target.getInteractManager().setCurrentScript(null);
            target.getInteractManager().setLastInteractorEntityId(-1L);
        }
        REQUEST_TIMEOUT_EXECUTOR.schedule(() -> expireRequest(request),
                REQUEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        return request;
    }

    /** Client decision: 0 declines, 1 accepts. */
    public static boolean handleRequestDecision(CharacterManager target, byte type) {
        long targetId = characterId(target);
        PendingBattleRequest request;
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
        if (!isUsable(requester) || !isUsable(target)
                || !sameLocation(requester, target) || !PlayerVisibilityService.mutuallyVisible(requester, target)
                || !hasAliveParty(requester) || !hasAliveParty(target)
                || isBusy(requester) || isBusy(target)) {
            return false;
        }

        BattleManager battleManager;
        synchronized (REQUEST_LOCK) {
            if (PENDING_BY_TARGET.containsKey(targetId)
                    || PENDING_BY_REQUESTER.containsKey(targetId)
                    || PENDING_BY_TARGET.containsKey(characterId(requester))
                    || PENDING_BY_REQUESTER.containsKey(characterId(requester))
                    || isBusy(requester) || isBusy(target) || !PlayerVisibilityService.mutuallyVisible(requester, target)) {
                return false;
            }
            battleManager = BattleGenerator.generatorPlayerBattle(
                    requester.getCharacterSession(), target.getCharacterSession(),
                    requester.getPartyPokemons(), target.getPartyPokemons(), request.battleFormatType());
            if (battleManager == null) {
                return false;
            }
            requester.setBattleManager(battleManager);
            target.setBattleManager(battleManager);
            GameSessionPool.addBattleManagerInPool(characterId(requester), battleManager);
            GameSessionPool.addBattleManagerInPool(characterId(target), battleManager);
        }
        battleManager.handleBattleBegin(requester.getCharacterSession(), false, false);
        battleManager.handleBattleBegin(target.getCharacterSession(), false, false);
        battleManager.broadcastPlayerBattleStatus((byte) 2);
        return true;
    }

    public static void cancelFor(CharacterManager manager) {
        long managerId = characterId(manager);
        if (managerId <= 0) {
            return;
        }
        PendingBattleRequest request;
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
        if (request != null) {
            clearInteraction(request.target());
        }
    }

    private static void expireRequest(PendingBattleRequest request) {
        long requesterId = characterId(request.requester());
        long targetId = characterId(request.target());
        boolean expired;
        synchronized (REQUEST_LOCK) {
            expired = PENDING_BY_TARGET.remove(targetId, request)
                    && PENDING_BY_REQUESTER.remove(requesterId, request);
        }
        if (expired) {
            clearInteraction(request.target());
            log.debug("单挑请求超时清理: requesterId={}, targetId={}", requesterId, targetId);
        }
    }

    private static boolean isBusy(CharacterManager manager) {
        return manager.getBattleManager() != null
                || manager.getInteractManager().getInteractType() != InteractType.NONE
                || TradeManager.isInTrade(manager);
    }

    private static boolean hasAliveParty(CharacterManager manager) {
        for (PokemonData pokemon : manager.getPartyPokemons()) {
            if (pokemon != null && pokemon.getCurrentHp() > 0) {
                return true;
            }
        }
        return false;
    }

    private static boolean sameLocation(CharacterManager first, CharacterManager second) {
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

    private static boolean isUsable(CharacterManager manager) {
        Session session = manager == null ? null : manager.getCharacterSession();
        return session != null && session.isActive()
                && GameSessionPool.getPlayerSessionInPool(characterId(manager)) == session
                && manager.getCharacterData() != null
                && manager.getCharacterData().getPlayerEntity() != null;
    }

    private static boolean isValidPlayerName(String name) {
        return name != null && !name.isBlank() && name.length() <= 32
                && name.chars().noneMatch(Character::isISOControl);
    }

    private static void clearInteraction(CharacterManager manager) {
        if (manager == null || manager.getInteractManager() == null) {
            return;
        }
        if (manager.getInteractManager().getInteractType() != InteractType.BATTLE_REQUEST) {
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

}
