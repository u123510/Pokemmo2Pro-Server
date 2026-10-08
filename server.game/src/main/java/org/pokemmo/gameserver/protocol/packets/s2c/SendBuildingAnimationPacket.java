package org.pokemmo.gameserver.protocol.packets.s2c;

import org.server.OutgoingPacket;
import org.server.bytes.ByteBufEx;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class SendBuildingAnimationPacket extends OutgoingPacket {
    private final short buildingIndex;
    private final short buildingDoorIndex;
    private final boolean isOpen;
    @Override
    public void encode(ByteBufEx buffer) throws Exception {
        buffer.writeShortLE(buildingIndex);
        buffer.writeShortLE(buildingDoorIndex);
        buffer.writeBoolean(isOpen);
    }
}
