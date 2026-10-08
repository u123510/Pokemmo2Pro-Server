package org.pokemmo.gameserver.protocol.packets.c2s;

import org.server.IncomingPacket;
import org.server.Session;
import org.server.bytes.ByteBufEx;

public class FlyPacket extends IncomingPacket {
    private byte cityIndex;
    @Override
    public void decode(ByteBufEx buffer) {
        cityIndex = buffer.readByte();
    }

    @Override
    public void handle(Session session) throws Exception {

    }
}
