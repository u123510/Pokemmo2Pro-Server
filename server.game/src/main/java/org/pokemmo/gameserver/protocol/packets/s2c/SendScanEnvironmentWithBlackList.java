package org.pokemmo.gameserver.protocol.packets.s2c;

import org.server.OutgoingPacket;
import org.server.bytes.ByteBufEx;

/**
 * 下发“排除黑名单”的环境扫描位图。
 *
 * <p>位图语义尚未验证。抓包中 hashCount 和 bitSetSize 都是 0，因此当前只写入 4 字节头部，
 * 不写出任何位图字节；这里保持同样的长度，避免改变客户端可见的协议数据。</p>
 */
public final class SendScanEnvironmentWithBlackList extends OutgoingPacket {

    private static final short idSize = 0;
    private static final short bufferSize = 0;
    /** 抓包记录的位图字节，当前 bitSetSize 为 0，不会写出。 */
    private static final byte[] payload = {
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
        buffer.writeShortLE(idSize);
        buffer.writeShortLE(bufferSize);
        buffer.writeBytes(payload , 0, bufferSize);
    }
}
