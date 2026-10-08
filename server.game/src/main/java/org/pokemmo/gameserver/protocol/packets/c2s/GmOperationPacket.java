package org.pokemmo.gameserver.protocol.packets.c2s;

import org.server.IncomingPacket;
import org.server.Session;
import org.server.bytes.ByteBufEx;

public class GmOperationPacket extends IncomingPacket {
    private byte gmOperationType;
    private long unkId;
    private String unkString;

    @Override
    public void decode(ByteBufEx buffer) {
        gmOperationType = buffer.readByte();
        if(gmOperationType == 1) {
            unkId = buffer.readLongLE();
            unkString = buffer.readUtf16();
        }
        else if(gmOperationType == 2) {
            unkId = buffer.readLongLE();
            unkString = buffer.readUtf16();
        }
    }

    @Override
    public void handle(Session session) throws Exception {

    }
}
