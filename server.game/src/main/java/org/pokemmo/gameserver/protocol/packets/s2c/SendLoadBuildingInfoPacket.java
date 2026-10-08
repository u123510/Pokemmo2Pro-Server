package org.pokemmo.gameserver.protocol.packets.s2c;
import lombok.RequiredArgsConstructor;
import org.server.OutgoingPacket;
import org.server.bytes.ByteBufEx;
import org.pokemmo.gameserver.game.building.BulidingType;
@RequiredArgsConstructor
public class SendLoadBuildingInfoPacket extends OutgoingPacket{
    private final BulidingType bulidingType;//house类型
    private boolean isLoadBuildingData = false;
    private byte[] buildContainerArray = new byte[0];
    @Override
    public void encode(ByteBufEx buffer) throws Exception {
        buffer.writeByte(bulidingType.getType());
        buffer.writeBoolean(isLoadBuildingData);
        if(isLoadBuildingData) {
            buffer.writeLongLE(0);//unuse
            buffer.writeByte(0);//unuse
            buffer.writeByte(0);//unuse
            buffer.writeShortLE(buildContainerArray.length);
            buffer.writeBytes(buildContainerArray);
        }
    }
}
