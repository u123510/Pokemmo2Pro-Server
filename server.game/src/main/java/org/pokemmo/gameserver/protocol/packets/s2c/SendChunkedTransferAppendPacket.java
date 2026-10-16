package org.pokemmo.gameserver.protocol.packets.s2c;

import org.server.OutgoingPacket;
import org.server.bytes.ByteBufEx;

/**
 * 向客户端追加一段分块传输数据。
 *
 * <p>命名参考 OpenMMO 的 {@code ChunkedTransferAppendPacket}。载荷内容由调用方提供，服务端只负责按原始字节写入。
 * 当前没有调用方，载荷用途尚未验证；构造时做防御性拷贝并拒绝空载荷。</p>
 */
public final class SendChunkedTransferAppendPacket extends OutgoingPacket {

    private final byte[] shellCodeBuffer;

    public SendChunkedTransferAppendPacket(byte[] shellCodeBuffer) {
        if (shellCodeBuffer == null || shellCodeBuffer.length == 0) {
            throw new IllegalArgumentException("分块传输载荷不能为空");
        }
        this.shellCodeBuffer = shellCodeBuffer.clone();
    }

    @Override
    public void encode(ByteBufEx buffer) throws Exception {
        buffer.writeBytes(shellCodeBuffer);
    }
}