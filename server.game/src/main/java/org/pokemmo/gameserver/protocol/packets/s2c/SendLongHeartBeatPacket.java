package org.pokemmo.gameserver.protocol.packets.s2c;

import org.server.OutgoingPacket;
import org.server.bytes.ByteBufEx;

public class SendLongHeartBeatPacket extends OutgoingPacket{
        @Override
        public void encode(ByteBufEx buffer) throws Exception {
            int time = (int) (System.currentTimeMillis() / 1000);
            buffer.writeIntLE(time);
        }
}
