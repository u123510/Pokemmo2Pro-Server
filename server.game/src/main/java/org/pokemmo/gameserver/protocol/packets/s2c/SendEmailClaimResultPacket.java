package org.pokemmo.gameserver.protocol.packets.s2c;

import lombok.RequiredArgsConstructor;
import org.server.OutgoingPacket;
import org.server.bytes.ByteBufEx;

/** S2C 0x9A: confirms the attachment slot claimed from the open mail. */
@RequiredArgsConstructor
public final class SendEmailClaimResultPacket extends OutgoingPacket {
    private final long mailId;
    private final int slot;

    @Override
    public void encode(ByteBufEx buffer) {
        buffer.writeLongLE(mailId);
        buffer.writeByte(slot);
    }
}
