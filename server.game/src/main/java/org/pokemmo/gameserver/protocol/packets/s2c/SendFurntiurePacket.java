package org.pokemmo.gameserver.protocol.packets.s2c;
import org.server.OutgoingPacket;
import org.server.bytes.ByteBufEx;

public class SendFurntiurePacket extends OutgoingPacket{

    @Override
    public void encode(ByteBufEx buffer) throws Exception {
        buffer.writeByte(0);
        buffer.writeShortLE(0);
    }
}
