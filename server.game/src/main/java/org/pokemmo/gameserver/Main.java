package org.pokemmo.gameserver;

import org.pokemmo.gameserver.game.pool.GameSessionPool;
import org.pokemmo.gameserver.script.ScriptManager;
import org.pokemmo.gameserver.util.SnowflakeIdGenerator;
import org.server.Server;
import org.server.ServerType;
import org.server.Session;
import org.pokemmo.db.Database;
import org.pokemmo.gameserver.protocol.GameProtocol;
import org.pokemmo.gameserver.services.GameServerService;
import org.server.protocol.tls.RootKeyLoader;
import org.server.protocol.tls.TlsProtocol;
import org.server.redis.RedisUtil;
import org.server.services.ServerService;
import lombok.extern.slf4j.Slf4j;

import java.io.File;

@Slf4j
public class Main {
  public static void main(String[] args) {
    log.info("游戏服务器启动...");
    TlsProtocol tlsProtocol = new TlsProtocol(
        new RootKeyLoader(new File("./game.public"), new File("./game.private"))
    );
    Database database = new Database(
            "jdbc:postgresql://45.125.45.164:35432/postgres",
            "postgres",
            "Lm040810."
    );
    //雪花算法id生成器
    GameProtocol gameProtocol = new GameProtocol(
        new ServerService(database),
        new GameServerService(database),
        database,
        new SnowflakeIdGenerator(1, 1),
        new ScriptManager(new String[]{"resource\\map",
                "resource\\pokemon","resource\\item","resource\\gift","resource\\trainer","resource\\move",
                "resource\\encounter"}
        ),
        new RedisUtil()
    );
    Server server = new Server(7777, side -> new Session(tlsProtocol, gameProtocol,side, ServerType.GAME));
    Thread shutdownHook = new Thread(() -> {
      log.info("检测到服务器正在关闭，向所有在线客户端发送停服通知...");
      try {
        GameSessionPool.broadcastShutdown();
        Thread.sleep(600);
      } catch (Exception e) {
        log.error("广播停服通知时发生异常", e);
      }
      server.shutdown();
    }, "GameServer-ShutdownHook");
    Runtime.getRuntime().addShutdownHook(shutdownHook);
    try {
      server.run();
    } finally {
      try {
        Runtime.getRuntime().removeShutdownHook(shutdownHook);
      } catch (IllegalStateException ignored) {
        // JVM is shutting down
      }
      database.close();
    }
  }
}
