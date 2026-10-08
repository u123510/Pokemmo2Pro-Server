package org.pokemmo.gameserver.protocol.packets.s2c;

import org.server.OutgoingPacket;
import org.server.bytes.ByteBufEx;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class SendPlaySoundPacket extends OutgoingPacket {
    private final byte soundRegionIndexId;
    private final short soundIndexId;
    private final byte soundType;
    @Override
    public void encode(ByteBufEx buffer) throws Exception {
        buffer.writeByte(soundRegionIndexId);
        buffer.writeShortLE(soundIndexId);
        buffer.writeByte(soundType);
        if(soundType == 2) {
            buffer.writeByte(0);
        }
    }
}
