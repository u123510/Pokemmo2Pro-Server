package org.pokemmo.loginserver.service;


import com.github.maltalex.ineter.base.IPAddress;
import lombok.Getter;
import org.jooq.Record;
import org.jooq.Result;
import org.pokemmo.db.Database;
import org.pokemmo.db.jooq.tables.records.*;
import org.pokemmo.loginserver.LoginState;
import lombok.RequiredArgsConstructor;
import org.server.node.JoinableSeverData;
import org.server.node.ServerNode;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.pokemmo.db.jooq.Tables.*;

@RequiredArgsConstructor
public class LoginService {
  private final Database database;
  // 静态变量用于缓存当前 IV 和生成时间
  private static volatile byte[] currentIv;
  private static volatile Instant ivGeneratedTime;
  private static final Object ivLock = new Object();
  public LoginState login(Integer userId, String password,boolean useToken)
  {
    AccountRecord account = database.ctx()
        .select()
        .from(ACCOUNT)
        .where(ACCOUNT.ACCOUNT_ID.eq(userId))
        .fetchOneInto(ACCOUNT);
    if (account == null) {
      return LoginState.INVALID_PASSWORD;
    }
    byte[] passwordHash = account.getPassword();
    byte[] passwordHashFromInput = hexToBytes(password);
    if (Arrays.equals(passwordHash, passwordHashFromInput)) {
      return LoginState.AUTHED;
    }
    if(useToken){
      return LoginState.INVALID_SAVED_CREDENTIALS;
    }
    return LoginState.INVALID_PASSWORD;
  }
  private static byte[] hexToBytes(String hex) {
    int len = hex.length();
    byte[] data = new byte[len / 2];
    for (int i = 0; i < len; i += 2) {
      data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
          + Character.digit(hex.charAt(i+1), 16));
    }
    return data;
  }
  public AccountContextRecord getAccountContext(int accountId){
    return database.ctx()
        .select()
        .from(ACCOUNT_CONTEXT)
        .where(ACCOUNT_CONTEXT.ACCOUNT_ID.eq(accountId))
        .fetchOneInto(ACCOUNT_CONTEXT);
  }
  public void deleteAccountContext(int accountId){
    database.ctx()
        .delete(ACCOUNT_CONTEXT)
        .where(ACCOUNT_CONTEXT.ACCOUNT_ID.eq(accountId))
        .execute();
  }
  public void deleteServerToken(int accountId,String nodeType,String tokenType){
    database.ctx()
        .delete(SERVER_TOKEN)
        .where(SERVER_TOKEN.ACCOUNT_ID.eq(accountId))
            .and(SERVER_TOKEN.NODE_TYPE.eq(nodeType))
            .and(SERVER_TOKEN.TOKEN_TYPE.eq(tokenType))
        .execute();
  }
  private static byte[] getWeeklyIv() {
    Instant now = Instant.now();
    // 检查当前IV是否存在且仍在本周有效
    if (currentIv != null && ivGeneratedTime != null) {
      Instant weekStart = now.truncatedTo(ChronoUnit.WEEKS);
      if (ivGeneratedTime.isAfter(weekStart)) {
        return currentIv;
      }
    }
     // 线程安全地生成新的IV
       synchronized (ivLock) {
      // 双重检查锁定
      if (currentIv != null && ivGeneratedTime != null) {
        Instant weekStart = now.truncatedTo(ChronoUnit.WEEKS);
        if (ivGeneratedTime.isAfter(weekStart)) {
          return currentIv;
        }
      }
      // 生成新的随机IV
      byte[] newIv = new byte[16];
      SecureRandom secureRandom = new SecureRandom();
      secureRandom.nextBytes(newIv);

      // 更新缓存
      currentIv = newIv;
      ivGeneratedTime = now;

      return newIv;
    }
  }
  public String passwordToCredentialsKey(String accountName,String password) {
    try {
      // 从accountName生成AES密钥
      MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
      byte[] keyBytes = sha256.digest(accountName.getBytes("UTF-8"));
      // 使用前32字节作为AES-256密钥
      keyBytes = Arrays.copyOf(keyBytes, 32);
      SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "AES");
      // 获取每周生成一次的IV
      byte[] iv = getWeeklyIv();
      IvParameterSpec ivSpec = new IvParameterSpec(iv);
      // 初始化AES加密器
      Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
      cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec);
      // 加密密码
      byte[] encryptedPassword = cipher.doFinal(password.getBytes("UTF-8"));
      // 组合IV和密文
      byte[] combined = new byte[iv.length + encryptedPassword.length];
      System.arraycopy(iv, 0, combined, 0, iv.length);
      System.arraycopy(encryptedPassword, 0, combined, iv.length, encryptedPassword.length);
      // Base64编码并返回
      return Base64.getEncoder().encodeToString(combined);
    } catch (Exception e) {
      throw new RuntimeException("Failed to encrypt password", e);
    }
  }
  public Optional<Integer> getAccountIdByName(String accountName) {
    AccountRecord account = database.ctx()
        .select()
        .from(ACCOUNT)
        .where(ACCOUNT.ACCOUNT_NAME.eq(accountName))
        .fetchOneInto(ACCOUNT);
    if (account == null) {
      return Optional.empty();
    }

    return Optional.ofNullable(account.getAccountId());
  }
  public String getPasswordByCredentialsKey(String username, String token) {
    try {
      // Base64解码token字符串，得到组合字节数组（IV + 加密密码）
      byte[] combined = Base64.getDecoder().decode(token);

      // 从组合字节数组中分离IV和加密的密码（IV长度为16字节）
      byte[] iv = Arrays.copyOfRange(combined, 0, 16);
      byte[] encryptedPassword = Arrays.copyOfRange(combined, 16, combined.length);

      // 从username生成AES密钥（与passwordToCredentialsKey方法一致）
      MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
      byte[] keyBytes = sha256.digest(username.getBytes("UTF-8"));
      keyBytes = Arrays.copyOf(keyBytes, 32); // 使用前32字节作为AES-256密钥
      SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "AES");
      // 初始化AES解密器（使用与加密相同的算法和IV）
      Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
      cipher.init(Cipher.DECRYPT_MODE, secretKey, new IvParameterSpec(iv));
      // 解密密码
      byte[] decryptedPassword = cipher.doFinal(encryptedPassword);
      // 将解密后的字节数组转换为字符串并返回
      return new String(decryptedPassword, "UTF-8");
    } catch (Exception e) {
      return "";
    }
  }
  public List<ServerNode> getGameNodes(Integer accountId) {
    Result<org.jooq.Record> gameServers = database.ctx()
            .select().from(GAME_NODE)
            .fetch();
    return gameServers.map(record -> new ServerNode(
            record.get(GAME_NODE.NODE_ID).byteValue(),
            record.get(GAME_NODE.NODE_NAME),
            record.get(GAME_NODE.PORT),
            canJoinGameNode(record.get(GAME_NODE.NODE_ID).byteValue(), accountId)
    ));
  }

  public boolean canJoinGameNode(byte gameNodeId, int userId) {
    // 查询节点是否存在
    GameNodeRecord nodeRecord = database.ctx()
            .select().from(GAME_NODE)
            .where(GAME_NODE.NODE_ID.eq((int) gameNodeId))
            .fetchOneInto(GameNodeRecord.class);
    if (nodeRecord == null) {
      return false;
    }
    //查询所属节点的在线服务器
    OnlineGameNodeServerRecord gameNodeServerRecord = database.ctx()
            .select().from(ONLINE_GAME_NODE_SERVER)
            .where(ONLINE_GAME_NODE_SERVER.NODE_ID.eq((int) gameNodeId))
            .fetchOneInto(OnlineGameNodeServerRecord.class);
    if (gameNodeServerRecord == null) {
      return false;
    }
    AccountRecord account = database.ctx()
            .select().from(ACCOUNT)
            .where(ACCOUNT.ACCOUNT_ID.eq(userId))
            .fetchOneInto(AccountRecord.class);
    if (account == null) {
      return false;
    }
    //账号的权限应该大于等于节点的权限
    return account.getLoginPermission() >= nodeRecord.getPermissionId();
  }

  public List<JoinableSeverData> getOnlineGameNodeServers(int gameNodeId) {
    Result<org.jooq.Record> gameServerNodes = database.ctx()
            .select().from(ONLINE_GAME_NODE_SERVER)
            .join(GAME_NODE).on(GAME_NODE.NODE_ID.eq(gameNodeId))
            .fetch();
    return gameServerNodes
            .map(record -> record.into(ONLINE_GAME_NODE_SERVER))
            .stream().map(r -> new JoinableSeverData(
                    IPAddress.of(r.getIpv4().address()),
                    IPAddress.of(r.getIpv6().address()),
                    r.getPort(),
                    (byte)40
            )).toList();
  }
  public JoinableSeverData getGameNodeServer(int gameNodeId, short serverId) {
    OnlineGameNodeServerRecord gameNodeServerRecord = database.ctx()
            .select().from(ONLINE_GAME_NODE_SERVER)
            .join(GAME_NODE).on(GAME_NODE.NODE_ID.eq(gameNodeId))
            .fetchOneInto(OnlineGameNodeServerRecord.class);
    if (gameNodeServerRecord == null) {
      return null;
    }
    return new JoinableSeverData(
            IPAddress.of(gameNodeServerRecord.getIpv4().address()),
            IPAddress.of(gameNodeServerRecord.getIpv6().address()),
            gameNodeServerRecord.getPort(),
            (byte)40
    );
  }

  public Optional<ServerNode> getGameSeverNode(byte gameNodeId, int userId) {
    Record gameServerNode = database.ctx()
            .select().from(GAME_NODE)
            .where(GAME_NODE.NODE_ID.eq((int) gameNodeId))
            .fetchOne();
    if (gameServerNode == null) {
      return Optional.empty();
    }
    return Optional.of(new ServerNode(
            gameServerNode.get(GAME_NODE.NODE_ID).byteValue(),
            gameServerNode.get(GAME_NODE.NODE_NAME),
            gameServerNode.get(GAME_NODE.PORT),
            canJoinGameNode(gameServerNode.get(GAME_NODE.NODE_ID).byteValue(), userId)
    ));
  }
}
