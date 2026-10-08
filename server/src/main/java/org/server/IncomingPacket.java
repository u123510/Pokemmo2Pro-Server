package org.server;

import org.server.bytes.ByteBufEx;

public abstract class IncomingPacket extends Packet {
  public IncomingPacket() {
    super();
  }

  @Override
  public void encode(ByteBufEx buffer) throws Exception {
    throw new UnsupportedOperationException();
  }

    public abstract void decode(ByteBufEx buffer);
}
