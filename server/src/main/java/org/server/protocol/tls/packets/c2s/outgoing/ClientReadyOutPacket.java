package org.server.protocol.tls.packets.c2s.outgoing;

import org.server.OutgoingPacket;
import org.server.bytes.ByteBufEx;
import org.server.util.ECUtil;

import java.security.PublicKey;
import java.security.interfaces.ECPublicKey;

public class ClientReadyOutPacket extends OutgoingPacket {
  private final PublicKey connectionKeyPublic;

  public ClientReadyOutPacket(PublicKey connectionKeyPublic) {
    this.connectionKeyPublic = connectionKeyPublic;
  }

  @Override
  public void encode(ByteBufEx buffer) throws Exception {
    assert connectionKeyPublic != null;
    byte[] uncompressedPoint = ECUtil.PublicKeyToUncompressedPoint((ECPublicKey) connectionKeyPublic);
    buffer.writeShortLE(uncompressedPoint.length);
    buffer.writeBytes(uncompressedPoint);
  }
}
