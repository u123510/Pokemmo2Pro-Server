package org.server.protocol.tls.packets.c2s.incoming;

import org.server.IncomingPacket;
import org.server.Session;
import org.server.bytes.ByteBufEx;
import org.server.protocol.tls.TlsInfo;
import org.server.protocol.tls.TlsProtocol;
import org.server.util.ECUtil;

import java.security.KeyPair;
import java.security.PublicKey;

public class ClientReadyPacket extends IncomingPacket {
  private byte[] uncompressedPoint;

  @Override
  public void decode(ByteBufEx buffer) {
    uncompressedPoint = new byte[buffer.readShortLE()];
    buffer.readBytes(uncompressedPoint);
  }

  @Override
  public void handle(Session session) throws Exception {
    PublicKey publicKey = ECUtil.PublicKeyFromUncompressedPoint(uncompressedPoint);
    KeyPair keyPair = session.getChannel().attr(TlsProtocol.ATTRIBUTE_KEY_PAIR).get();
    session.enableEncryption(new TlsInfo(keyPair.getPrivate(), publicKey, session.getEncryptedProtocol().getHashSize()));

  }
}
