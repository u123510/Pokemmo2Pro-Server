package org.pokemmo.gameserver.protocol.packets.s2c;

import org.server.OutgoingPacket;
import org.server.bytes.ByteBufEx;

public class SendUpdateFrameAperturePacket extends OutgoingPacket {
    private final byte frameApertureType;
    private final boolean isLockApertureType;
    public SendUpdateFrameAperturePacket(int frameApertureType, boolean isLockApertureType) {
        this.frameApertureType = (byte) frameApertureType;
        this.isLockApertureType = isLockApertureType;
    }

    @Override
    public void encode(ByteBufEx buffer) throws Exception {
        //0-6 is Aperture Size 7 is regular
        buffer.writeByte(frameApertureType);
        buffer.writeBoolean(isLockApertureType);
    }
}
