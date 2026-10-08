package org.pokemmo.gameserver.protocol.packets.s2c;

import org.server.OutgoingPacket;
import org.server.bytes.ByteBufEx;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class SendPokemonDiedPacket extends OutgoingPacket {
    private final byte selectorData;
    private final boolean unuse;
    private final boolean isPokemonAllDied;

    @Override
    public void encode(ByteBufEx buffer) throws Exception {
        buffer.writeByte(selectorData);
        buffer.writeBoolean(unuse);
        buffer.writeBoolean(isPokemonAllDied);
    }
}
