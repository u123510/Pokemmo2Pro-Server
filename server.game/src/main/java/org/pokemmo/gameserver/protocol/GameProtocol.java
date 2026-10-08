package org.pokemmo.gameserver.protocol;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import org.pokemmo.db.Database;
import org.pokemmo.db.jooq.tables.records.AccountContextRecord;
import org.pokemmo.gameserver.game.character.CharacterManager;
import org.pokemmo.gameserver.command.GameCommandModule;
import org.pokemmo.gameserver.game.interact.InteractType;
import org.pokemmo.gameserver.game.gtl.GtlRequestState;
import org.pokemmo.gameserver.protocol.packets.c2s.*;
import org.pokemmo.gameserver.protocol.packets.s2c.*;
import org.pokemmo.gameserver.script.ScriptManager;
import org.pokemmo.gameserver.util.SnowflakeIdGenerator;
import org.server.*;
import org.pokemmo.gameserver.services.GameServerService;
import org.pokemmo.gameserver.game.trade.TradeManager;
import org.pokemmo.gameserver.game.friend.FriendManager;
import org.pokemmo.gameserver.game.battle.BattleRequestManager;
import org.pokemmo.gameserver.game.battle.BattleSpectatingService;
import org.pokemmo.gameserver.game.pool.GameSessionPool;
import org.pokemmo.gameserver.game.shop.ShopSessions;
import org.pokemmo.gameserver.game.story.StoryService;
import org.server.redis.RedisUtil;
import org.server.services.ServerService;
import io.netty.util.AttributeKey;


public class GameProtocol extends Protocol {
  // 角色数据
  public static final AttributeKey<CharacterManager> ATTRIBUTE_CHARACTER_MANAGER = AttributeKey.valueOf("character_manager");
  public static final AttributeKey<GtlRequestState> ATTRIBUTE_GTL_REQUEST_STATE =
          AttributeKey.valueOf("gtl_request_state");
  // 断开连接事件监听器
  private final Session.DisconnectListener disconnectListener = new Session.DisconnectListener() {
    @Override
    public void onSessionDisconnected(Session session, ServerType serverType) {
      if(serverType==ServerType.GAME){
        handleGameSessionDisconnected(session);
      }
    }
  };
  private void handleGameSessionDisconnected(Session session) {
    ShopSessions.close(session, "连接已断开", false);
    GameSessionPool.unregisterSession(session);
    CharacterManager disconnected = session.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get();
    if (disconnected == null) {
      return;
    }
    disconnected.getInteractManager().setMailWidgetOpen(false);
    if (disconnected.getCharacterData() != null && disconnected.getCharacterData().getPlayerEntity() != null) {
      long characterId = disconnected.getCharacterData().getPlayerEntity().getEntityGameId();
      Session activeSession = GameSessionPool.getPlayerSessionInPool(characterId);
      if (activeSession != null && activeSession != session) {
        return;
      }
      StoryService.onDisconnect(disconnected, session);
      disconnected.saveOnlineTime();
      disconnected.removeFromCurrentMapSessions(session);
      GameSessionPool.removePlayerSessionInPool(characterId, session);
    }
    FriendManager.cancelFor(disconnected);
    BattleRequestManager.cancelFor(disconnected);
    BattleSpectatingService.leave(disconnected);
    if (disconnected.getAccountData() == null) {
      return;
    }
    TradeManager.cancelFor(disconnected);
    int accountId = disconnected.getAccountData().getAccountId();
    if(disconnected.getInteractManager().getInteractType()== InteractType.NONE && disconnected.getBattleManager()==null){
        disconnected.getCharacterService().deleteAccountContext(accountId);
    }
    else{
      AccountContextRecord accountRecord = new AccountContextRecord();
      accountRecord.setAccountId(accountId);
      accountRecord.setIsLogOut(true);
      disconnected.getServerService().updateAccountContext(accountRecord);
    }
  }
  public GameProtocol(ServerService sessionService, GameServerService characterService, Database database, SnowflakeIdGenerator snowflakeIdGenerator, ScriptManager scriptManager, RedisUtil redisUtil) {
    super( 2, true, true, createInjector(sessionService, characterService, database, snowflakeIdGenerator, scriptManager, redisUtil));
    characterService.configureItemUseManager(scriptManager.getItemUseManager());
    //注册监听函数
    Session.registerDisconnectListener(disconnectListener);
    registerPacket(DataFlow.CLIENT_TO_SERVER, (byte) 0x01, JoinGameWorldPacket.class);
    registerPacket(DataFlow.SERVER_TO_CLIENT, (byte) 0x01, SendJoinGameWorldResponsePacket.class);
    registerPacket(DataFlow.CLIENT_TO_SERVER, (byte) 0x02, StartCharacterSelectionPacket.class);
    registerPacket(DataFlow.SERVER_TO_CLIENT, (byte) 0x02, SendCharacterListPacket.class);
    registerPacket(DataFlow.CLIENT_TO_SERVER, (byte) 0x04, SelectCharacterPacket.class);
    registerPacket(DataFlow.SERVER_TO_CLIENT, (byte) 0x04, SendAcknowledgeCharacterSelectionPacket.class);
    registerPacket(DataFlow.CLIENT_TO_SERVER, (byte) 0x05, RequestPlayerPacket.class);
    registerPacket(DataFlow.SERVER_TO_CLIENT, (byte) 0x05, SendLoadPlayerPacket.class);
    registerPacket(DataFlow.CLIENT_TO_SERVER, (byte) 0x06, MovePacket.class);
    registerPacket(DataFlow.CLIENT_TO_SERVER, (byte) 0x07, ChangeTwordPacket.class);
    registerPacket(DataFlow.SERVER_TO_CLIENT, (byte) 0x07, SendSetEntityTowardPacket.class);
    registerPacket(DataFlow.CLIENT_TO_SERVER, (byte) 0x08, ChatPacket.class);
    registerPacket(DataFlow.SERVER_TO_CLIENT, (byte) 0x08, SendRemoveEntityPacket.class);
    registerPacket(DataFlow.CLIENT_TO_SERVER, (byte) 0x09, ChangePokemonPosPacket.class);
    registerPacket(DataFlow.SERVER_TO_CLIENT, (byte) 0x09, SendChatMessagePacket.class);
    registerPacket(DataFlow.SERVER_TO_CLIENT, (byte) 0x0A, SendLoadDexPacket.class);
    registerPacket(DataFlow.SERVER_TO_CLIENT, (byte) 0x0B, SendUpdateDexPacket.class);
    registerPacket(DataFlow.SERVER_TO_CLIENT, (byte) 0x0C, SendUpdatePlayerInfo.class);
    registerPacket(DataFlow.CLIENT_TO_SERVER, (byte) 0x0C, ReleasePokemonPacket.class);
    registerPacket(DataFlow.SERVER_TO_CLIENT, (byte) 0x0D, SendEntitySportPacket.class);
    registerPacket(DataFlow.SERVER_TO_CLIENT, (byte) 0x0E, SendHasEventPacket.class);
    registerPacket(DataFlow.CLIENT_TO_SERVER, (byte) 0x0E, RenamePokemonPacket.class);
    registerPacket(DataFlow.CLIENT_TO_SERVER, (byte) 0x0F, UpdatePokemonItemPacket.class);
    registerPacket(DataFlow.SERVER_TO_CLIENT, (byte) 0x0F, SendBattleStatusPacket.class);
    registerPacket(DataFlow.CLIENT_TO_SERVER, (byte) 0x10, FlyPacket.class);
    registerPacket(DataFlow.SERVER_TO_CLIENT, (byte) 0x10, SendLoadMapPacket.class);
    registerPacket(DataFlow.CLIENT_TO_SERVER, (byte) 0x11, ChangeFllowPokemonPacket.class);
    registerPacket(DataFlow.SERVER_TO_CLIENT, (byte) 0x11, SendSetEntityPosPacket.class);
    registerPacket(DataFlow.SERVER_TO_CLIENT, (byte) 0x12, SendAddGameEntityPacket.class);
    registerPacket(DataFlow.CLIENT_TO_SERVER, (byte) 0x13, OtherSettingUpdatePacket.class);
    registerPacket(DataFlow.SERVER_TO_CLIENT, (byte) 0x13, SendPokemonContainerPacket.class);
    registerPacket(DataFlow.SERVER_TO_CLIENT, (byte) 0x14, SendAddPokemonPacket.class);
    registerPacket(DataFlow.CLIENT_TO_SERVER, (byte) 0x15, RequsetChatChannelPacket.class);
    registerPacket(DataFlow.SERVER_TO_CLIENT, (byte) 0x15, SendRemovePokemonPacket.class);
    registerPacket(DataFlow.SERVER_TO_CLIENT, (byte) 0x16, SendUpdatePokemonDataPacket.class);
    registerPacket(DataFlow.CLIENT_TO_SERVER, (byte) 0x17, CloseMailWidgetPacket.class);
    registerPacket(DataFlow.CLIENT_TO_SERVER, (byte) 0x18, SelectPokemonParticleEffectPacket.class);
    registerPacket(DataFlow.SERVER_TO_CLIENT, (byte) 0x1A, SendBattlePokemonUpdateMovePpPacket.class);
    registerPacket(DataFlow.CLIENT_TO_SERVER, (byte) 0x1B, SortPokemonPacket.class);
    registerPacket(DataFlow.SERVER_TO_CLIENT, (byte) 0x1B, SendRemoveAroundEntityPacket.class);
    registerPacket(DataFlow.SERVER_TO_CLIENT, (byte) 0x1C, SendAddGameEventFlagPacket.class);
    registerPacket(DataFlow.CLIENT_TO_SERVER, (byte) 0x1C, UpdatePcBoxInfoPacket.class);
    registerPacket(DataFlow.CLIENT_TO_SERVER, (byte) 0x1D, PokemonLockStatusPacket.class);
    registerPacket(DataFlow.CLIENT_TO_SERVER, (byte) 0x20, LongtHeartbeatPacket.class);
    registerPacket(DataFlow.SERVER_TO_CLIENT, (byte) 0x20, SendLongHeartBeatPacket.class);
    registerPacket(DataFlow.CLIENT_TO_SERVER, (byte) 0x21, InteractPacket.class);
    registerPacket(DataFlow.SERVER_TO_CLIENT, (byte) 0x21, SendInteractPacket.class);
    registerPacket(DataFlow.CLIENT_TO_SERVER, (byte) 0x23, TradePokemonPacket.class);
    registerPacket(DataFlow.CLIENT_TO_SERVER, (byte) 0x24, TradeItemPacket.class);
    registerPacket(DataFlow.SERVER_TO_CLIENT, (byte) 0x50, SendTradeWindowPacket.class);
    registerPacket(DataFlow.CLIENT_TO_SERVER, (byte) 0x50, TradeLockPacket.class);
    registerPacket(DataFlow.CLIENT_TO_SERVER, (byte) 0x51, RequestTradePacket.class);
    registerPacket(DataFlow.SERVER_TO_CLIENT, (byte) 0x51, SendTradeStatusPacket.class);
    registerPacket(DataFlow.SERVER_TO_CLIENT, (byte) 0x52, SendTradePokemonPacket.class);
    registerPacket(DataFlow.CLIENT_TO_SERVER, (byte) 0x52, TradeMoneyPacket.class);
    registerPacket(DataFlow.CLIENT_TO_SERVER, (byte) 0x53, TradeItemPacket.class);
    registerPacket(DataFlow.SERVER_TO_CLIENT, (byte) 0x53, SendTradeMoneyPacket.class);
    registerPacket(DataFlow.SERVER_TO_CLIENT, (byte) 0x54, SendTradeItemPacket.class);
    registerPacket(DataFlow.CLIENT_TO_SERVER, (byte) 0x22, StartTalkPacket.class);
    registerPacket(DataFlow.SERVER_TO_CLIENT, (byte) 0x23, SendItemShopPacket.class);
    registerPacket(DataFlow.SERVER_TO_CLIENT, (byte) 0x24, SendShowPokemonNameplateWidgetPacket.class);
    registerPacket(DataFlow.CLIENT_TO_SERVER, (byte) 0x25, BattleRequestPacket.class);
    registerPacket(DataFlow.SERVER_TO_CLIENT, (byte) 0x25, SendPlayMusicPacket.class);
    registerPacket(DataFlow.CLIENT_TO_SERVER, (byte) 0x26, UseItemPacket.class);
    registerPacket(DataFlow.SERVER_TO_CLIENT, (byte) 0x26, SendPlaySoundPacket.class);
    registerPacket(DataFlow.CLIENT_TO_SERVER, (byte) 0x27, EmptyInteractPacket.class);
    registerPacket(DataFlow.SERVER_TO_CLIENT, (byte) 0x27, SendPcStatePacket.class);
    registerPacket(DataFlow.SERVER_TO_CLIENT, (byte) 0x28, SendUpdatePlayerTransportationPacket.class);
    registerPacket(DataFlow.CLIENT_TO_SERVER, (byte) 0x29, PlayerSetSkinPacket.class);
    registerPacket(DataFlow.SERVER_TO_CLIENT, (byte) 0x29, SendGameSideEventFlagPacket.class);
    registerPacket(DataFlow.SERVER_TO_CLIENT, (byte) 0x2B, SendSetFollowPokemonPacket.class);
    registerPacket(DataFlow.CLIENT_TO_SERVER, (byte) 0x2D, NpctriggerBattlePacket.class);
    registerPacket(DataFlow.CLIENT_TO_SERVER, (byte) 0x30, ResetCharacterClothesPacket.class);
    registerPacket(DataFlow.SERVER_TO_CLIENT, (byte) 0x30, SendBattleInitPacket.class);
    registerPacket(DataFlow.SERVER_TO_CLIENT, (byte) 0x31, SendBattleFinishPacket.class);
    registerPacket(DataFlow.CLIENT_TO_SERVER, (byte) 0x32, BattlePokemonCommandPacket.class);
    registerPacket(DataFlow.SERVER_TO_CLIENT, (byte) 0x32, SendBattleDebutPokemonCanActionPacket.class);
    registerPacket(DataFlow.CLIENT_TO_SERVER, (byte) 0x33, BattleFinishSuccessPacket.class);
    registerPacket(DataFlow.SERVER_TO_CLIENT, (byte) 0x33, SendBattlePokemonActionWithSelectorPacket.class);
    registerPacket(DataFlow.CLIENT_TO_SERVER, (byte) 0x34, BattleSpectateRequestPacket.class);
    registerPacket(DataFlow.SERVER_TO_CLIENT, (byte) 0x34, SendBattleRunResultPacket.class);
    registerPacket(DataFlow.SERVER_TO_CLIENT, (byte) 0x35, SendSwapPokemonPacket.class);
    registerPacket(DataFlow.CLIENT_TO_SERVER, (byte) 0x35, BattleSpectatingReturnPacket.class);
    registerPacket(DataFlow.CLIENT_TO_SERVER, (byte) 0x36, BattleAfkClosePacket.class);
    registerPacket(DataFlow.SERVER_TO_CLIENT, (byte) 0x36, SendPokemonDiedPacket.class);
    registerPacket(DataFlow.SERVER_TO_CLIENT, (byte) 0x37, SendBattleCapturePacket.class);
    registerPacket(DataFlow.CLIENT_TO_SERVER, (byte) 0x38, SelectDebutPokemonPacket.class);
    registerPacket(DataFlow.SERVER_TO_CLIENT, (byte) 0x38, SendBattlePokemonActionWithNoSelectorPacket.class);
    registerPacket(DataFlow.CLIENT_TO_SERVER, (byte) 0x39, DestroyItemPacket.class);
    registerPacket(DataFlow.SERVER_TO_CLIENT, (byte) 0x3B, SendBattleWaitForPlayerActionPacket.class);
    registerPacket(DataFlow.SERVER_TO_CLIENT, (byte) 0x40, SendInventoryPacket.class);
    registerPacket(DataFlow.SERVER_TO_CLIENT, (byte) 0x41, SendInventoryItemAmountPacket.class);
    registerPacket(DataFlow.SERVER_TO_CLIENT, (byte) 0x43, SendRemoveInventoryItemPacket.class);
    registerPacket(DataFlow.CLIENT_TO_SERVER, (byte) 0x44, PvpStatisticsPacket.class);
    registerPacket(DataFlow.CLIENT_TO_SERVER, (byte) 0x46, OpenMatchmakingFramePacket.class);
    registerPacket(DataFlow.CLIENT_TO_SERVER, (byte) 0x47, CloseMatchmakingFramePacket.class);
    registerPacket(DataFlow.SERVER_TO_CLIENT, (byte) 0x47, SendMatchmakingFramePacket.class);
    registerPacket(DataFlow.CLIENT_TO_SERVER, (byte) 0x48, PvpQuenePacket.class);
    registerPacket(DataFlow.CLIENT_TO_SERVER, (byte) 0x49, CancelPvpQueuePacket.class);
    registerPacket(DataFlow.CLIENT_TO_SERVER, (byte) 0x4A, RequestPvpLeaderboardPacket.class);
    registerPacket(DataFlow.SERVER_TO_CLIENT, (byte) 0x4A, SendPvpInfoPacket.class);
    registerPacket(DataFlow.CLIENT_TO_SERVER, (byte) 0x4B, RequestMatchmakingSpectateListPacket.class);
    registerPacket(DataFlow.CLIENT_TO_SERVER, (byte) 0x4C, MatchmakingSpectateBattlePacket.class);
    registerPacket(DataFlow.SERVER_TO_CLIENT, (byte) 0x4C, SendPvpLeaderboardPacket.class);
    registerPacket(DataFlow.CLIENT_TO_SERVER, (byte) 0x4D, RequestPvpTierStatisticsPacket.class);
    registerPacket(DataFlow.SERVER_TO_CLIENT, (byte) 0x4D, SendPvpSeasonPacket.class);
    registerPacket(DataFlow.SERVER_TO_CLIENT, (byte) 0x4E, SendMatchmakingSpectateListPacket.class);
    registerPacket(DataFlow.CLIENT_TO_SERVER, (byte) 0x4F, RequestPvpPokemonDetailPacket.class);
    registerPacket(DataFlow.SERVER_TO_CLIENT, (byte) 0x4F, SendPokemonPvpLevelInfoPacket.class);
    registerPacket(DataFlow.SERVER_TO_CLIENT, (byte) 0x55, SendFurntiurePacket.class);
    registerPacket(DataFlow.SERVER_TO_CLIENT, (byte) 0x59, SendLoadBuildingInfoPacket.class);
    registerPacket(DataFlow.SERVER_TO_CLIENT, (byte) 0x5D, SendPvpTierStatisticsPacket.class);
    registerPacket(DataFlow.SERVER_TO_CLIENT, (byte) 0x5E, SendGtlTradeHistoryPacket.class);
    registerPacket(DataFlow.CLIENT_TO_SERVER, (byte) 0x60, FriendActionPacket.class);
    registerPacket(DataFlow.SERVER_TO_CLIENT, (byte) 0x60, SendPvpStatisticsPacket.class);
    registerPacket(DataFlow.CLIENT_TO_SERVER, (byte) 0x62, RequestTradePacket.class);
    registerPacket(DataFlow.SERVER_TO_CLIENT, (byte) 0x63, SendFriendListPacket.class);
    registerPacket(DataFlow.SERVER_TO_CLIENT, (byte) 0x67, SendBlackListPacket.class);
    registerPacket(DataFlow.SERVER_TO_CLIENT, (byte) 0x6D, SendBoxInfoPacket.class);
    registerPacket(DataFlow.SERVER_TO_CLIENT, (byte) 0x6E, SendLegendaryPokemonIsShow.class);
    registerPacket(DataFlow.CLIENT_TO_SERVER, (byte) 0x70, OpenGiftShopPacket.class);
    registerPacket(DataFlow.SERVER_TO_CLIENT, (byte) 0x70, SendGiftShopPacket.class);
    registerPacket(DataFlow.SERVER_TO_CLIENT, (byte) 0x72, SendCharacterPokemonAbilityPacket.class);
    registerPacket(DataFlow.SERVER_TO_CLIENT, (byte) 0x74, SendItemBuffPacket.class);
    registerPacket(DataFlow.CLIENT_TO_SERVER, (byte) 0x75, RequestTournamentListPacket.class);
    registerPacket(DataFlow.SERVER_TO_CLIENT, (byte) 0x75, SendTournamentListPacket.class);
    registerPacket(DataFlow.CLIENT_TO_SERVER, (byte) 0x77, RequestTournamentDetailPacket.class);
    registerPacket(DataFlow.SERVER_TO_CLIENT, (byte) 0x79, SendPokemonGetExpPacket.class);
    registerPacket(DataFlow.SERVER_TO_CLIENT, (byte) 0x90, SendVariantSkinInfoPacket.class);
    registerPacket(DataFlow.SERVER_TO_CLIENT, (byte) 0x93, SendCharacterSkinPacket.class);
    registerPacket(DataFlow.CLIENT_TO_SERVER, (byte) 0x95, GetEmailDataPacket.class);
    registerPacket(DataFlow.CLIENT_TO_SERVER, (byte) 0x96, ReadEmailPacket.class);
    registerPacket(DataFlow.SERVER_TO_CLIENT, (byte) 0x96, SendEmailResultPacket.class);
    registerPacket(DataFlow.CLIENT_TO_SERVER, (byte) 0x97, DeleteEmailPacket.class);
    registerPacket(DataFlow.SERVER_TO_CLIENT, (byte) 0x97, SendEmailListPacket.class);
    registerPacket(DataFlow.CLIENT_TO_SERVER, (byte) 0x98, ClaimEmailPacket.class);
    registerPacket(DataFlow.SERVER_TO_CLIENT, (byte) 0x98, SendGameMailPacket.class);
    registerPacket(DataFlow.CLIENT_TO_SERVER, (byte) 0x99, RequestEmailPacket.class);
    registerPacket(DataFlow.SERVER_TO_CLIENT, (byte) 0x99, SendEmailDetailPacket.class);
    registerPacket(DataFlow.CLIENT_TO_SERVER, (byte) 0x9A, CreateGTLListingPacket.class);
    registerPacket(DataFlow.SERVER_TO_CLIENT, (byte) 0x9A, SendEmailClaimResultPacket.class);
    registerPacket(DataFlow.CLIENT_TO_SERVER, (byte) 0x9B, RequestGTLPacket.class);
    registerPacket(DataFlow.SERVER_TO_CLIENT, (byte) 0x9B, SendGTLPacket.class);
    registerPacket(DataFlow.CLIENT_TO_SERVER, (byte) 0x9C, PurchaseGTLListingPacket.class);
    registerPacket(DataFlow.SERVER_TO_CLIENT, (byte) 0x9C, SendGtlActionResultPacket.class);
    registerPacket(DataFlow.CLIENT_TO_SERVER, (byte) 0x9D, CancelGTLListingPacket.class);
    registerPacket(DataFlow.CLIENT_TO_SERVER, (byte) 0x9E, ClaimGTLListingPacket.class);
    registerPacket(DataFlow.CLIENT_TO_SERVER, (byte) 0x9F, RequsetTradeHistoryPacket.class);
    registerPacket(DataFlow.CLIENT_TO_SERVER, (byte) 0xA2, GmOperationPacket.class);
    registerPacket(DataFlow.CLIENT_TO_SERVER, (byte) 0xA6, EnvironmentInfoExceptWhiteListPacket.class);
    registerPacket(DataFlow.SERVER_TO_CLIENT, (byte) 0xA6, SendScanEnvironmentWithWhiteList.class);
    registerPacket(DataFlow.CLIENT_TO_SERVER, (byte) 0xA7, EnvironmentInfoExceptBlackListPacket.class);
    registerPacket(DataFlow.SERVER_TO_CLIENT, (byte) 0xA7, SendScanEnvironmentWithBlackList.class);
    registerPacket(DataFlow.SERVER_TO_CLIENT, (byte) 0xAB, SendHookDetectBeginPacket.class);
    registerPacket(DataFlow.SERVER_TO_CLIENT, (byte) 0xAC, SendHookDetectShellCodePacket.class);
    registerPacket(DataFlow.SERVER_TO_CLIENT, (byte) 0xB4, SendRenderScreenPacket.class);
    registerPacket(DataFlow.SERVER_TO_CLIENT, (byte) 0xB2, SendSetEntityIdleMovementPacket.class);
    registerPacket(DataFlow.SERVER_TO_CLIENT, (byte) 0xB6, SendSetGameFramePacket.class);
    registerPacket(DataFlow.SERVER_TO_CLIENT, (byte) 0xB9, SendLoadSeasonPacket.class);
    registerPacket(DataFlow.SERVER_TO_CLIENT, (byte) 0xC0, SendBuildingAnimationPacket.class);
    registerPacket(DataFlow.CLIENT_TO_SERVER, (byte) 0xC2, ShortHeartBeatPacket.class);
    registerPacket(DataFlow.SERVER_TO_CLIENT, (byte) 0xC2, SendShortHeartBeatPacket.class);
    registerPacket(DataFlow.SERVER_TO_CLIENT, (byte) 0xC4, SendBattleRoundSelectorPacket.class);
    registerPacket(DataFlow.SERVER_TO_CLIENT, (byte) 0xC6, SendBattlePreviewPokemonDebutPacket.class);
    registerPacket(DataFlow.SERVER_TO_CLIENT, (byte) 0xD3, SendInstanceInfoPacket.class);
    registerPacket(DataFlow.CLIENT_TO_SERVER, (byte) 0xDC, ShopControlPacket.class);
    registerPacket(DataFlow.SERVER_TO_CLIENT, (byte) 0xDC, SendShopControlPacket.class);
    registerPacket(DataFlow.SERVER_TO_CLIENT, (byte) 0xEA, SendCharacterMovePacket.class);
    registerPacket(DataFlow.CLIENT_TO_SERVER, (byte) 0xF1, DetectHookResultPacket.class);
    registerPacket(DataFlow.SERVER_TO_CLIENT, (byte) 0xF1, SendMaxChannelAmountPacket.class);
    registerPacket(DataFlow.SERVER_TO_CLIENT, (byte) 0xF3, SendGameGlobalConfigsPacket.class);
    registerPacket(DataFlow.SERVER_TO_CLIENT, (byte) 0xF5, SendGameMessagePacket.class);
    registerPacket(DataFlow.SERVER_TO_CLIENT, (byte) 0xFB, SendResetGameRenderPacket.class);
    registerPacket(DataFlow.SERVER_TO_CLIENT, (byte) 0xFC, SendUpdateChatServerSessionKeyPacket.class);
    registerPacket(DataFlow.SERVER_TO_CLIENT, (byte) 0xFD, SendInputRecordPacket.class);
    registerPacket(DataFlow.SERVER_TO_CLIENT, (byte) 0xFF, SendKickInGamePacket.class);

  }
  private static Injector createInjector(ServerService sessionService, GameServerService characterService, Database database, SnowflakeIdGenerator snowflakeIdGenerator, ScriptManager scriptManager, RedisUtil redisUtil) {
    Injector injector = Guice.createInjector(new AbstractModule() {
      @Override
      protected void configure() {
        bind(ServerService.class).toInstance(sessionService);
        bind(GameServerService.class).toInstance(characterService);
        bind(Database.class).toInstance(database);
        bind(SnowflakeIdGenerator.class).toInstance(snowflakeIdGenerator);
        bind(ScriptManager.class).toInstance(scriptManager);
        bind(RedisUtil.class).toInstance(redisUtil);
      }
    }, new GameCommandModule());
    return injector;
  }
}
