package org.pokemmo.gameserver.protocol.packets.s2c;

import org.server.OutgoingPacket;
import org.server.bytes.ByteBufEx;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class SendSetEntityIdleMovementPacket extends OutgoingPacket {
    private final long npcId;
    private final byte moveMentType;
    @Override
    public void encode(ByteBufEx buffer) throws Exception {
        buffer.writeLongLE(npcId);
        buffer.writeByte(moveMentType);
    }
}
