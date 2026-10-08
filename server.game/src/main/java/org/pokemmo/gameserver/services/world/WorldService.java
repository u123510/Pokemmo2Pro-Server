package org.pokemmo.gameserver.services.world;

import com.github.maltalex.ineter.base.IPAddress;
import org.jooq.Record;
import org.jooq.Result;
import org.jooq.postgres.extensions.types.Inet;
import org.pokemmo.db.Database;
import org.pokemmo.db.jooq.tables.records.*;
import org.pokemmo.gameserver.game.container.PokemonContainerType;
import org.pokemmo.gameserver.game.events.EventRegionType;
import org.pokemmo.gameserver.game.events.GameEvent;
import org.pokemmo.gameserver.game.events.GameEventType;
import org.pokemmo.gameserver.game.events.ServerEventType;
import org.pokemmo.gameserver.game.instance.GameInstance;
import org.pokemmo.gameserver.game.pokemon.PokemonDexData;
import org.pokemmo.gameserver.game.pokemon.PokemonManager;
import org.server.node.JoinableSeverData;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

import static org.pokemmo.db.jooq.Tables.ACCOUNT_CONTEXT;
import static org.pokemmo.db.jooq.Tables.CHARACTER;
import static org.pokemmo.db.jooq.Tables.CONTAINER;
import static org.pokemmo.db.jooq.Tables.GAME_NODE;
import static org.pokemmo.db.jooq.Tables.ONLINE_CHAT_NODE_SERVER;
import static org.pokemmo.db.jooq.Tables.ONLINE_GAME_NODE_SERVER;
import static org.pokemmo.db.jooq.Tables.POKEMON_DEX;

public final class WorldService {
    private final Database database;

    public WorldService(Database database) {
        this.database = database;
    }

public String getServerNodeName(int nodeId){
    return database.ctx()
            .select().from(GAME_NODE)
            .where(GAME_NODE.NODE_ID.eq(nodeId))
            .fetchOneInto(GameNodeRecord.class).getNodeName();
  }

public ContainerRecord getContainerByType(PokemonContainerType containerType) {
    return database.ctx()
        .select().from(CONTAINER)
        .where(CONTAINER.NAME.eq(containerType.getName()))
        .fetchOneInto(ContainerRecord.class);
  }

public AccountContextRecord getAccountContext(int accountId) {
    return database.ctx()
        .select().from(ACCOUNT_CONTEXT)
        .where(ACCOUNT_CONTEXT.ACCOUNT_ID.eq(accountId))
        .fetchOneInto(AccountContextRecord.class);
  }

public AccountContextRecord getAccountContextByCharacterId(long characterId) {
    return database.ctx()
            .select().from(ACCOUNT_CONTEXT)
            .where(ACCOUNT_CONTEXT.CHARACTER_ID.eq(characterId))
            .fetchOneInto(AccountContextRecord.class);
  }

public int getGameServerIdByIpv4(Inet ipAddress) {
    OnlineGameNodeServerRecord serverRecord = database.ctx()
            .select().from(ONLINE_GAME_NODE_SERVER)
            .where(ONLINE_GAME_NODE_SERVER.IPV4.eq(ipAddress))
            .fetchOneInto(OnlineGameNodeServerRecord.class);
    if(serverRecord != null){
      return serverRecord.getServerId();
    }
    return -1;
  }

public void deleteAccountContext(int accountId) {
    database.ctx()
            .delete(ACCOUNT_CONTEXT)
            .where(ACCOUNT_CONTEXT.ACCOUNT_ID.eq(accountId))
            .execute();
  }

public List<ContainerRecord> getPokemonContainer() {
    return database.ctx()
        .select().from(CONTAINER)
        .fetchInto(ContainerRecord.class);
  }

public List<GameEvent> getGameSideActiveEventFlags(long characterId, EventRegionType regionType) {
    List<GameEvent> eventList = new ArrayList<>();
    CharacterRecord characterRecord = database.ctx()
        .select().from(CHARACTER)
        .where(CHARACTER.ID.eq(characterId))
        .fetchOneInto(CharacterRecord.class);
    //获取冠军标志
    boolean championFlag = characterRecord.getChampionFlag()[regionType.getType()];
    //获取跑鞋标志
    boolean runningShoeFlag = characterRecord.getRunningShoeFlag()[regionType.getType()];
    switch (regionType) {
      case KANTO:
        if (championFlag) {
            eventList.add(new GameEvent(GameEventType.the_hall_of_fame_status,1));
        }
        if(runningShoeFlag){
            eventList.add(new GameEvent(GameEventType.running_shoe_status,1));
        }
        //获取徽章标志
        short badgeFlag = characterRecord.getBadgeFlag()[regionType.getType()];
        for(GameEventType gameEventType:GameEventType.kantoBadgeArray){
            if((badgeFlag & (1 << gameEventType.getIndex())) != 0){
                eventList.add(new GameEvent(gameEventType,1));
            }
        }
        //获取故事线标志
        short storyLineFlag = characterRecord.getStoryLineFlag()[regionType.getType()];
        for(GameEventType gameEventType:GameEventType.kantoStoryLineArray){
            if((storyLineFlag & (1 << gameEventType.getIndex())) != 0){
                eventList.add(new GameEvent(gameEventType,1));
            }
        }
        //获取城市飞行标志
        long cityCanFlyFlag = characterRecord.getCityCanFlyFlag()[regionType.getType()];
        for(GameEventType gameEventType:GameEventType.kantoCityCanFlyArray) {
          if ((cityCanFlyFlag & (1 << gameEventType.getIndex())) != 0) {
            eventList.add(new GameEvent(gameEventType, 1));
          }
        }
    }
    return eventList;
  }

public List<JoinableSeverData> getOnlineChatNodeServers(int serverNodeId) {
    Result<Record> chatNodeServers= database.ctx()
            .select().from(ONLINE_CHAT_NODE_SERVER)
            .join(GAME_NODE).on(ONLINE_CHAT_NODE_SERVER.NODE_ID.eq(serverNodeId))
            .fetch();
    return chatNodeServers
            .map(record -> record.into(ONLINE_CHAT_NODE_SERVER))
            .stream().map(r -> new JoinableSeverData(
                    IPAddress.of(r.getIpv4().address()),
                    IPAddress.of(r.getIpv6().address()),
                    r.getPort(),
                    (byte)40
            )).toList();
  }

public void setServerSideEventStatus(long characterId, GameEventType gameEventType, short flag) {
    int regionIndex = gameEventType.getEventRegionType().getType();
    switch (gameEventType.getEventKindType())
    {
      case BADGE:
        database.ctx()
                .update(CHARACTER)
                .set(org.jooq.impl.DSL.field("{0}[{1}]", Short.class, CHARACTER.BADGE_FLAG, regionIndex + 1), flag)
                .where(CHARACTER.ID.eq(characterId))
                .execute();
        break;
      case CITY_FLY:
        database.ctx()
                .update(CHARACTER)
                .set(org.jooq.impl.DSL.field("{0}[{1}]", Short.class, CHARACTER.CITY_CAN_FLY_FLAG, regionIndex + 1), flag)
                .where(CHARACTER.ID.eq(characterId))
                .execute();
        break;
      case STORY_LINE:
        database.ctx()
                .update(CHARACTER)
                .set(org.jooq.impl.DSL.field("{0}[{1}]", Short.class, CHARACTER.STORY_LINE_FLAG, regionIndex + 1), flag)
                .where(CHARACTER.ID.eq(characterId))
                .execute();
        break;
      case RUNNING_SHOE:
        boolean hasRunningShoe = flag == 1;
        database.ctx()
                .update(CHARACTER)
                .set(org.jooq.impl.DSL.field("{0}[{1}]", Boolean.class, CHARACTER.RUNNING_SHOE_FLAG, regionIndex + 1), hasRunningShoe)
                .where(CHARACTER.ID.eq(characterId))
                .execute();
        break;
      case FAME:
        boolean hasFame = flag == 1;
        database.ctx()
                .update(CHARACTER)
                .set(org.jooq.impl.DSL.field("{0}[{1}]", Boolean.class, CHARACTER.CHAMPION_FLAG, regionIndex + 1), hasFame)
                .where(CHARACTER.ID.eq(characterId))
                .execute();
        break;
    }
  }

public void setGameSideEventStatus(long characterId, ServerEventType serveEventType, short eventStatus) {
    int regionIndex = serveEventType.getEventRegionType().getType();
    switch (serveEventType.getEventName()) {
      case "oak_lab_status":
        database.ctx()
                .update(CHARACTER)
                .set(CHARACTER.OAK_LAB_STATUS, eventStatus)
                .where(CHARACTER.ID.eq(characterId))
                .execute();
        break;
      case "oak_parcel_status":
        database.ctx()
                .update(CHARACTER)
                .set(CHARACTER.OAK_PARCEL_STATUS, eventStatus)
                .where(CHARACTER.ID.eq(characterId))
                .execute();
        break;
      case "kanto_first_partner_status":
        database.ctx()
                .update(CHARACTER)
                .set(org.jooq.impl.DSL.field("{0}[{1}]", Short.class, CHARACTER.FIRST_PARTNER_STATUS, regionIndex + 1), eventStatus)
                .where(CHARACTER.ID.eq(characterId))
                .execute();
        break;
    }
  }

public BitSet[] getPokemonDexUnlockDataById(long characterId) {
    PokemonDexRecord record = database.ctx()
            .select().from(POKEMON_DEX)
            .where(POKEMON_DEX.PLAYER_ID.eq(characterId))
            .fetchOneInto(PokemonDexRecord.class);
    if (record == null) {
      return new BitSet[]{new BitSet(), new BitSet(), new BitSet(), new BitSet()};
    }
    BitSet meetLevel = BitSet.valueOf(record.getMeetLevel());               // 遇见层级
    BitSet alreadyHaveLevel = BitSet.valueOf(record.getAlreadyHaveLevel());               // 已拥有层级
    BitSet caughtLevel = BitSet.valueOf(record.getCaughtLevel());           // 已捕获层级
    BitSet caughtAlphaLevel = BitSet.valueOf(record.getCaughtAlphaLevel());           // 已捕获头目层级
    return new BitSet[]{meetLevel, alreadyHaveLevel, caughtLevel, caughtAlphaLevel};
  }

public BitSet[] unlockAllPokemonDex(long characterId) {
    BitSet allDex = new BitSet();
    allDex.set(1, 650);
    for (PokemonDexData data : PokemonManager.getAllPokemonDexData()) {
        if (data.getPokemonIndexId() > 0) {
            allDex.set(data.getPokemonIndexId());
        }
    }
    byte[] dexBytes = allDex.toByteArray();
    PokemonDexRecord record = database.ctx()
            .selectFrom(POKEMON_DEX)
            .where(POKEMON_DEX.PLAYER_ID.eq(characterId))
            .fetchOne();
    if (record == null) {
        database.ctx().insertInto(POKEMON_DEX)
                .set(POKEMON_DEX.PLAYER_ID, characterId)
                .set(POKEMON_DEX.MEET_LEVEL, dexBytes)
                .set(POKEMON_DEX.ALREADY_HAVE_LEVEL, dexBytes)
                .set(POKEMON_DEX.CAUGHT_LEVEL, dexBytes)
                .set(POKEMON_DEX.CAUGHT_ALPHA_LEVEL, dexBytes)
                .execute();
    } else {
        database.ctx().update(POKEMON_DEX)
                .set(POKEMON_DEX.MEET_LEVEL, dexBytes)
                .set(POKEMON_DEX.ALREADY_HAVE_LEVEL, dexBytes)
                .set(POKEMON_DEX.CAUGHT_LEVEL, dexBytes)
                .set(POKEMON_DEX.CAUGHT_ALPHA_LEVEL, dexBytes)
                .where(POKEMON_DEX.PLAYER_ID.eq(characterId))
                .execute();
    }
    return new BitSet[]{
            (BitSet) allDex.clone(),
            (BitSet) allDex.clone(),
            (BitSet) allDex.clone(),
            (BitSet) allDex.clone()
    };
  }

public List<GameInstance> getInstaceInfo(long characterId) {
    List<GameInstance> gameInstances = new ArrayList<>();
    CharacterRecord characterRecord = database.ctx()
            .select().from(CHARACTER)
            .where(CHARACTER.ID.eq(characterId))
            .fetchOneInto(CharacterRecord.class);
    LocalDateTime[] nextInstanceTime = characterRecord.getInstanceNextTime();
    Short[] finishTimes = characterRecord.getInstanceTimes();
    for (int i = 0;i<5;i++){
        gameInstances.add(new GameInstance((byte) i, (int)nextInstanceTime[i].toEpochSecond(ZoneOffset.UTC), finishTimes[i]));
    }
    return gameInstances;
  }

}
