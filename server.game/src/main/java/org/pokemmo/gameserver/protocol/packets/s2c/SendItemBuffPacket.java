package org.pokemmo.gameserver.protocol.packets.s2c;

import org.server.OutgoingPacket;
import org.server.bytes.ByteBufEx;

public class SendItemBuffPacket extends OutgoingPacket {
    final short type = 0x3;
    final int time = 100;

    @Override
    public void encode(ByteBufEx buffer) throws Exception {
        buffer.writeShortLE(type);
        buffer.writeIntLE(time);
        buffer.writeUtf16LE("Niusz");
    }
}
