package org.pokemmo.loginserver.protocol.packets.c2s;

import com.google.inject.Inject;
import org.pokemmo.db.jooq.tables.GameNode;
import org.pokemmo.loginserver.service.LoginService;
import org.server.IncomingPacket;
import org.server.Session;
import org.server.bytes.ByteBufEx;
import org.pokemmo.loginserver.LoginState;
import org.pokemmo.loginserver.protocol.LoginProtocol;
import org.pokemmo.loginserver.protocol.packets.s2c.SendGameNodeListPacket;
import org.server.node.ServerNode;

import java.util.List;

public class RequestGameNodeListPacket extends IncomingPacket {
  @Inject
  private LoginService loginService;
  @Override
  public void decode(ByteBufEx buffer) {
  }
  @Override
  public void handle(Session session) throws Exception {
    if (session.attr(LoginProtocol.ATTRIBUTE_LOGIN_STATE).get() != LoginState.AUTHED) {
      throw new IllegalStateException("账号认证失败");
    }
    Integer accountId = session.attr(LoginProtocol.ATTRIBUTE_ACCOUNT_ID).get();
    if (accountId == null) {
      throw new IllegalStateException("账号ID未设置");
    }
    List<ServerNode> gameServerNodes = loginService.getGameNodes(accountId);
    session.send(new SendGameNodeListPacket(gameServerNodes));
  }
}