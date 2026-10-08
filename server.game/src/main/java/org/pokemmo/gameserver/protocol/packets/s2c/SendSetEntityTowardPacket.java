package org.pokemmo.gameserver.protocol.packets.s2c;

import org.server.OutgoingPacket;
import org.server.bytes.ByteBufEx;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class SendSetEntityTowardPacket extends OutgoingPacket {
    private final long npcId;
    private final byte toward;

    public SendSetEntityTowardPacket(long npcId, int toward) {
        this.npcId = npcId;
        this.toward = (byte) (toward & 0x03);
    }

    @Override
    public void encode(ByteBufEx buffer) throws Exception {
        buffer.writeLongLE(npcId);
        buffer.writeByte(toward);
    }
}
