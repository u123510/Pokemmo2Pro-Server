package org.pokemmo.gameserver.protocol.packets.c2s;

import lombok.extern.slf4j.Slf4j;
import org.server.IncomingPacket;
import org.server.Session;
import org.server.bytes.ByteBufEx;
@Slf4j
public final class EnvironmentInfoExceptWhiteListPacket extends IncomingPacket {

    private byte[] payload = new byte[0];

    @Override
    public void decode(ByteBufEx buffer) {
        payload = buffer.readByteArray(buffer.readableBytes());
    }

    @Override
    public void handle(Session session) {
    }
}
