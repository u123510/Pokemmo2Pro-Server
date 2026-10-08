package org.pokemmo.gameserver.protocol.packets.s2c;

import org.server.OutgoingPacket;
import org.server.bytes.ByteBufEx;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class SendRenderScreenPacket extends OutgoingPacket {
    private final boolean render;

    @Override
    public void encode(ByteBufEx buffer) throws Exception {
        buffer.writeBoolean(render);
    }
}
