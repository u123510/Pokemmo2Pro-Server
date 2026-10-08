package org.pokemmo.gameserver.protocol.packets.s2c;

import org.server.OutgoingPacket;
import org.server.bytes.ByteBufEx;

public class SendInputRecordPacket extends OutgoingPacket {
    private final int executeTime;
    @Override
    public void encode(ByteBufEx buffer) throws Exception {
            buffer.writeIntLE(executeTime);
    }
    public SendInputRecordPacket(int executeTime) {
        this.executeTime = executeTime;
    }
}
