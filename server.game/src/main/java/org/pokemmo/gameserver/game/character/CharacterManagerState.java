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
import org.pokemmo.gameserver.game.shop.ShopSessions;
import org.pokemmo.gameserver.game.story.PalletStoryState;
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
@Getter @Setter @Slf4j
class CharacterManagerState {
    static final long ONLINE_TIME_FLUSH_INTERVAL_SECONDS = 60L;
    static final ScheduledExecutorService ONLINE_TIME_EXECUTOR =
            Executors.newScheduledThreadPool(2, new ThreadFactory() {
                private int threadNumber;

                @Override
                public Thread newThread(Runnable runnable) {
                    Thread thread = new Thread(runnable, "character-online-time-" + (++threadNumber));
                    thread.setDaemon(true);
                    return thread;
                }
            });

    //角色所属的账户id
    AccountData accountData;
    //角色数据
    CharacterData characterData;
    //角色在游戏中的会话
    Session characterSession;
    //角色的对话管理器
    final InteractManager interactManager = new InteractManager();
    final PlayerVisibilityState playerVisibility = new PlayerVisibilityState();
    final PalletStoryState palletStory = new PalletStoryState();
    //队伍中的宝可梦
    final PokemonData[] partyPokemons = new PokemonData[6];
    //玩家所处的地图数据
    //当前地图，与上下左右4个方向的连接地图数据
    final MapData[] currentMapDatas = new MapData[5];
    //玩家实体的地图加载状态
    CompletableFuture<Boolean> mapLoadFuture;
    //角色在游戏中的心跳线程
    final ServerHeartBeatThread characterHeartBeatThread = new ServerHeartBeatThread(characterSession);
    //随机数生成器
    final Random random = new Random();
    //野外遭遇的短期运行状态，不写入角色数据库
    private int wildEncounterRateBuff;
    private int wildEncounterStepsSinceLastEncounter;
    private byte previousWildEncounterBehavior = -1;
    //战斗管理器
    BattleManager battleManager;
    //当前交易会话
    org.pokemmo.gameserver.game.trade.TradeSession tradeSession;
    //脚本管理器
    ScriptManager scriptManager;
    //雪花id生成器
    SnowflakeIdGenerator snowflakeIdGenerator;
    //数据库服务
    GameServerService characterService;
    //数据库的验证服务
    ServerService serverService;
    //本次游戏会话开始计时的时间，单位为毫秒
    long onlineSessionStartMillis;
    //在线期间定期把完整分钟写入数据库，避免只能依赖断线回调
    ScheduledFuture<?> onlineTimeFlushTask;
    private final CharacterWorldLoader worldLoader = new CharacterWorldLoader(this);
    private final OnlineTimeService onlineTimeService = new OnlineTimeService(this);
    private final MapVisibilityService mapVisibilityService = new MapVisibilityService(this);
    private final CharacterMovementService movementService = new CharacterMovementService(this);
    private final WildEncounterService wildEncounterService = new WildEncounterService(this);
    private final CharacterEventService eventService = new CharacterEventService(this);
    public static boolean exChangeSessionText(long characterId,Session session){
        Session oldSession = GameSessionPool.getPlayerSessionInPool(characterId);
        if(oldSession != null){
            ShopSessions.close(oldSession, "角色重新连接", false);
            //重新设置chatManager上下文
            session.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).set(oldSession.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get());
            //重新设置玩家会话
            CharacterManager reconnectedManager = session.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get();
            reconnectedManager.setCharacterSession(session);
            reconnectedManager.replaceMapSession(oldSession, session);
            //如果旧的session存在对战，需要对 对战的队伍session进行替换
            if(session.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get().getBattleManager() != null){
                if(!session.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get().getBattleManager().exChangeSessionText(oldSession,session)){
                    log.info("重连交换对战的管理器的Session失败，玩家id：{}",characterId);
                    return false;
                }
            }
            GameSessionPool.removePlayerSessionInPool(characterId);
        }
        else{
            return false;
        }
        GameSessionPool.addPlayerSessionInPool(characterId,session);
        return true;
    }

    void replaceMapSession(Session oldSession, Session newSession) {
        if (characterData == null || characterData.getPlayerEntity() == null) {
            return;
        }
        long characterId = characterData.getPlayerEntity().getEntityGameId();
        for (MapData map : currentMapDatas) {
            if (map != null && map.getPlayerSession(characterId) == oldSession) {
                map.addPlayerSession(characterId, newSession);
            }
        }
    }

    public short findNextFreePartyPosition() {
        short position = -1;
        // 计算非null元素的个数
        short partyPokemonAmount = 0;
        for (PokemonData pokemon : partyPokemons) {
            if (pokemon != null) {
                partyPokemonAmount++;
            }
        }
        // 如果还有空位，返回下一个可用位置
        if (partyPokemonAmount < 6) {
            position = partyPokemonAmount;
        }
        return position;
    }
    public byte getCurrentBadgeLimitLevel(){
        byte currentRegionIndexId = characterData.getPlayerEntity().getRegionIndexId();
        int badgeFlag =  characterData.getBadgeFlag()[currentRegionIndexId];
        int badgeSize = 0;
        for(int i = 0;i<8;i++){
            if((badgeFlag & (1 << i)) != 0){
                badgeSize++;
            }
        }
        return BadgeLevelTable.BADGE_LEVEL_TABLE[currentRegionIndexId][badgeSize];
    }
    public void handleLoadGameWorldContext(){
        worldLoader.handleLoadGameWorldContext();
    }

    public synchronized void startOnlineSession() {
        onlineTimeService.startOnlineSession();
    }

    public synchronized void saveOnlineTime() {
        onlineTimeService.saveOnlineTime();
    }







    public synchronized boolean flushOnlineMinutes() {
        return onlineTimeService.flushOnlineMinutes();
    }


    //重新设置当前地图数据
    public void reSetCurrentMaps(MapData targetMapData){
        worldLoader.reSetCurrentMaps(targetMapData);
    }
    public boolean isSelfEntity(long entityGameId) {
        return entityGameId == characterData.getPlayerEntity().getEntityGameId();
    }

    /**
     * Sends the client-compatible trade request dialog to an online player on the same map.
     * The later accept/decline decision is routed through {@code InteractPacket}.
     */
    public void handleTradeRequest(String targetPlayerName) {
        eventService.handleTradeRequest(targetPlayerName);
    }
    //标记地图加载完成
    public void completeMapLoad() {
        mapVisibilityService.completeMapLoad();
    }





    public void broadcastPlayerMovement(boolean isRun) {
        mapVisibilityService.broadcastPlayerMovement(isRun);
    }

    public void broadcastPlayerTransportation() {
        mapVisibilityService.broadcastPlayerTransportation();
    }

    /**
     * Synchronizes the complete player entity after the client has confirmed
     * its map load. The client may clear entities while processing the map
     * packet, so sending nearby players before that point is not reliable.
     * The reverse send makes an already loaded player see the newly joined one.
     */
    public void synchronizeVisiblePlayersAfterMapLoad() {
        mapVisibilityService.synchronizeVisiblePlayersAfterMapLoad();
    }

    public void broadcastPlayerToward() {
        mapVisibilityService.broadcastPlayerToward();
    }

    public void broadcastPlayerPosition() {
        mapVisibilityService.broadcastPlayerPosition();
    }

    public void notifyMapTransition(MapData[] previousVisibleMaps, MapData previousPrimaryMap) {
        mapVisibilityService.notifyMapTransition(previousVisibleMaps, previousPrimaryMap);
    }

    public void removeFromCurrentMapSessions() {
        mapVisibilityService.removeFromCurrentMapSessions();
    }

    public void removeFromCurrentMapSessions(Session disconnectedSession) {
        mapVisibilityService.removeFromCurrentMapSessions(disconnectedSession);
    }

    //处理玩家重新加载地图
    public CompletableFuture<Boolean> handleReLoadMap(MapData targetMap){
        ShopSessions.close(characterSession, "地图切换，商店已关闭", true);
        interactManager.setMailWidgetOpen(false);
        return worldLoader.handleReLoadMap(targetMap);
    }
    //检测是否与自身玩家重叠
    public boolean IsOverlapWithCharacter(int x, int y){
        return movementService.IsOverlapWithCharacter(x, y);
    }

    //处理更换地图的事件
    public boolean handleWarpEvent(short currentX,short currentY,byte currentZ){
        return eventService.handleWarpEvent(currentX, currentY, currentZ);
    }
    public boolean handleEvent(short x,short y,byte z) throws InterruptedException {
        return eventService.handleEvent(x, y, z);
    }
    public void handleWildEncounter(MapData previousPrimaryMap, MapData currentMap, short x, short y, byte z) {
        wildEncounterService.handle(previousPrimaryMap, currentMap, x, y, z);
    }
    public List<GameEvent> getActiveGameEvents(EventRegionType eventRegionType){
        return eventService.getActiveGameEvents(eventRegionType);
    }
    //获取自动移动路径并重设实体坐标
    public List<SportType> AutoMove(long entityGameId, boolean isRun, byte regionIndexId, byte mapHeaderIdOrGbaMapGroupId, byte gbaMapId, short tarX, short tarY, byte z) {
        return movementService.AutoMove(entityGameId, isRun, regionIndexId, mapHeaderIdOrGbaMapGroupId, gbaMapId, tarX, tarY, z);
    }


    //计算新的位置
    public void computerNewPosition(short x, short y, byte moveToward, byte z, int mapTopEdge, int mapBottomEdge, int mapLeftEdge, int mapRightEdge) throws InterruptedException {
        movementService.computerNewPosition(x, y, moveToward, z, mapTopEdge, mapBottomEdge, mapLeftEdge, mapRightEdge);
    }

    public String moveClose(int deltaX, int deltaY) {
        return movementService.moveClose(deltaX, deltaY);
    }

    public String moveTo(int regionId, int mapHeaderIdOrGroupId, int gbaMapId, int x, int y) {
        return movementService.moveTo(regionId, mapHeaderIdOrGroupId, gbaMapId, x, y);
    }

    //更新附近的地图数据

    protected CharacterManagerState(AccountData accountData, Session characterSession, ScriptManager scriptManager, SnowflakeIdGenerator snowflakeIdGenerator, GameServerService characterService, ServerService serverService){
        this.accountData = accountData;
        this.characterSession = characterSession;
        this.scriptManager = scriptManager;
        this.snowflakeIdGenerator = snowflakeIdGenerator;
        this.characterService = characterService;
        this.serverService = serverService;
    }
    public static class Builder {
        private AccountData accountData;
        private Session characterSession;
        private ScriptManager scriptManager;
        private SnowflakeIdGenerator snowflakeIdGenerator;
        private GameServerService characterService;
        private ServerService serverService;
        public Builder setAccountData(AccountData accountData) {
            this.accountData = accountData;
            return this;
        }
        public Builder setCharacterSession(Session characterSession) {
            this.characterSession = characterSession;
            return this;
        }
        public Builder setScriptManager(ScriptManager scriptManager) {
            this.scriptManager = scriptManager;
            return this;
        }
        public Builder setSnowflakeIdGenerator(SnowflakeIdGenerator snowflakeIdGenerator) {
            this.snowflakeIdGenerator = snowflakeIdGenerator;
            return this;
        }
        public Builder setCharacterService(GameServerService characterService) {
            this.characterService = characterService;
            return this;
        }
        public Builder setServerService(ServerService serverService) {
            this.serverService = serverService;
            return this;
        }
        public CharacterManager build() {
            return new CharacterManager(accountData, characterSession, scriptManager, snowflakeIdGenerator, characterService, serverService);
        }
    }

}

