package org.pokemmo.gameserver.protocol.packets.c2s;

import org.server.IncomingPacket;
import org.server.Session;
import org.server.bytes.ByteBufEx;

public class EnvironmentInfoExceptWhiteListPacket extends IncomingPacket {

    private  byte hashSize;
    private byte key;
    private byte[] value;
    private byte listSize;
    private byte[] listValue;

    @Override
    public void decode(ByteBufEx buffer) {
        while(buffer.isReadable())
        {
            key = buffer.readByte();
        }
    }

    @Override
    public void handle(Session session) throws Exception {

    }
}
