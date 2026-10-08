package org.pokemmo.gameserver.protocol.packets.s2c;

import org.server.OutgoingPacket;
import org.server.bytes.ByteBufEx;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class SendUpdateDexPacket extends OutgoingPacket {
    private final  byte catchLevel;
    private final short pokemonId;
    private final byte mask;
    @Override
    public void encode(ByteBufEx buffer) throws Exception {
        buffer.writeByte(catchLevel);
        buffer.writeShortLE(pokemonId);
        buffer.writeByte(mask);
    }
}
