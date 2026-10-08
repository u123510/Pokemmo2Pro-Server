package org.pokemmo.gameserver.protocol.packets.c2s;

import org.server.IncomingPacket;
import org.server.Session;
import org.server.bytes.ByteBufEx;

public class PokemonLockStatusPacket  extends IncomingPacket {
    private long id;
    private byte lockStatus;
    @Override
    public void decode(ByteBufEx buffer) {
        id = buffer.readLongLE();
        lockStatus = buffer.readByte();
    }
    @Override
    public void handle(Session session) throws Exception {

    }
}
