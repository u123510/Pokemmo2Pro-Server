package org.pokemmo.gameserver.protocol.packets.s2c;
import org.server.OutgoingPacket;
import org.server.bytes.ByteBufEx;

public class SendShortHeartBeatPacket extends OutgoingPacket{
    private final boolean isHeartBeat;
    @Override
    public void encode(ByteBufEx buffer) throws Exception {
        buffer.writeBoolean(isHeartBeat);
        buffer.writeLongLE(System.currentTimeMillis());
        if (!isHeartBeat){
            buffer.writeUtf16LE("PokeMMO2");//服务器名称 server Name
        }
    }
    public SendShortHeartBeatPacket(boolean isHeartBeat) {
        this.isHeartBeat= isHeartBeat;
    }
}
