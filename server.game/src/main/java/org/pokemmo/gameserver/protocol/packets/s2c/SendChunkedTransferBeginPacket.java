package org.pokemmo.gameserver.protocol.packets.s2c;

import org.server.OutgoingPacket;
import org.server.bytes.ByteBufEx;

/**
 * 通知客户端开始接收分块传输数据。
 *
 * <p>命名参考 OpenMMO 的 {@code ChunkedTransferBeginPacket}。字段顺序为 id + unk2 + 固定 payload，
 * 字节序列按抓包记录保留，字段语义尚未验证。</p>
 */
public final class SendChunkedTransferBeginPacket extends OutgoingPacket {

    private static final long id = 0L;
    private static final int unk2= 46701;
    private static final byte[] payload = {
            (byte) 0x47, (byte) 0x00, (byte) 0x30, (byte) 0x45, (byte) 0x02, (byte) 0x20, (byte) 0x4B, (byte) 0xD2,
            (byte) 0x28, (byte) 0x0E, (byte) 0xF1, (byte) 0x36, (byte) 0x8A, (byte) 0xA7, (byte) 0x30, (byte) 0x58,
            (byte) 0x14, (byte) 0x3E, (byte) 0x3F, (byte) 0x08, (byte) 0x98, (byte) 0x9E, (byte) 0xFE, (byte) 0x07,
            (byte) 0x40, (byte) 0x66, (byte) 0xEF, (byte) 0x9E, (byte) 0x8E, (byte) 0xF5, (byte) 0xFF, (byte) 0x7E,
            (byte) 0x59, (byte) 0x82, (byte) 0xA5, (byte) 0xFD, (byte) 0x22, (byte) 0x4F, (byte) 0x02, (byte) 0x21,
            (byte) 0x00, (byte) 0xA6, (byte) 0xE6, (byte) 0x9B, (byte) 0x18, (byte) 0xD4, (byte) 0x19, (byte) 0x14,
            (byte) 0x89, (byte) 0x97, (byte) 0x53, (byte) 0xB5, (byte) 0x04, (byte) 0x61, (byte) 0x59, (byte) 0xCE,
            (byte) 0x95, (byte) 0xC6, (byte) 0x48, (byte) 0x20, (byte) 0xB7, (byte) 0x91, (byte) 0x3F, (byte) 0x8B,
            (byte) 0xDD, (byte) 0x75, (byte) 0xF5, (byte) 0x4C, (byte) 0x0A, (byte) 0xEF, (byte) 0x23, (byte) 0x38,
            (byte) 0x00
    };

    @Override
    public void encode(ByteBufEx buffer) throws Exception {
        buffer.writeLongLE(id);
        buffer.writeIntLE(unk2);
        buffer.writeBytes(payload);
    }
}