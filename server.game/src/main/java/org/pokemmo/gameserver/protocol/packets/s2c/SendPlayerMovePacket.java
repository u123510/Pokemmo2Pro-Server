package org.pokemmo.gameserver.protocol.packets.s2c;

import org.server.OutgoingPacket;
import org.server.bytes.ByteBufEx;

public class SendPlayerMovePacket extends OutgoingPacket {
    private long characterId;
     /*
     if ((packetId & 1) != 0) {
        this.regionIndexId = this.rBuffer.get();
     }
        if ((packetId & 2) != 0) {
        this.GBAmapId = this.rBuffer.get();
        this.mapHeaderIdOrGBAmapGroupId = this.rBuffer.get();
    }
        if ((packetId & 4) != 0) {
        this.x = this.rBuffer.getShort();
        this.y = this.rBuffer.getShort();
    } else {
        this.x = this.rBuffer.get();
        this.y = this.rBuffer.get();
    }
        this.height = (packetId & 8) != 0 ? this.rBuffer.get() : (byte) 0;
        this.flag = this.rBuffer.get();
     */
    @Override
    public void encode(ByteBufEx buffer) throws Exception {

    }
}
