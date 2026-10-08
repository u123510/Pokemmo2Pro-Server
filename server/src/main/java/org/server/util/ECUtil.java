package org.server.util;

import lombok.extern.slf4j.Slf4j;

import javax.crypto.KeyAgreement;
import java.math.BigInteger;
import java.security.*;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.util.Arrays;

@Slf4j
public class ECUtil {
  private static final byte uncompressedPointIndicator = 0x04;
  private static final AlgorithmParameters algorithmSpec;
  private static final ECParameterSpec ecParameterSpec;
  private static final KeyPairGenerator keyPairGenerator;
  private static final KeyFactory keyFactory;
  static {
    try {
      algorithmSpec = AlgorithmParameters.getInstance("EC");
      algorithmSpec.init(new ECGenParameterSpec("secp256r1"));
      ecParameterSpec = algorithmSpec.getParameterSpec(ECParameterSpec.class);
      keyPairGenerator = KeyPairGenerator.getInstance("EC");
      keyPairGenerator.initialize(ecParameterSpec);
      keyFactory = KeyFactory.getInstance("EC");
    } catch (Exception e) {
      log.error("Failed to initialize ECParameterSpec", e);
      throw new RuntimeException(e);
    }
  }
  public static ECPublicKey PublicKeyFromUncompressedPoint(byte[] data) throws Exception {
    if (data[0] != uncompressedPointIndicator) {
      throw new IllegalArgumentException("Invalid uncompressed point indicator");
    }

    int keyLength = ((ecParameterSpec.getOrder().bitLength() + Byte.SIZE - 1) / Byte.SIZE);
    if (data.length != 1 + 2 * keyLength) {
      throw new IllegalArgumentException("Invalid data length");
    }

    int offset = 1;
    BigInteger x = new BigInteger(1, Arrays.copyOfRange(data, offset, offset + keyLength));
    offset += keyLength;
    BigInteger y = new BigInteger(1, Arrays.copyOfRange(data, offset, offset + keyLength));
    ECPoint point = new ECPoint(x, y);

    return (ECPublicKey) keyFactory.generatePublic(new ECPublicKeySpec(point, ecParameterSpec));
  }

  public static byte[] PublicKeyToUncompressedPoint(ECPublicKey key) {
    ECPoint point = key.getW();
    int keyLength = ((ecParameterSpec.getOrder().bitLength() + Byte.SIZE - 1) / Byte.SIZE);

    int offset = 0;
    byte[] data = new byte[1 + 2 * keyLength];
    data[offset++] = uncompressedPointIndicator;

    byte[] x = point.getAffineX().toByteArray();
    byte[] y = point.getAffineY().toByteArray();
    if (x.length <= keyLength) {
      System.arraycopy(x, 0, data, offset + keyLength - x.length, x.length);
    } else if (x.length == keyLength + 1 && x[0] == 0) {
      System.arraycopy(x, 1, data, offset, keyLength);
    } else {
      throw new IllegalStateException("x value is too large");
    }

    offset += keyLength;
    if (y.length <= keyLength) {
      System.arraycopy(y, 0, data, offset + keyLength - y.length, y.length);
    } else if (y.length == keyLength + 1 && y[0] == 0) {
      System.arraycopy(y, 1, data, offset, keyLength);
    } else {
      throw new IllegalStateException("y value is too large");
    }

    return data;
  }

  public static KeyPair keyPair() {
    return keyPairGenerator.generateKeyPair();
  }

  public static byte[] keyAgreement(PrivateKey privateKey, PublicKey publicKey) throws Exception {
    var keyAgreement = KeyAgreement.getInstance("ECDH");
    keyAgreement.init(privateKey);
    keyAgreement.doPhase(publicKey, true);
    return keyAgreement.generateSecret();
  }

  public static byte[] sign(byte[] data, PrivateKey privateKey) throws Exception {
    var signature = Signature.getInstance("SHA256withECDSA");
    signature.initSign(privateKey);
    signature.update(data);
    return signature.sign();
  }

  public static boolean verify(byte[] signature, byte[] data, PublicKey publicKey) throws Exception {
    var signatureVerification = Signature.getInstance("SHA256withECDSA");
    signatureVerification.initVerify(publicKey);
    signatureVerification.update(data);
    return signatureVerification.verify(signature);
  }
}
