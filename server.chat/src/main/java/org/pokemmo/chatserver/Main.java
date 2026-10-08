package org.pokemmo.chatserver;

import org.pokemmo.chatserver.chat.ChatBroadcastListener;
import org.pokemmo.chatserver.protocol.ChatProtocol;
import org.pokemmo.chatserver.services.ChatServerService;
import org.pokemmo.db.Database;
import org.server.Server;
import org.server.ServerType;
import org.server.Session;
import org.server.protocol.tls.RootKeyLoader;
import org.server.protocol.tls.TlsProtocol;
import org.server.services.ServerService;

import java.io.File;

public class Main {
    public static void main(String[] args) {
        TlsProtocol tlsProtocol = new TlsProtocol(
                new RootKeyLoader(new File("./chat.public"), new File("./chat.private"))
        );
        Database database = new Database(
                "jdbc:postgresql://45.125.45.164:35432/postgres",
                "postgres",
                "Lm040810."
        );
        ChatProtocol chatProtocol = new ChatProtocol(
                new ChatServerService(database),
                new ServerService(database),
                new ChatBroadcastListener()
        );
        Server server = new Server(7778,side -> new Session(tlsProtocol, chatProtocol, side, ServerType.CHAT));
        try {
            server.run();
        } finally {
            database.close();
        }
    }
}
