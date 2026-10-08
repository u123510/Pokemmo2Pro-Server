package org.server.protocol.tls.hash;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

public class HmacSha256 implements Hash {
  private final byte size;
  private final Mac mac;
  private int hashRound = 0;
  private int verifyRound = 0;

  public HmacSha256(byte size, byte[] key) {
    assert size >= 4 && size <= 32;
    this.size = size;

    try {
      this.mac = Mac.getInstance("HmacSHA256");
      this.mac.init(new SecretKeySpec(key, "HmacSHA256"));
    } catch (NoSuchAlgorithmException | InvalidKeyException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public byte getSize() {
    return size;
  }

  /**
   * Convert an integer to a big endian byte array.
   * @param value The integer to convert.
   * @return The big endian byte array.
   */
  private byte[] getBytes(int value) {
    return new byte[] {
      (byte) ((value >> 24) & 0xFF),
      (byte) ((value >> 16) & 0xFF),
      (byte) ((value >> 8) & 0xFF),
      (byte) (value & 0xFF)
    };
  }

  @Override
  public byte[] hash(byte[] data) {
    mac.update(data);
    mac.update(getBytes(hashRound++));
    return Arrays.copyOfRange(mac.doFinal(), 0, size);
  }

  @Override
  public boolean verify(byte[] data, byte[] hash) {
    mac.update(data);
    mac.update(getBytes(verifyRound++));
    byte[] result = Arrays.copyOfRange(mac.doFinal(), 0, size);
    return MessageDigest.isEqual(result, hash);
  }
}
