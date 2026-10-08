package org.pokemmo.gameserver.protocol.packets.c2s;

import org.server.IncomingPacket;
import org.server.Session;
import org.server.bytes.ByteBufEx;
import org.pokemmo.gameserver.protocol.GameProtocol;
import org.pokemmo.gameserver.protocol.packets.s2c.SendLongHeartBeatPacket;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LongtHeartbeatPacket extends IncomingPacket {
  private int vmFlags; // is 0 if no vm/hypervisor is detected
  private byte KeyPressTimeReapeatRatio;
  private byte zeroMsPressRatio;
  private byte stickButtonPressRatio;
  private byte averageKeyPressRangeRatio;
  private short lastFourMinutesKeyPressTimes;
  private long clientMillis;
  private long clientNanos;
  private byte[] mac;
  private byte unk3; // should always be 0

  @Override
  public void decode(ByteBufEx buffer) {
    vmFlags = buffer.readIntLE();
    KeyPressTimeReapeatRatio = buffer.readByte();
    zeroMsPressRatio = buffer.readByte();
    stickButtonPressRatio = buffer.readByte();
    averageKeyPressRangeRatio = buffer.readByte();
    lastFourMinutesKeyPressTimes = buffer.readShortLE();
    clientMillis = buffer.readLongLE();
    clientNanos = buffer.readLongLE();
    mac = buffer.readByteArray(6);
    unk3 = buffer.readByte();
  }

  @Override
  public void handle(Session session) throws Exception {
    log.trace("收到长心跳来自用户 {}. ", session.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get().getAccountData().getAccountId());
     session.send(
             new SendLongHeartBeatPacket()
     );

    // TODO: implement vm detection (low priority ig)
    // TODO: make sure the client is sending the HeartbeatPacket at a regular interval else kick
  }
}
