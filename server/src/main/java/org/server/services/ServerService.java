package org.server.services;

import com.github.maltalex.ineter.base.IPAddress;
import org.pokemmo.db.Database;
import org.pokemmo.db.jooq.tables.records.AccountRecord;
import org.pokemmo.db.jooq.tables.records.AccountContextRecord;
import org.pokemmo.db.jooq.tables.records.ServerTokenRecord;
import org.server.util.UuidUtils;
import lombok.RequiredArgsConstructor;
import org.jooq.postgres.extensions.types.Inet;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import static org.pokemmo.db.jooq.Tables.*;

@RequiredArgsConstructor
public class ServerService {
   private final Database database;
  /**
   * Generate a session key for the given user id.
   * This method does not check for permissions or anything else.
   * It just generates a session key.
   *
   * @return the session key or null if the user does not exist
   */
  public byte[] generateSessionKey(int nodeId, Integer accountId, String nodeType,String tokenType, IPAddress userIp) {
    AccountRecord accountRecord = database.ctx()
        .select()
        .from(ACCOUNT)
        .where(ACCOUNT.ACCOUNT_ID.eq(accountId))
        .fetchOneInto(ACCOUNT);
    if (accountRecord == null) {
      return null;
    }
    // 删除旧的token
    database.ctx()
        .deleteFrom(SERVER_TOKEN)
        .where(SERVER_TOKEN.ACCOUNT_ID.eq(accountId))
            .and(SERVER_TOKEN.NODE_TYPE.eq(nodeType))
        .execute();
    // 生成新的token
    ServerTokenRecord token = database.ctx().newRecord(SERVER_TOKEN);
    token.setNodeId(nodeId);
    token.setNodeType(nodeType);
    token.setAccountId(accountId);
    token.setAccountIp(Inet.inet(userIp.toInetAddress()));
    token.setTokenType(tokenType);
    token.store();
    token.refresh();//刷新默认值
    return UuidUtils.asBytes(token.getToken());
  }

  public boolean validateSessionKey(Integer accountId, String nodeType,String tokenType,IPAddress userIp, byte[] sessionKey) {
    ServerTokenRecord token = database.ctx()
        .select()
        .from(SERVER_TOKEN)
        .where(SERVER_TOKEN.TOKEN.eq(UuidUtils.asUuid(sessionKey))) // token is validated in the query no need to check it again
        .and(SERVER_TOKEN.ACCOUNT_ID.eq(accountId))
        .and(SERVER_TOKEN.NODE_TYPE.eq(nodeType))
        .and(SERVER_TOKEN.TOKEN_TYPE.eq(tokenType))
        .and(SERVER_TOKEN.ACCOUNT_IP.eq(Inet.inet(userIp.toInetAddress())))
        .fetchOneInto(SERVER_TOKEN);
    if (token == null) {
      return false;
    }
    //检测token的时间是否过期
    if(token.getCreatedAt().isBefore(LocalDateTime.now().minusMinutes(5))){
      return false;
    }
    token.delete();
    return true;
  }
  public boolean updateAccountContext(AccountContextRecord record) {
    if (record.getAccountId() == null) {
      return false;
    }
    //先检测account是否存在
    AccountRecord accountRecord = database.ctx()
            .select()
            .from(ACCOUNT)
            .where(ACCOUNT.ACCOUNT_ID.eq(record.getAccountId()))
            .fetchOneInto(ACCOUNT);
    if (accountRecord == null) {
      return false;
    }
    AccountContextRecord accountContextRecord = database.ctx()
            .select()
            .from(ACCOUNT_CONTEXT)
            .where(ACCOUNT_CONTEXT.ACCOUNT_ID.eq(record.getAccountId()))
            .fetchOneInto(ACCOUNT_CONTEXT);
    if (accountContextRecord == null) {
      AccountContextRecord newRecord = database.ctx().newRecord(ACCOUNT_CONTEXT);
      newRecord.setAccountId(record.getAccountId());
      if(record.getCharacterId() !=null) {
        newRecord.setCharacterId(record.getCharacterId());
      }
      if(record.getGameNodeId() !=null){
        newRecord.setGameNodeId(record.getGameNodeId());
      }
      if(record.getNodeName() !=null){
        newRecord.setNodeName(record.getNodeName());
      }
      if(record.getServerId() !=null) {
        newRecord.setServerId(record.getServerId());
      }
      if(record.getIsLogOut() !=null){
        newRecord.setIsLogOut(record.getIsLogOut());
      }
      newRecord.setUpdateAt(LocalDateTime.now());
      newRecord.store();
      newRecord.refresh();
    }
    else{
      if(record.getCharacterId() !=null){
        accountContextRecord.setCharacterId(record.getCharacterId());
      }
      if(record.getGameNodeId() !=null){
        accountContextRecord.setGameNodeId(record.getGameNodeId());
      }
      if(record.getNodeName() !=null){
        accountContextRecord.setNodeName(record.getNodeName());
      }
      if(record.getServerId() !=null) {
        accountContextRecord.setServerId(record.getServerId());
      }
      if(record.getIsLogOut() !=null){
        accountContextRecord.setIsLogOut(record.getIsLogOut());
      }
      accountContextRecord.setUpdateAt(LocalDateTime.now(ZoneOffset.UTC));
      accountContextRecord.update();
      accountContextRecord.refresh();
    }
    return true;
  }
}