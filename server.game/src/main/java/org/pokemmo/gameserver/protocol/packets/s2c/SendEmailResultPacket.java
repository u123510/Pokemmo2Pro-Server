package org.pokemmo.gameserver.protocol.packets.s2c;

import lombok.RequiredArgsConstructor;
import org.server.OutgoingPacket;
import org.server.bytes.ByteBufEx;

/** S2C 0x96: completes the client's send-mail request. */
@RequiredArgsConstructor
public final class SendEmailResultPacket extends OutgoingPacket {
    public static final byte SUCCESS = 0;
    public static final byte REJECTED = 1;

    private final byte status;

    @Override
    public void encode(ByteBufEx buffer) {
        buffer.writeByte(status);
    }
}
