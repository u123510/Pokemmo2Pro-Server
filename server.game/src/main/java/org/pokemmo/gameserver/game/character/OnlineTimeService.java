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
final class OnlineTimeService extends CharacterManagerComponent {
    OnlineTimeService(CharacterManagerState context) {
        super(context);
    }

    public synchronized void startOnlineSession() {
        if (context.characterData == null || context.characterData.getPlayerEntity() == null
                || context.onlineSessionStartMillis > 0) {
            return;
        }
        context.onlineSessionStartMillis = System.currentTimeMillis();
        context.onlineTimeFlushTask = CharacterManagerState.ONLINE_TIME_EXECUTOR.scheduleAtFixedRate(
                this::flushOnlineMinutesWhileActive,
                CharacterManagerState.ONLINE_TIME_FLUSH_INTERVAL_SECONDS,
                CharacterManagerState.ONLINE_TIME_FLUSH_INTERVAL_SECONDS,
                TimeUnit.SECONDS);
    }

    public synchronized void saveOnlineTime() {
        if (flushOnlineMinutes()) {
            context.onlineSessionStartMillis = 0;
            cancelOnlineTimeFlushTask();
        }
    }

    private void flushOnlineMinutesWhileActive() {
        synchronized (this) {
            if (context.onlineSessionStartMillis <= 0) {
                cancelOnlineTimeFlushTask();
                return;
            }
            if (!isCharacterSessionActive()) {
                saveOnlineTime();
                return;
            }
            flushOnlineMinutes();
        }
    }

    private boolean isCharacterSessionActive() {
        return context.characterSession != null
                && context.characterSession.getChannel() != null
                && context.characterSession.isActive();
    }

    private void cancelOnlineTimeFlushTask() {
        if (context.onlineTimeFlushTask != null) {
            context.onlineTimeFlushTask.cancel(false);
            context.onlineTimeFlushTask = null;
        }
    }

    public synchronized boolean flushOnlineMinutes() {
        if (context.onlineSessionStartMillis <= 0 || context.characterData == null
                || context.characterData.getPlayerEntity() == null) {
            return true;
        }
        long elapsedMillis = Math.max(0L, System.currentTimeMillis() - context.onlineSessionStartMillis);
        long elapsedMinutes = TimeUnit.MILLISECONDS.toMinutes(elapsedMillis);
        if (elapsedMinutes <= 0) {
            return true;
        }
        int minutes = (int) Math.min(elapsedMinutes, Integer.MAX_VALUE);
        long characterId = context.characterData.getPlayerEntity().getEntityGameId();
        try {
            if (!context.characterService.addOnlineMinutes(characterId, minutes)) {
                log.warn("保存角色在线时间失败: characterId={}, minutes={}", characterId, minutes);
                return false;
            }
            long totalMinutes = (long) Math.max(0, context.characterData.getOnlineMinutes()) + minutes;
            context.characterData.setOnlineMinutes((int) Math.min(totalMinutes, Integer.MAX_VALUE));
            context.onlineSessionStartMillis += TimeUnit.MINUTES.toMillis(minutes);
            log.trace("角色在线时长已保存: characterId={}, addedMinutes={}, totalMinutes={}, remainingMillis={}",
                    characterId, minutes, context.characterData.getOnlineMinutes(),
                    Math.max(0L, System.currentTimeMillis() - context.onlineSessionStartMillis));
            return true;
        } catch (RuntimeException exception) {
            log.error("保存角色在线时间时发生数据库错误: characterId={}, minutes={}",
                    characterId, minutes, exception);
            return false;
        }
    }
}

