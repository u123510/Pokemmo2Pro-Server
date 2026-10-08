package org.server.protocol.tls.packets.s2c.incoming;

import org.server.IncomingPacket;
import org.server.Session;
import org.server.bytes.ByteBufEx;
import org.server.protocol.tls.TlsInfo;
import org.server.protocol.tls.TlsProtocol;
import org.server.protocol.tls.packets.c2s.outgoing.ClientReadyOutPacket;
import org.server.util.ECUtil;

import java.security.KeyPair;
import java.security.PublicKey;

public class ServerHelloInPacket extends IncomingPacket {
  private byte hashSize;
  private byte[] uncompressedPoint;
  private byte[] signature;

  @Override
  public void decode(ByteBufEx buffer) {
    uncompressedPoint = new byte[buffer.readShortLE()];
    buffer.readBytes(uncompressedPoint);
    signature = new byte[buffer.readShortLE()];
    buffer.readBytes(signature);
    hashSize = buffer.readByte();
  }

  @Override
  public void handle(Session session) throws Exception {
    PublicKey publicKey = ECUtil.PublicKeyFromUncompressedPoint(uncompressedPoint);
    if (!ECUtil.verify(uncompressedPoint, signature, publicKey)) {
      throw new IllegalStateException("Handshake failed due to invalid signature");
    }

    KeyPair keyPair = session.getChannel().attr(TlsProtocol.ATTRIBUTE_KEY_PAIR).get();
    session.send(new ClientReadyOutPacket(keyPair.getPublic()));
    session.enableEncryption(new TlsInfo(keyPair.getPrivate(), publicKey, hashSize));
  }
}
