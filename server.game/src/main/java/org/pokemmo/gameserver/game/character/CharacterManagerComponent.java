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
abstract class CharacterManagerComponent {
    protected final CharacterManagerState context;

    protected CharacterManagerComponent(CharacterManagerState context) {
        this.context = context;
    }

    protected ScriptManager getScriptManager() { return context.getScriptManager(); }
    protected boolean isSelfEntity(long entityGameId) { return context.isSelfEntity(entityGameId); }
    protected void handleLoadGameWorldContext() { context.handleLoadGameWorldContext(); }
    protected void reSetCurrentMaps(MapData targetMapData) { context.reSetCurrentMaps(targetMapData); }
    protected CompletableFuture<Boolean> handleReLoadMap(MapData targetMap) { return context.handleReLoadMap(targetMap); }
    protected boolean flushOnlineMinutes() { return context.flushOnlineMinutes(); }
    protected void completeMapLoad() { context.completeMapLoad(); }
    protected void broadcastPlayerMovement(boolean isRun) { context.broadcastPlayerMovement(isRun); }
    protected void broadcastPlayerTransportation() { context.broadcastPlayerTransportation(); }
    protected void synchronizeVisiblePlayersAfterMapLoad() { context.synchronizeVisiblePlayersAfterMapLoad(); }
    protected void broadcastPlayerToward() { context.broadcastPlayerToward(); }
    protected void notifyMapTransition(MapData[] previousVisibleMaps, MapData previousPrimaryMap) { context.notifyMapTransition(previousVisibleMaps, previousPrimaryMap); }
    protected void removeFromCurrentMapSessions() { context.removeFromCurrentMapSessions(); }
    protected void removeFromCurrentMapSessions(Session disconnectedSession) { context.removeFromCurrentMapSessions(disconnectedSession); }
    protected boolean IsOverlapWithCharacter(int x, int y) { return context.IsOverlapWithCharacter(x, y); }
    protected boolean handleWarpEvent(short x, short y, byte z) { return context.handleWarpEvent(x, y, z); }
    protected boolean handleEvent(short x, short y, byte z) throws InterruptedException { return context.handleEvent(x, y, z); }
    protected List<GameEvent> getActiveGameEvents(EventRegionType type) { return context.getActiveGameEvents(type); }
    protected List<SportType> AutoMove(long entityId, boolean isRun, byte region, byte header, byte map, short x, short y, byte z) { return context.AutoMove(entityId, isRun, region, header, map, x, y, z); }
    protected void computerNewPosition(short x, short y, byte toward, byte z, int top, int bottom, int left, int right) throws InterruptedException { context.computerNewPosition(x, y, toward, z, top, bottom, left, right); }
}
