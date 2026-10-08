package org.server.protocol.tls.packets.c2s.outgoing;

import org.server.OutgoingPacket;
import org.server.bytes.ByteBufEx;

public class SendTestConnectPacket  extends OutgoingPacket {
    @Override
    public void encode(ByteBufEx buffer) throws Exception {
        buffer.writeLongLE(0);
        buffer.writeLongLE(0);
    }
}
