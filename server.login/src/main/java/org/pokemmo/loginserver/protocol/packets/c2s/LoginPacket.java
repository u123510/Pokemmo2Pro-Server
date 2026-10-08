package org.pokemmo.loginserver.protocol.packets.c2s;
import com.github.maltalex.ineter.base.IPAddress;
import com.google.inject.Inject;
import org.pokemmo.db.jooq.tables.records.AccountContextRecord;
import org.pokemmo.loginserver.protocol.packets.s2c.SendReconnectPacket;
import org.server.IncomingPacket;
import org.server.Session;
import org.server.bytes.ByteBufEx;
import org.pokemmo.loginserver.LoginState;
import org.pokemmo.loginserver.protocol.LoginProtocol;
import org.pokemmo.loginserver.protocol.packets.s2c.LoginResultPacket;
import org.pokemmo.loginserver.protocol.packets.s2c.SendUpdateLoginCredentialsKey;
import org.pokemmo.loginserver.service.LoginService;
import org.server.node.JoinableSeverData;
import org.server.services.ServerService;
import java.net.InetAddress;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
@Slf4j
public class LoginPacket extends IncomingPacket {
  @Inject
  private LoginService loginService;
  @Inject
  private ServerService sessionService;
  private String username;
  private boolean manualLogin;
  private byte[] hwid;
  private boolean useToken;
  private String password;
  private boolean stayLoggedIn;
  private byte[] token;
  private String language;
  private int clientRevision;
  private int gameRevision;
  private int os;
  private byte[]hardwareInfoCache ;

  @Override
  public void decode(ByteBufEx buffer) {
    username = buffer.readUtf16LE();
    manualLogin = buffer.readBoolean();
    hwid = new byte[buffer.readUnsignedByte()];
    buffer.readBytes(hwid);
    useToken = buffer.readBoolean();
    if (useToken) {
      token = new byte[buffer.readUnsignedByte()];
      buffer.readBytes(token);
    } else {
      password = buffer.readUtf16LE();
      stayLoggedIn = buffer.readBoolean();
    }
    language = buffer.readUtf16LE();
    clientRevision = buffer.readIntLE();
    gameRevision = buffer.readIntLE();
    os = buffer.readUnsignedByte();
    hardwareInfoCache = new byte[buffer.readUnsignedByte()];
    buffer.readBytes(hardwareInfoCache);
  }


  @Override
  public void handle(Session session){
    try {
      //说明重连选否
      if(manualLogin){
        int accountId = session.attr(LoginProtocol.ATTRIBUTE_ACCOUNT_ID).get();
        //需要删除数据库的账号上下文，与重连的token
        loginService.deleteAccountContext(accountId);
        //删除重连的token
        loginService.deleteServerToken(accountId,"game","reconnect");
        session.attr(LoginProtocol.ATTRIBUTE_ACCOUNT_ID).set(null);
        session.send(new LoginResultPacket(LoginState.ALREADY_LOGGED_IN));
        session.close();
        return;
      }
      //根据账号名查找账号Id
      Optional<Integer> accountIdOpt = loginService.getAccountIdByName(username);
      if (accountIdOpt.isEmpty()) {
        session.send(new LoginResultPacket(LoginState.INVALID_PASSWORD));
        session.close();
        return;
      }
      LoginState state;
      int accountId = accountIdOpt.get();
      if (useToken)
      {
        String tokenBase64 = Base64.getEncoder().encodeToString(token);
        password = loginService.getPasswordByCredentialsKey(username,tokenBase64);
        state = loginService.login(accountId, password,useToken);
        if(state == LoginState.INVALID_SAVED_CREDENTIALS){
          session.send(new SendUpdateLoginCredentialsKey(username,""));
        }
      }
      else {
        state = loginService.login(accountId, password,useToken);
        if(stayLoggedIn){
          if(state == LoginState.AUTHED)
          {
            String passwordCredentialsKey = loginService.passwordToCredentialsKey(username, password);
            session.send(new SendUpdateLoginCredentialsKey(username,passwordCredentialsKey));
          }
        }
      }
      session.attr(LoginProtocol.ATTRIBUTE_LOGIN_STATE).set(state);
      if (state == LoginState.AUTHED) {
        session.attr(LoginProtocol.ATTRIBUTE_ACCOUNT_ID).set(accountId);
      } else {
        session.attr(LoginProtocol.ATTRIBUTE_ACCOUNT_ID).set(null);
      }
      AccountContextRecord accountContextRecord = loginService.getAccountContext(accountId);
      if(accountContextRecord != null && accountContextRecord.getIsLogOut())
      {
          int reconnectAccountId = accountContextRecord.getAccountId();
          long reconnectCharacterId = accountContextRecord.getCharacterId();
          short reconnectGameNodeId = accountContextRecord.getGameNodeId();
          //重新设置要登录的账号id
          session.attr(LoginProtocol.ATTRIBUTE_ACCOUNT_ID).set(reconnectAccountId);
          byte[] reconnectGameSessionKey = sessionService.generateSessionKey(reconnectGameNodeId, reconnectAccountId,"game","reconnect",session.getRemoteAddress());
          String reconnectGameNodeName = accountContextRecord.getNodeName();
          short reconnectGameServerId = accountContextRecord.getServerId();
          JoinableSeverData gameNodeServer = loginService.getGameNodeServer(reconnectGameNodeId, reconnectGameServerId);
          IPAddress gameServerIp = gameNodeServer.getAddress4();
          InetAddress localAddress = InetAddress.getLocalHost();
          int reconnectGameServerPort = gameNodeServer.getPort();
          List<JoinableSeverData> gameNodeServers = loginService.getOnlineGameNodeServers(reconnectGameNodeId);
          session.send(new SendReconnectPacket(reconnectCharacterId, reconnectGameSessionKey, (byte) reconnectGameNodeId, reconnectGameNodeName, gameServerIp,
                  localAddress.getHostName(), reconnectGameServerPort, (short) 1, (short) 100, true, gameNodeServers));
      }
      else{
        session.send(new LoginResultPacket(state));
      }
    } catch (Exception e) {

    log.error("登录处理失败: username={}, useToken={}, manualLogin={}", username, useToken, manualLogin, e);

    session.attr(LoginProtocol.ATTRIBUTE_LOGIN_STATE).set(LoginState.UNAUTHED);
    session.attr(LoginProtocol.ATTRIBUTE_ACCOUNT_ID).set(null);
    session.send(new LoginResultPacket(LoginState.SYSTEM_ERROR));
    if (!Session.isRecoverableConnectionFailure(e)) {
      session.close();
    }
      }
    }
  }
