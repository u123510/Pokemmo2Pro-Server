package org.pokemmo.gameserver.protocol.packets.s2c;
import org.server.OutgoingPacket;
import org.server.bytes.ByteBufEx;
public class SendLegendaryPokemonIsShow extends OutgoingPacket {
    private final short legendaryPokemonIndexId[] = {0,0,0,0,0};
    private final boolean isShow[] = {false,false,false,false,false};
    @Override
    public void encode(ByteBufEx buffer) throws Exception {
        buffer.writeByte(legendaryPokemonIndexId.length);
        for(int i = 0; i < legendaryPokemonIndexId.length; i++){
            buffer.writeShortLE(legendaryPokemonIndexId[i]);
            buffer.writeBoolean(isShow[i]);
        }
    }
}
