package org.pokemmo.gameserver.game.character;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.pokemmo.db.jooq.tables.records.ContainerRecord;
import org.pokemmo.db.jooq.tables.records.InventoryRecord;
import org.pokemmo.gameserver.game.account.AccountData;
import org.pokemmo.gameserver.game.badge.BadgeLevelTable;
import org.pokemmo.gameserver.game.battle.BattleManager;
import org.pokemmo.gameserver.game.building.BulidingType;
import org.pokemmo.gameserver.game.entity.NpcEntity;
import org.pokemmo.gameserver.game.entity.PlayerEntity;
import org.pokemmo.gameserver.game.entity.SportType;
import org.pokemmo.gameserver.game.events.*;
import org.pokemmo.gameserver.game.interact.InteractType;
import org.pokemmo.gameserver.game.interact.GameInteractionType;
import org.pokemmo.gameserver.game.map.*;
import org.pokemmo.gameserver.game.player.InteractManager;
import org.pokemmo.gameserver.game.player.ServerHeartBeatThread;
import org.pokemmo.gameserver.game.pokemon.PokemonData;
import org.pokemmo.gameserver.game.pokemon.PokemonManager;
import org.pokemmo.gameserver.game.pool.GameSessionPool;
import org.pokemmo.gameserver.game.region.RegionData;
import org.pokemmo.gameserver.game.region.RegionType;
import org.pokemmo.gameserver.game.script.InteractScript;
import org.pokemmo.gameserver.game.script.Script;
import org.pokemmo.gameserver.game.string.GameLocalFormatString;
import org.pokemmo.gameserver.game.string.GameMassageString;
import org.pokemmo.gameserver.game.trade.TradeManager;
import org.pokemmo.gameserver.protocol.GameProtocol;
import org.pokemmo.gameserver.protocol.packets.s2c.*;
import org.pokemmo.gameserver.script.ScriptManager;
import org.pokemmo.gameserver.services.GameServerService;
import org.pokemmo.gameserver.util.SnowflakeIdGenerator;
import org.server.Session;
import org.server.Packet;
import org.server.services.ServerService;
import org.server.union.chat.ChatMessage;
import org.server.union.chat.ChatType;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
@Slf4j
final class MapVisibilityService extends CharacterManagerComponent {
    MapVisibilityService(CharacterManagerState context) {
        super(context);
    }

    public void completeMapLoad() {
        if (context.mapLoadFuture != null && !context.mapLoadFuture.isDone()) {
            context.mapLoadFuture.complete(true);
        }
    }

    private Set<Session> collectVisiblePlayerSessions(MapData[] maps) {
        Set<Session> sessions = new LinkedHashSet<>();
        if (maps == null || context.characterData == null) {
            return sessions;
        }
        for (MapData map : maps) {
            if (map == null) {
                continue;
            }
            for (Session candidate : map.getPlayerSessionPool().values()) {
                if (candidate == null || !candidate.isActive()) {
                    continue;
                }
                CharacterManager candidateManager = candidate.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get();
                if (candidateManager == null || candidateManager.getCharacterData() == null
                        || candidateManager.getCharacterData().getPlayerEntity() == null
                        || candidateManager.getCharacterData().getChannel() != context.characterData.getChannel()) {
                    continue;
                }
                sessions.add(candidate);
            }
        }
        return sessions;
    }

    private void sendToVisiblePlayers(Packet packet, boolean includeSelf) {
        long selfId = context.characterData.getPlayerEntity().getEntityGameId();
        for (Session targetSession : collectVisiblePlayerSessions(context.currentMapDatas)) {
            CharacterManager targetManager = targetSession.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get();
            if (targetManager == null || targetManager.getCharacterData() == null
                    || targetManager.getCharacterData().getPlayerEntity() == null) {
                continue;
            }
            long targetId = targetManager.getCharacterData().getPlayerEntity().getEntityGameId();
            if (includeSelf || targetId != selfId) {
                PlayerVisibilityService.sendIfVisible((CharacterManager) context, targetSession, packet);
            }
        }
    }

    public void broadcastPlayerMovement(boolean isRun) {
        byte moveToward = context.characterData.getPlayerEntity().getToward();
        SportType sportType = switch (moveToward) {
            case 0 -> isRun ? SportType.RUN_DOWN : SportType.WALK_DOWN;
            case 1 -> isRun ? SportType.RUN_UP : SportType.WALK_UP;
            case 2 -> isRun ? SportType.RUN_LEFT : SportType.WALK_LEFT;
            case 3 -> isRun ? SportType.RUN_RIGHT : SportType.WALK_RIGHT;
            default -> null;
        };
        if (sportType == null) {
            return;
        }
        boolean isNdsType = RegionType.isNDS(
                Byte.toUnsignedInt(context.characterData.getPlayerEntity().getRegionIndexId())
        );
        // The client advances one tile from the sport action; a position packet
        // here would interrupt the animation and make movement visibly stutter.
        sendToVisiblePlayers(new SendEntitySportPacket(
                context.characterData.getPlayerEntity().getEntityGameId(),
                isNdsType,
                List.of(sportType)
        ), false);
    }

    public void broadcastPlayerTransportation() {
        if (context.characterData == null || context.characterData.getPlayerEntity() == null) {
            return;
        }
        PlayerEntity playerEntity = context.characterData.getPlayerEntity();
        SendUpdatePlayerTransportationPacket packet = new SendUpdatePlayerTransportationPacket(
                playerEntity.getEntityGameId(),
                playerEntity.getTransportation()
        );
        if (context.characterSession != null && context.characterSession.isActive()) {
            context.characterSession.send(packet);
        }
        sendToVisiblePlayers(packet, false);
    }

    public void synchronizeVisiblePlayersAfterMapLoad() {
        if (context.characterData == null || context.characterData.getPlayerEntity() == null
                || context.characterSession == null || !context.characterSession.isActive()) {
            return;
        }
        long selfId = context.characterData.getPlayerEntity().getEntityGameId();
        Set<Long> sentPlayerIds = new HashSet<>();
        for (Session targetSession : collectVisiblePlayerSessions(context.currentMapDatas)) {
            CharacterManager targetManager = targetSession.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get();
            if (targetManager == null || targetManager.getCharacterData() == null
                    || targetManager.getCharacterData().getPlayerEntity() == null) {
                continue;
            }
            CharacterData targetData = targetManager.getCharacterData();
            long targetId = targetData.getPlayerEntity().getEntityGameId();
            if (targetId == selfId || !sentPlayerIds.add(targetId)) {
                continue;
            }
            PlayerVisibilityService.sendPlayer(targetManager, context.characterSession);
            PlayerVisibilityService.sendPlayer((CharacterManager) context, targetSession);
        }
    }

    public void broadcastPlayerToward() {
        sendToVisiblePlayers(new SendSetEntityTowardPacket(
                context.characterData.getPlayerEntity().getEntityGameId(),
                context.characterData.getPlayerEntity().getToward()
        ), false);
    }

    public void broadcastPlayerPosition() {
        if (context.characterData == null || context.characterData.getPlayerEntity() == null) {
            return;
        }
        SendSetEntityPosPacket packet = new SendSetEntityPosPacket(context.characterData.getPlayerEntity());
        if (context.characterSession != null && context.characterSession.isActive()) {
            context.characterSession.send(packet);
        }
        sendToVisiblePlayers(packet, false);
    }

    public void notifyMapTransition(MapData[] previousVisibleMaps, MapData previousPrimaryMap) {
        MapData currentPrimaryMap = context.currentMapDatas[MapConnectionType.NOTHING.getType()];
        if (previousPrimaryMap == null || currentPrimaryMap == null || previousPrimaryMap.equals(currentPrimaryMap)) {
            return;
        }
        long characterId = context.characterData.getPlayerEntity().getEntityGameId();
        for (Session targetSession : collectVisiblePlayerSessions(previousVisibleMaps)) {
            CharacterManager targetManager = targetSession.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get();
            if (targetManager != null && targetManager.getCharacterData() != null
                    && targetManager.getCharacterData().getPlayerEntity() != null
                    && targetManager.getCharacterData().getPlayerEntity().getEntityGameId() != characterId) {
                PlayerVisibilityService.removePlayer((CharacterManager) context, targetSession);
            }
        }
        for (Session targetSession : collectVisiblePlayerSessions(context.currentMapDatas)) {
            CharacterManager targetManager = targetSession.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get();
            if (targetManager != null && targetManager.getCharacterData() != null
                    && targetManager.getCharacterData().getPlayerEntity() != null
                    && targetManager.getCharacterData().getPlayerEntity().getEntityGameId() != characterId) {
                PlayerVisibilityService.sendPlayer((CharacterManager) context, targetSession);
            }
        }
    }

    public void removeFromCurrentMapSessions() {
        removeFromCurrentMapSessions(context.characterSession);
    }

    public void removeFromCurrentMapSessions(Session disconnectedSession) {
        if (context.characterData == null || context.characterData.getPlayerEntity() == null) {
            return;
        }
        MapData[] visibleMaps = context.currentMapDatas.clone();
        long characterId = context.characterData.getPlayerEntity().getEntityGameId();
        Set<Session> recipients = collectVisiblePlayerSessions(visibleMaps);
        boolean removed = false;
        for (MapData map : visibleMaps) {
            if (map != null && map.getPlayerSession(characterId) == disconnectedSession) {
                map.removePlayerSession(characterId);
                removed = true;
            }
        }
        if (!removed) {
            return;
        }
        for (Session targetSession : recipients) {
            CharacterManager targetManager = targetSession.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get();
            if (targetManager != null && targetManager.getCharacterData() != null
                    && targetManager.getCharacterData().getPlayerEntity() != null
                    && targetManager.getCharacterData().getPlayerEntity().getEntityGameId() != characterId) {
                PlayerVisibilityService.removePlayer((CharacterManager) context, targetSession);
            }
        }
    }
}

