package org.patcher;

import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.Instrumentation;
import java.util.Base64;

public class PatcherAgent {
  public static void premain(String arg, Instrumentation inst) throws IOException {
    // public key for gameserver and loginserver are the same
    createTransformer(
        "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEtqx2myJz3ftlYWgd7cbNqf2t208itQMY7ouPNBDpQetbi7eXbEDxDDZy4Q9fMnI6mF5/D0qMdRd40SRXf0OS7Q==",
        "/game.public",
        inst
    );
    createTransformer(
        "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEh4Vqgnd+8Fqebu0H40v+FgwhE6RwgAYxJMihb8mJmcHDy8r/rPz3kLHH1oabyKIRUa5Y2cK0TsxZky+mp7DKWA==",
        "/chat.public",
        inst
    );
  }

  private static void createTransformer(String oldCert, String newCertPath,
      Instrumentation inst)
      throws IOException {
    try (InputStream in = PatcherAgent.class.getResourceAsStream(newCertPath)) {
      if (in == null) {
        throw new IOException("Could not find patcher certificate resource " + newCertPath);
      }
      byte[] newCert = in.readAllBytes();
      String base64Cert = Base64.getEncoder().encodeToString(newCert);
      inst.addTransformer(new StringTransformer(oldCert, base64Cert));
    }
  }
}
