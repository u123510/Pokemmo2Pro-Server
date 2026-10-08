package org.server.protocol.tls.hash;

public class NoHash implements Hash {
  @Override
  public byte getSize() {
    return 0;
  }

  @Override
  public byte[] hash(byte[] data) {
    return new byte[0];
  }

  @Override
  public boolean verify(byte[] data, byte[] hash) {
    return hash.length == 0;
  }
}
