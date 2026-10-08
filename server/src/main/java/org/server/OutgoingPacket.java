package org.server;

import org.server.bytes.ByteBufEx;

public abstract class OutgoingPacket extends Packet {
  public OutgoingPacket() {
    super();
  }

  @Override
  public void decode(ByteBufEx buffer) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void handle(Session session) throws Exception {
    throw new UnsupportedOperationException();
  }
}
