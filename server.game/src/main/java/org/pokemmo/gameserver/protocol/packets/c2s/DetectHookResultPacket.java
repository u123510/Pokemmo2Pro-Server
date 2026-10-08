package org.pokemmo.gameserver.protocol.packets.c2s;

import org.server.IncomingPacket;
import org.server.Session;
import org.server.bytes.ByteBufEx;

public class DetectHookResultPacket extends IncomingPacket {
    private long id;
    private int bufferLength;
    private short fragmentationLenght;
    private byte[] resultBuffer;
    @Override
    public void decode(ByteBufEx buffer) {
        id = buffer.readLongLE();
        bufferLength = buffer.readIntLE();
        fragmentationLenght = buffer.readShortLE();
        resultBuffer = buffer.readByteArray(fragmentationLenght);
    }

    @Override
    public void handle(Session session) throws Exception {

    }
}
