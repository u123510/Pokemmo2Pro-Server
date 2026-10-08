package org.pokemmo.gameserver.protocol.packets.s2c;
import org.server.OutgoingPacket;
import org.server.bytes.ByteBufEx;
public class SendPvpInfoPacket extends OutgoingPacket{
    private final byte recordsize = 0;
    private final long characterId = 1;
    private final byte pvpLevel = 9;
    private final byte pvpSeaSon = 21;
    private final float pvpMark = 500.00f;
    private final byte streak = 1;
    private final byte  oddsDifference= 1;
    private final int  winTimes = 1;
    private final int  allTimes = 5;
    @Override
    public void encode(ByteBufEx buffer) throws Exception {
        buffer.writeByte(recordsize );
        /*for(int i = 0;i<recordsize;i++){
            buffer.writeLongLE(characterId);
            buffer.writeByte(pvpLevel);
            buffer.writeByte(pvpSeaSon);
            buffer.writeFloatLE(pvpMark);
            buffer.writeShortLE(streak);
            buffer.writeByte(oddsDifference);
            buffer.writeIntLE(winTimes);
            buffer.writeIntLE(allTimes);
        }*/
    }
}
