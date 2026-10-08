package org.pokemmo.gameserver.protocol.packets.s2c;

import org.server.OutgoingPacket;
import org.server.bytes.ByteBufEx;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class SendShowPokemonNameplateWidgetPacket extends OutgoingPacket {
    private final byte pokemonFormType;
    private final short pokemonIndexId;
    private final boolean isShiny;
    @Override
    public void encode(ByteBufEx buffer) throws Exception {
        buffer.writeShortLE(pokemonIndexId);
        if(pokemonIndexId>0){
            buffer.writeByte(pokemonFormType);
            buffer.writeBoolean(isShiny);
        }
    }
}
