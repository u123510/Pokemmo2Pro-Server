package org.pokemmo.gameserver.protocol.packets.s2c;

import lombok.RequiredArgsConstructor;
import org.server.OutgoingPacket;
import org.server.bytes.ByteBufEx;

@RequiredArgsConstructor
public class SendResetGameRenderPacket extends OutgoingPacket {
    private final boolean render;

    @Override
    public void encode(ByteBufEx buffer) throws Exception {
        buffer.writeBoolean(render);
    }
}
