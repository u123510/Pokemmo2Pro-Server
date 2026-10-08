package org.pokemmo.gameserver.protocol.packets.s2c;

import org.server.OutgoingPacket;
import org.server.bytes.ByteBufEx;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class SendLoadSeasonPacket extends OutgoingPacket {
    private final byte season = (byte) (new java.util.GregorianCalendar(java.util.TimeZone.getTimeZone("UTC")).get(2) % 4);

    @Override
    public void encode(ByteBufEx buffer) throws Exception {
        buffer.writeByte(season);
    }
}
