package org.pokemmo.gameserver.protocol.packets.s2c;

import lombok.RequiredArgsConstructor;
import org.pokemmo.gameserver.game.container.PokemonContainerType;
import org.server.OutgoingPacket;
import org.server.bytes.ByteBufEx;

@RequiredArgsConstructor
public class SendRemovePokemonPacket extends OutgoingPacket {
    private final PokemonContainerType containerType;
    private final long pokemonObjectId;

    @Override
    public void encode(ByteBufEx buffer) {
        buffer.writeByte(containerType.getType());
        buffer.writeLongLE(pokemonObjectId);
    }
}
