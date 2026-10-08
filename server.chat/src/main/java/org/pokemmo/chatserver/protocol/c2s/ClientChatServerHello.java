package org.pokemmo.chatserver.protocol.c2s;

import org.pokemmo.chatserver.protocol.s2c.SendServerChatServerHello;
import org.server.IncomingPacket;
import org.server.Session;
import org.server.bytes.ByteBufEx;

public class ClientChatServerHello extends IncomingPacket {
    @Override
    public void decode(ByteBufEx buffer) {

    }

    @Override
    public void handle(Session session) throws Exception {
        session.send(new SendServerChatServerHello());
    }
}
