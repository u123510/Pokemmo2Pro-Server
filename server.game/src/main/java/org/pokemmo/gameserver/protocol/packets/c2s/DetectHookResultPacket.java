package org.pokemmo.gameserver.protocol.packets.c2s;

import lombok.extern.slf4j.Slf4j;
import org.server.IncomingPacket;
import org.server.Session;
import org.server.bytes.ByteBufEx;
@Slf4j
public final class DetectHookResultPacket extends IncomingPacket {
    private static final int HEADER_SIZE = Long.BYTES + Integer.BYTES + Short.BYTES;

    private long id;
    private int bufferRemain;
    private short fragmentLength;
    private byte[] remainBuffer = new byte[0];

    @Override
    public void decode(ByteBufEx buffer) {
        long payloadSize = buffer.readLongLE();
        id = buffer.readLongLE();
        bufferRemain= buffer.readIntLE();
        fragmentLength = buffer.readShortLE();
        remainBuffer = buffer.readByteArray(fragmentLength);
    }

    @Override
    public void handle(Session session) {
    }
}
