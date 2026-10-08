package org.server.protocol.tls;

import org.server.DataFlow;
import org.server.protocol.tls.hash.Crc16;
import org.server.protocol.tls.hash.Hash;
import org.server.protocol.tls.hash.HmacSha256;
import org.server.protocol.tls.hash.NoHash;
import org.server.util.ECUtil;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Arrays;

@Slf4j
public class TlsInfo {
  private static final byte[] COMMON_IV = new byte[] { 'I', 'V', 'D', 'E', 'R', 'I', 'V' };
  private static final byte[] CLIENT_KEY_SALT = new byte[] { 'K', 'e', 'y', 'S', 'a', 'l', 't', 1 };
  private static final byte[] SERVER_KEY_SALT = new byte[] { 'K', 'e', 'y', 'S', 'a', 'l', 't', 2 };
  private byte[] clientSeed = new byte[] { 63, 24, -15, 98, 114, 7, 68, 24, -12, 109, -111, -105, 66, -96, -2, -55 };
  private byte[] serverSeed = new byte[] { 31, -102, -128, 60, -103, 38, 10, -117, -105, -50, 2, 116, -83, 57, 39, -76 };
  private final byte hashSize;
  private Cipher clientCipher;
  private Cipher serverCipher;

  public TlsInfo(PrivateKey privateKey, PublicKey publicKey, byte hashSize)
      throws Exception {
    this.hashSize = hashSize;
    byte[] sharedSecret = ECUtil.keyAgreement(privateKey, publicKey);

    if (sharedSecret.length * 8 >= 128) {
      clientSeed = tripleHash(sharedSecret, CLIENT_KEY_SALT);
      serverSeed = tripleHash(sharedSecret, SERVER_KEY_SALT);
    }

    // log.info(toHexString(clientSeed));
    // log.info(toHexString(serverSeed));
  }

  public Cipher getEncryptionCipher(DataFlow side) {
    if (side == DataFlow.CLIENT_TO_SERVER) {
      if (clientCipher == null) {
        clientCipher = createCipher(Cipher.ENCRYPT_MODE, clientSeed);
      }
      return clientCipher;
    } else {
      if (serverCipher == null) {
        serverCipher = createCipher(Cipher.ENCRYPT_MODE, serverSeed);
      }
      return serverCipher;
    }
  }

  public Cipher getDecryptionCipher(DataFlow side) {
    if (side == DataFlow.CLIENT_TO_SERVER) {
      if (clientCipher == null) {
        clientCipher = createCipher(Cipher.DECRYPT_MODE, clientSeed);
      }
      return clientCipher;
    } else {
      if (serverCipher == null) {
        serverCipher = createCipher(Cipher.DECRYPT_MODE, serverSeed);
      }
      return serverCipher;
    }
  }

  public Hash getHash(DataFlow side) {
    if (side == DataFlow.CLIENT_TO_SERVER) {
      return create(hashSize, clientSeed);
    } else {
      return create(hashSize, serverSeed);
    }
  }

  public static String toHexString(byte[] bytes) {
    StringBuilder sb = new StringBuilder();
    for (byte b : bytes) {
      sb.append(String.format("%02X", b));
    }
    return sb.toString();
  }

  private static byte[] tripleHash(byte[] data1, byte[] data2) throws Exception {
    MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
    sha256.update(data2);
    sha256.update(data1);
    sha256.update(data2);
    return Arrays.copyOfRange(sha256.digest(), 0, 16);
  }

  private static Cipher createCipher(int mode, byte[] seed) {
    try {
      Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
      cipher.init(
          mode,
          new SecretKeySpec(seed, "AES"),
          new IvParameterSpec(tripleHash(seed, COMMON_IV))
      );
      return cipher;
    } catch (Exception e) {
      log.error("Failed to create cipher", e);
      throw new RuntimeException(e);
    }
  }

  private Hash create(byte size, byte[] seed) {
    if (size == 0) {
      return new NoHash();
    } else if (size == 2) {
      return new Crc16();
    } else if (4 <= size && size <= 32) {
      return new HmacSha256(size, seed);
    } else {
      throw new IllegalArgumentException("Unsupported hash size: " + size);
    }
  }
}
