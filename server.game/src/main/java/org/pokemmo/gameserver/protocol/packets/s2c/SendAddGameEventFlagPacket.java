package org.pokemmo.gameserver.protocol.packets.s2c;
import lombok.RequiredArgsConstructor;
import org.server.OutgoingPacket;
import org.server.bytes.ByteBufEx;
@RequiredArgsConstructor
public class SendAddGameEventFlagPacket extends OutgoingPacket{
    private final short[] addGameEventFlags;
    @Override
    public void encode(ByteBufEx buffer) throws Exception {
        buffer.writeShortLE(addGameEventFlags.length);
        for (short addGameEventFlag : addGameEventFlags) {
            buffer.writeShortLE(addGameEventFlag);
        }
    }
}