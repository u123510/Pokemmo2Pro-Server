package org.pokemmo.loginserver.protocol.packets.c2s;

import com.github.maltalex.ineter.base.IPAddress;
import com.google.inject.Inject;
import org.pokemmo.db.jooq.tables.records.AccountContextRecord;
import org.pokemmo.loginserver.service.LoginService;
import org.server.IncomingPacket;
import org.server.Session;
import org.server.bytes.ByteBufEx;
import org.pokemmo.loginserver.LoginState;
import org.pokemmo.loginserver.protocol.LoginProtocol;
import org.pokemmo.loginserver.protocol.packets.s2c.SendGameNodeServerListPacket;
import org.server.node.JoinableSeverData;
import org.server.node.ServerNode;
import org.server.services.ServerService;
import lombok.extern.slf4j.Slf4j;
import java.net.InetAddress;
import java.util.List;
import java.util.Optional;

@Slf4j
public class JoinGameServerPacket extends IncomingPacket {
  @Inject
  private LoginService loginService;
  @Inject
  private ServerService sessionService;
  private byte gameNodeId;
  @Override
  public void decode(ByteBufEx buffer) {
    gameNodeId = buffer.readByte();
  }

  @Override
  public void handle(Session session) throws Exception {
    LoginState loginState = session.attr(LoginProtocol.ATTRIBUTE_LOGIN_STATE).get();
    if (loginState != LoginState.AUTHED) {
      session.send(new SendGameNodeServerListPacket(loginState));
      return;
    }
    int accountId = session.attr(LoginProtocol.ATTRIBUTE_ACCOUNT_ID).get();
    Optional<ServerNode> gameServerNodeOpt = loginService.getGameSeverNode(gameNodeId, accountId);
    if (gameServerNodeOpt.isEmpty()) {
      log.error("账号 {} 尝试进入游戏节点 {} 但节点已关闭", accountId, gameNodeId);
      session.send(new SendGameNodeServerListPacket(LoginState.SERVER_DOWN));
      return;
    }
    ServerNode gameServerNode = gameServerNodeOpt.get();
    if (!gameServerNode.isJoinAble()) {
      log.error("账号 {} 尝试进入游戏节点 {} 但节点已关闭不足", accountId, gameNodeId);
      session.send(new SendGameNodeServerListPacket(LoginState.SYSTEM_ERROR));
      return;
    }
    String gameNodeName = gameServerNode.getName();
    //像数据库更新当前登录的节点
    AccountContextRecord accountContextRecord = new AccountContextRecord();
    accountContextRecord.setAccountId(accountId);
    accountContextRecord.setGameNodeId((short) gameNodeId);
    accountContextRecord.setNodeName(gameNodeName);
    boolean success = sessionService.updateAccountContext(accountContextRecord);
    if (!success) {
      log.error("账号 {} 尝试进入游戏节点 {} 但更新账号上下文失败", accountId, gameNodeId);
      session.send(new SendGameNodeServerListPacket(LoginState.SYSTEM_ERROR));
      return;
    }
    byte[] gameSessionKey = sessionService.generateSessionKey(gameNodeId, accountId,"game","login",session.getRemoteAddress());
    if (gameSessionKey == null) {
      log.error("账号 {} 尝试进入游戏节点 {} 但会话密钥生成失败", accountId, gameNodeId);
      session.send(new SendGameNodeServerListPacket(LoginState.SYSTEM_ERROR));
      return;
    }
    InetAddress localAddress = InetAddress.getLocalHost();
    List<JoinableSeverData> gameNodeServers = loginService.getOnlineGameNodeServers(gameNodeId);
    session.send(new SendGameNodeServerListPacket(
        LoginState.AUTHED,
        gameNodeId,
        accountId,
            gameSessionKey,
        IPAddress.of(localAddress),
        localAddress.getHostName(),
        gameServerNode.getPort(),
        gameNodeServers
    ));
  }
}