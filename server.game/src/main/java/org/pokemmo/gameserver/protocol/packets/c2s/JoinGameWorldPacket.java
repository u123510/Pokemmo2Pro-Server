package org.pokemmo.gameserver.protocol.packets.c2s;

import com.google.inject.Inject;
import org.pokemmo.db.jooq.tables.records.AccountContextRecord;
import org.pokemmo.gameserver.game.account.AccountData;
import org.pokemmo.gameserver.game.character.CharacterManager;
import org.pokemmo.gameserver.game.pool.GameSessionPool;
import org.server.union.language.LanguageType;
import org.pokemmo.gameserver.game.platform.CpuArchitectureType;
import org.pokemmo.gameserver.game.platform.CpuBitType;
import org.pokemmo.gameserver.game.platform.PlatformType;
import org.pokemmo.gameserver.game.rom.RomInfo;
import org.pokemmo.gameserver.game.rom.RomType;
import org.pokemmo.gameserver.script.ScriptManager;
import org.pokemmo.gameserver.services.GameServerService;
import org.pokemmo.gameserver.util.SnowflakeIdGenerator;
import org.server.IncomingPacket;
import org.server.Session;
import org.server.bytes.ByteBufEx;
import org.pokemmo.gameserver.protocol.GameProtocol;
import org.pokemmo.gameserver.protocol.packets.s2c.SendGameGlobalConfigsPacket;
import org.pokemmo.gameserver.protocol.packets.s2c.SendJoinGameWorldResponsePacket;
import org.server.services.ServerService;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
//加入游戏的主世界
public class JoinGameWorldPacket extends IncomingPacket {
  @Inject
  private ServerService sessionService;
  @Inject
  private ScriptManager scriptManager;
  @Inject
  private SnowflakeIdGenerator snowflakeIdGenerator;
  @Inject
  private GameServerService characterService;
  private boolean isReconnect;
  private int accountId;
  private long characterId;
  private byte[] reconnectGameSessionKey;
  private byte[] gameSessionKey;
  private byte[] mac;
  private int clientRevision; // hardcoded into the client
  private int installationRevision; // revision.txt in the game directory
  private LanguageType countryType; // language the client is set to
  private short visableChatTypes; // bitmask of languages the client supports
  private short ignoreLanguages; // bitmask of languages the client ignores
  private byte romMask; // bitmask of roms that are loaded by the client used for region checks
  private List<RomInfo> roms;
  private Map<Byte, String> clientInfo;
  private PlatformType platformType;
  private CpuArchitectureType cpuArchitectureType;
  private CpuBitType cpuBitType;
  private byte unk1;
  private byte[] unk2;

  @Override
  public void decode(ByteBufEx buffer) {
    isReconnect = buffer.readBoolean();
    if (isReconnect) {
      characterId = buffer.readLongLE();
      reconnectGameSessionKey = new byte[buffer.readByte()];
      buffer.readBytes(reconnectGameSessionKey);
    }
    else{
      accountId = buffer.readIntLE();
      gameSessionKey = new byte[buffer.readByte()];
      buffer.readBytes(gameSessionKey);
    }
    mac = buffer.readByteArray(6);
    clientRevision = buffer.readIntLE();
    installationRevision = buffer.readIntLE();
    countryType = LanguageType.getByType(buffer.readByte());
    visableChatTypes = buffer.readShortLE();
    ignoreLanguages = buffer.readShortLE();
    romMask = buffer.readByte();
    roms = new ArrayList<>();
    int romCount = buffer.readByte();
    for (int i = 0; i < romCount; i++) {
      roms.add(new RomInfo(buffer.readUtf16LE(), buffer.readByte(), RomType.getByType(buffer.readByte())));
    }
    clientInfo = new HashMap<>();
    int clientInfoCount = buffer.readByte();
    for (int i = 0; i < clientInfoCount; i++) {
      clientInfo.put(buffer.readByte(), buffer.readUtf16LE());
    }
    platformType = PlatformType.getByType(buffer.readByte());
    cpuArchitectureType = CpuArchitectureType.getByType(buffer.readByte());
    cpuBitType = CpuBitType.getByType(buffer.readByte());
    unk1 = buffer.readByte();
    unk2 = buffer.readByteArray(32); // smth related to soundmuxer???
  }
  @Override
  public void handle(Session session) throws Exception {
    if(isReconnect){
      accountId = characterService.getAccountContextByCharacterId(characterId).getAccountId();
      if (!sessionService.validateSessionKey(accountId , "game", "reconnect",session.getRemoteAddress(), reconnectGameSessionKey)) {
        session.send(new SendJoinGameWorldResponsePacket(false));
        session.close();
        return;
      }
      else{
        //对上下文进行切换
        if(!CharacterManager.exChangeSessionText(characterId,session)){
          session.send(new SendJoinGameWorldResponsePacket(false));
          session.close();
          return;
        }
      }
    }
    else{
      if (!sessionService.validateSessionKey(accountId, "game", "login",session.getRemoteAddress(), gameSessionKey)) {
        session.send(new SendJoinGameWorldResponsePacket(false));
        session.close();
        return;
      }
    }
    AccountContextRecord accountContextRecord = characterService.getAccountContext(accountId);
    if(accountContextRecord == null){
      session.send(new SendJoinGameWorldResponsePacket(false));
      session.close();
      return;
    }
    short loginNodeId = accountContextRecord.getGameNodeId();
    log.debug("账号 {} 已加入游戏主世界.", accountId);
    if(isReconnect){
      session.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get().handleLoadGameWorldContext();
    }
    else{
      AccountData accountData = new AccountData(accountId, loginNodeId, mac, clientRevision, installationRevision, countryType, clientInfo, platformType, cpuArchitectureType, cpuBitType);
      CharacterManager characterManager = new CharacterManager.Builder()
              .setAccountData(accountData)
              .setCharacterSession(session)
              .setScriptManager(scriptManager)
              .setSnowflakeIdGenerator(snowflakeIdGenerator)
              .setCharacterService(characterService)
              .setServerService(sessionService)
              .build();
      session.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).set(characterManager);
      session.send(new SendJoinGameWorldResponsePacket(true));
    }
    GameSessionPool.registerSession(session);
    session.send(new SendGameGlobalConfigsPacket());
  }
}
