package org.pokemmo.gameserver.protocol.packets.s2c;

import lombok.RequiredArgsConstructor;
import org.server.OutgoingPacket;
import org.server.bytes.ByteBufEx;

/**
 * Controls the client PC window. The client packet handler for opcode 0x27
 * reads a single boolean byte and opens or closes the PC UI accordingly.
 */
@RequiredArgsConstructor
public class SendPcStatePacket extends OutgoingPacket {
    private final boolean open;

    @Override
    public void encode(ByteBufEx buffer) {
        buffer.writeBoolean(open);
    }
}
