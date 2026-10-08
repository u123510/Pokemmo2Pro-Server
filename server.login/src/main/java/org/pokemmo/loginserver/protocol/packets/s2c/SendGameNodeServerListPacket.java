package org.pokemmo.loginserver.protocol.packets.s2c;

import com.github.maltalex.ineter.base.IPAddress;
import com.github.maltalex.ineter.base.IPv4Address;
import org.server.OutgoingPacket;
import org.server.bytes.ByteBufEx;
import org.pokemmo.loginserver.LoginState;
import lombok.RequiredArgsConstructor;
import org.server.node.JoinableSeverData;


import java.util.List;
@RequiredArgsConstructor
//实际在线的游戏服务器，让客户端选择与那个服务器连接
public class SendGameNodeServerListPacket extends OutgoingPacket {
  private final LoginState loginState;
  private final byte gameServerId;
  private final int accountId;
  private final byte[] gameServerSessionKey;
  private final IPAddress localAddress; // 127.0.0.1
  private final String localHostname; // localhost
  private final int port; // server port not node port (7777)
  private final List<JoinableSeverData> nodeCanJoinServers;

  public SendGameNodeServerListPacket(LoginState loginState) {
    if (loginState == LoginState.AUTHED) {
      throw new IllegalArgumentException("Use the other constructor for AUTHED state");
    }
    this.loginState = loginState;
    this.gameServerId = 0;
    this.accountId = 0;
    this.gameServerSessionKey = new byte[0];
    this.localAddress = IPv4Address.MIN_ADDR;
    this.localHostname = "";
    this.port = 0;
    this.nodeCanJoinServers = List.of();
  }

  @Override
  public void encode(ByteBufEx buffer){
    buffer.writeByte(loginState.getId());
    if (loginState != LoginState.AUTHED) {
      return;
    }
    buffer.writeIntLE(accountId);
    buffer.writeByte(gameServerSessionKey.length);
    buffer.writeBytes(gameServerSessionKey);
    buffer.writeByte(gameServerId);
    byte[] localAddressBytes = localAddress.toLittleEndianArray();
    buffer.writeByte(localAddressBytes.length);
    buffer.writeBytes(localAddressBytes);
    buffer.writeUtf16LE(localHostname);
    buffer.writeIntLE(port);
    assert nodeCanJoinServers.size() <= Byte.MAX_VALUE;
    buffer.writeByte(nodeCanJoinServers.size());
    for (JoinableSeverData serverData : nodeCanJoinServers) {
      buffer.writeByte(0); // unused
      buffer.writeIpLE(serverData.getAddress4());
      buffer.writeIpLE(serverData.getAddress6());
      buffer.writeShortLE(serverData.getPort());
      buffer.writeByte(serverData.getServerWeight());
    }
  }
}
