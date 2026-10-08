package org.pokemmo.gameserver.protocol.packets.s2c;

import lombok.RequiredArgsConstructor;
import org.server.OutgoingPacket;
import org.server.bytes.ByteBufEx;

@RequiredArgsConstructor
public class SendUpdatePlayerTransportationPacket extends OutgoingPacket {
    private final long playerId;
    private final byte transportation;
    @Override
    public void encode(ByteBufEx buffer) throws Exception {
        buffer.writeLongLE(playerId);
        buffer.writeByte(transportation);
    }
}
