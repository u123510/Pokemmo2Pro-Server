package org.pokemmo.loginserver.protocol.packets.s2c;

import org.server.OutgoingPacket;
import org.server.bytes.ByteBufEx;
import org.pokemmo.loginserver.LoginState;

public class LoginResultPacket extends OutgoingPacket {
  private final LoginState result;
  private final int rateLimitEnd;

  public LoginResultPacket(LoginState result, int rateLimitEnd) {
    this.result = result;
    this.rateLimitEnd = rateLimitEnd;
  }
  public LoginResultPacket(LoginState result) {
    this(result, 0);
  }
  @Override
  public void encode(ByteBufEx buffer){
    buffer.writeByte(result.getId());
    if (result == LoginState.RATE_LIMITED || result == LoginState.RATE_LIMITED_2FA) {
      buffer.writeIntLE(rateLimitEnd);
    }
    if (result == LoginState.AUTHED) {
      buffer.writeUtf16LE("");
    }
  }
}
