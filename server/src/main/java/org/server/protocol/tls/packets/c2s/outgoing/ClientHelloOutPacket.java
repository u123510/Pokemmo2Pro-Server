package org.server.protocol.tls.packets.c2s.outgoing;

import org.server.OutgoingPacket;
import org.server.bytes.ByteBufEx;

import java.util.Random;

public class ClientHelloOutPacket extends OutgoingPacket {
  private static final Random random = new Random();
  private static final long key1 = 3214621489648854472L;
  private static final long key2 = -4214651440992349575L;

  @Override
  public void encode(ByteBufEx buffer) throws Exception {
    long randomValue = random.nextLong();
    long timestamp = System.currentTimeMillis();
    buffer.writeLongLE(randomValue ^ key1);
    buffer.writeLongLE(timestamp ^ key2 ^ randomValue);
  }
}
