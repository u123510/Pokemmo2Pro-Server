package org.pokemmo.gameserver.protocol.packets.s2c;

import org.server.OutgoingPacket;
import org.server.bytes.ByteBufEx;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class SendPlayMusicPacket extends OutgoingPacket {
    private final byte musicRegionIndexId;
    private final short musicIndexId;
    private final boolean isStopCurrent;
    @Override
    public void encode(ByteBufEx buffer) throws Exception {
        buffer.writeByte(musicRegionIndexId);
        buffer.writeShortLE(musicIndexId);
        buffer.writeBoolean(isStopCurrent);//unuse
    }
}
