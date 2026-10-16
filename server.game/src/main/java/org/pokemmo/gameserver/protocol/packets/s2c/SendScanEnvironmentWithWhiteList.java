package org.pokemmo.gameserver.protocol.packets.s2c;

import org.server.OutgoingPacket;
import org.server.bytes.ByteBufEx;

/**
 * 下发“排除白名单”的环境扫描位图。
 *
 * <p>字段顺序为 hashCount + bitSetSize + 位图长度 + 位图字节。抓包中 bitSetSize(3) 与
 * 实际写出的位图长度(49) 不一致，含义尚未验证；这里保持原有字节序列不变，仅把 int[]
 * 换成 byte[] 并去掉越界的隐式转换。</p>
 */
public final class SendScanEnvironmentWithWhiteList extends OutgoingPacket {

    private static final short HASH_COUNT = (short) 0x180;
    private static final short BIT_SET_SIZE = 3;
    private static final byte[] BIT_SET = {
            (byte) 0x00, (byte) 0x37, (byte) 0x9E, (byte) 0x3C, (byte) 0x83, (byte) 0x71, (byte) 0x94, (byte) 0x26,
            (byte) 0x18, (byte) 0xC9, (byte) 0x3D, (byte) 0x87, (byte) 0x6B, (byte) 0x8F, (byte) 0xE7, (byte) 0x94,
            (byte) 0x7E, (byte) 0xCA, (byte) 0x59, (byte) 0x06, (byte) 0x8F, (byte) 0xF8, (byte) 0x48, (byte) 0x74,
            (byte) 0xDC, (byte) 0xC8, (byte) 0x08, (byte) 0x95, (byte) 0x46, (byte) 0xD1, (byte) 0x22, (byte) 0xDE,
            (byte) 0x08, (byte) 0x37, (byte) 0x75, (byte) 0x93, (byte) 0x76, (byte) 0x2F, (byte) 0x1A, (byte) 0x68,
            (byte) 0xE9, (byte) 0xFA, (byte) 0x31, (byte) 0x63, (byte) 0x62, (byte) 0x9C, (byte) 0x71, (byte) 0x97,
            (byte) 0x13
    };

    @Override
    public void encode(ByteBufEx buffer) throws Exception {
        buffer.writeShortLE(HASH_COUNT);
        buffer.writeShortLE(BIT_SET_SIZE);
        buffer.writeShortLE((short) BIT_SET.length);
        buffer.writeBytes(BIT_SET);
    }
}
