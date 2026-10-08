package org.pokemmo.loginserver;

import org.pokemmo.db.Database;
import org.pokemmo.loginserver.protocol.LoginProtocol;
import org.pokemmo.loginserver.service.LoginService;
import lombok.extern.slf4j.Slf4j;
import org.server.Server;
import org.server.ServerType;
import org.server.Session;
import org.server.protocol.tls.RootKeyLoader;
import org.server.protocol.tls.TlsProtocol;
import org.server.redis.RedisUtil;
import org.server.services.ServerService;

import java.io.File;

@Slf4j
public class Main {
  public static void main(String[] args) {

    TlsProtocol tlsProtocol = new TlsProtocol(
        new RootKeyLoader(new File("./game.public"), new File("./game.private"))
    );
    Database database = new Database(
            "jdbc:postgresql://45.125.45.164:35432/postgres",
            "postgres",
            "Lm040810."
    );
    LoginProtocol loginProtocol = new LoginProtocol(
        new LoginService(database),
        new ServerService(database),
            new RedisUtil()
    );
    Server server = new Server(2106, side -> new Session(tlsProtocol, loginProtocol, side, ServerType.LOGIN));
    try {
      server.run();
    } finally {
      database.close();
    }
  }
}
