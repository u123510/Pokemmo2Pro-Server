package org.pokemmo.loginserver.protocol;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import org.pokemmo.loginserver.protocol.packets.s2c.*;
import org.pokemmo.loginserver.service.LoginService;
import org.server.DataFlow;
import org.server.Protocol;
import org.pokemmo.loginserver.LoginState;
import org.pokemmo.loginserver.protocol.packets.c2s.JoinGameServerPacket;
import org.pokemmo.loginserver.protocol.packets.c2s.LoginPacket;
import org.pokemmo.loginserver.protocol.packets.c2s.RequestGameNodeListPacket;
import org.server.redis.RedisUtil;
import org.server.services.ServerService;
import io.netty.util.AttributeKey;

public class LoginProtocol extends Protocol {
  public static final AttributeKey<LoginState> ATTRIBUTE_LOGIN_STATE = AttributeKey.valueOf("login_state");
  public static final AttributeKey<Integer> ATTRIBUTE_ACCOUNT_ID = AttributeKey.valueOf("account_id");

  public LoginProtocol(LoginService loginService, ServerService sessionService, RedisUtil redisUtil) {
    super(16, true, false, createInjector(loginService, sessionService,redisUtil));
    registerPacket(DataFlow.SERVER_TO_CLIENT, (byte) 0x01, LoginResultPacket.class);
    registerPacket(DataFlow.CLIENT_TO_SERVER, (byte) 0x02, RequestGameNodeListPacket.class);
    registerPacket(DataFlow.CLIENT_TO_SERVER, (byte) 0x03, JoinGameServerPacket.class);
    registerPacket(DataFlow.SERVER_TO_CLIENT, (byte) 0x03, SendGameNodeServerListPacket.class);
    registerPacket(DataFlow.SERVER_TO_CLIENT, (byte) 0x05, SendLoginKickPacket.class);
    registerPacket(DataFlow.SERVER_TO_CLIENT, (byte) 0x07, SendUpdateLoginCredentialsKey.class);
    registerPacket(DataFlow.SERVER_TO_CLIENT, (byte) 0x08, SendMfaChangePacket.class);
    registerPacket(DataFlow.CLIENT_TO_SERVER, (byte) 0x11, LoginPacket.class);
    registerPacket(DataFlow.SERVER_TO_CLIENT, (byte) 0x22, SendGameNodeListPacket.class);
    registerPacket(DataFlow.SERVER_TO_CLIENT, (byte) 0x26, SendReconnectPacket.class);
  }

  private static Injector createInjector(LoginService loginService, ServerService sessionService, RedisUtil redisUtil) {
    return Guice.createInjector(new AbstractModule() {
      @Override
      protected void configure() {
        bind(LoginService.class).toInstance(loginService);
        bind(ServerService.class).toInstance(sessionService);
        bind(RedisUtil.class).toInstance(redisUtil);
      }
    });
  }
}
