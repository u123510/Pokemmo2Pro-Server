package org.server.handlers;

import org.server.DataFlow;
import org.server.Session;
import org.server.protocol.tls.hash.Hash;
import org.server.protocol.tls.TlsInfo;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageCodec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.Cipher;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class EncryptionHandler extends MessageToMessageCodec<ByteBuf, ByteBuf> {
  private final Cipher encryptCipher;
  private final Cipher decryptCipher;
  private final Hash encryptHash;
  private final Hash decryptHash;

  public EncryptionHandler(TlsInfo tlsInfo, Session.Side side) {
    DataFlow outgoing = side == Session.Side.CLIENT ? DataFlow.CLIENT_TO_SERVER : DataFlow.SERVER_TO_CLIENT;
    DataFlow incoming = side == Session.Side.CLIENT ? DataFlow.SERVER_TO_CLIENT : DataFlow.CLIENT_TO_SERVER;
    this.encryptCipher = tlsInfo.getEncryptionCipher(outgoing);
    this.decryptCipher = tlsInfo.getDecryptionCipher(incoming);
    this.encryptHash = tlsInfo.getHash(outgoing);
    this.decryptHash = tlsInfo.getHash(incoming);
    assert this.encryptHash.getSize() == this.decryptHash.getSize();
  }

  @Override
  protected void encode(ChannelHandlerContext ctx, ByteBuf msg, List<Object> out) {
    byte[] data = new byte[msg.readableBytes()];
    msg.readBytes(data);

    byte[] encryptedData = this.encryptCipher.update(data);
    byte[] hash = this.encryptHash.hash(encryptedData);

    out.add(Unpooled.wrappedBuffer(encryptedData, hash));
  }

  @Override
  protected void decode(ChannelHandlerContext ctx, ByteBuf msg, List<Object> out) {
    int dataLength = msg.readableBytes() - this.decryptHash.getSize();
    byte[] data = new byte[dataLength];
    byte[] hash = new byte[this.decryptHash.getSize()];
    msg.readBytes(data);
    msg.readBytes(hash);

    if (!this.decryptHash.verify(data, hash)) {
      throw new IllegalStateException("Could not verify packet integrity");
    }

    out.add(Unpooled.wrappedBuffer(this.decryptCipher.update(data)));
  }
}
