package org.server.protocol.tls;

import org.server.util.ECUtil;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.security.*;
import java.security.spec.KeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

@Slf4j
public class RootKeyLoader {
  private final KeyFactory keyFactory;
  @Getter
  private final KeyPair keyPair;

  public RootKeyLoader(File publicKeyFile, File privateKeyFile) {
    log.info("Initializing CA service");

    try {
      keyFactory = KeyFactory.getInstance("EC");
    } catch (NoSuchAlgorithmException e) {
      log.error("Failed to initialize KeyFactory", e);
      throw new RuntimeException(e);
    }

    if (!publicKeyFile.exists() || !privateKeyFile.exists()) {
      log.info("Generating new key pair...");
      deleteFiles(publicKeyFile, privateKeyFile);
      createDirectories(publicKeyFile, privateKeyFile);

      KeyPair keyPair = ECUtil.keyPair();
      try {
        saveKey(keyPair.getPublic(), publicKeyFile);
        saveKey(keyPair.getPrivate(), privateKeyFile);
      } catch (Exception e) {
        log.error("Failed to save key pair", e);
        throw new RuntimeException(e);
      }
    }

    log.info("Loading key pair...");
    PublicKey publicKey;
    PrivateKey privateKey;
    try {
      publicKey = (PublicKey) loadKey(publicKeyFile, false);
      privateKey = (PrivateKey) loadKey(privateKeyFile, true);
    } catch (Exception e) {
      log.error("Failed to load key pair", e);
      throw new RuntimeException(e);
    }

    keyPair = new KeyPair(publicKey, privateKey);
    log.info("CA service initialized");
  }

  private void createDirectories(File... files) {
    for (File file : files) {
      File parent = file.getParentFile();
      if (!parent.exists() && !parent.mkdirs()) {
        throw new RuntimeException("Failed to create parent directory for key file");
      }
    }
  }

  private void deleteFiles(File... files) {
    for (File file : files) {
      if (file.exists() && !file.delete()) {
        throw new RuntimeException("Failed to delete existing key file");
      }
    }
  }

  private void saveKey(Key key, File file) throws Exception {
    FileOutputStream fos = new FileOutputStream(file);
    fos.write(key.getEncoded());
    fos.close();
  }

  private Key loadKey(File file, boolean isPrivate) throws Exception {
    byte[] data = Files.readAllBytes(file.toPath());
    KeySpec keySpec;
    if (isPrivate) {
      keySpec = new PKCS8EncodedKeySpec(data);
    } else {
      keySpec = new X509EncodedKeySpec(data);
    }
    return isPrivate ? keyFactory.generatePrivate(keySpec) : keyFactory.generatePublic(keySpec);
  }
}
