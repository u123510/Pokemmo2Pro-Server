package org.pokemmo.gameserver.protocol.packets.s2c;

import org.server.OutgoingPacket;
import org.server.bytes.ByteBufEx;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class SendHasEventPacket extends OutgoingPacket {
    private final boolean hasEvent;
    @Override
    public void encode(ByteBufEx buffer) throws Exception {
        buffer.writeBoolean(hasEvent);
    }
}
