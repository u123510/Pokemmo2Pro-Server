package org.pokemmo.gameserver.protocol.packets.s2c;

import org.server.OutgoingPacket;
import org.server.bytes.ByteBufEx;

public class SendScanEnvironmentWithWhiteList extends OutgoingPacket {
    private short hashCount = (short) 0x180;
    private short bitSetSize = (short) 3;
    private int [] bitSet = {
            (byte)0x00,(byte)0x37,(byte)0x9E,(byte)0x3C,(byte)0x83,(byte)0x71,(byte)0x94,(byte)0x26,(byte)0x18,(byte)0xC9,
            (byte)0x3D,(byte)0x87,(byte)0x6B,(byte)0x8F,(byte)0xE7,(byte)0x94,(byte)0x7E,(byte)0xCA,(byte)0x59,(byte)0x06,
            (byte)0x8F,(byte)0xF8,(byte)0x48,(byte)0x74,(byte)0xDC,(byte)0xC8,(byte)0x08,(byte)0x95,(byte)0x46,(byte)0xD1,
            (byte)0x22,(byte)0xDE,(byte)0x08,(byte)0x37,(byte)0x75,(byte)0x93,(byte)0x76,(byte)0x2F,(byte)0x1A,(byte)0x68,
            (byte)0xE9,(byte)0xFA,(byte)0x31,(byte)0x63,(byte)0x62,(byte)0x9C,(byte)0x71,(byte)0x97,(byte)0x13

    };
    @Override
    public void encode(ByteBufEx buffer) throws Exception {
        buffer.writeShortLE(hashCount);
        buffer.writeShortLE(bitSetSize);
        buffer.writeShortLE(bitSet.length);
        for(int i = 0;i< bitSet.length;i++){
            buffer.writeByte(bitSet[i]);
        }
    }
}
