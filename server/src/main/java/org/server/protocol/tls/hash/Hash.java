package org.server.protocol.tls.hash;

public interface Hash {
  byte getSize();
  byte[] hash(byte[] data);
  boolean verify(byte[] data, byte[] hash);
}
