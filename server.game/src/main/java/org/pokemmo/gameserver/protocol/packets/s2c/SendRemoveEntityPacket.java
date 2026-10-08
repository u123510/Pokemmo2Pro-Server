package org.pokemmo.gameserver.protocol.packets.s2c;

import org.server.OutgoingPacket;
import org.server.bytes.ByteBufEx;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class SendRemoveEntityPacket extends OutgoingPacket {
    private final long characterId;
    @Override
    public void encode(ByteBufEx buffer) throws Exception {
        buffer.writeLongLE(characterId);
    }
}
