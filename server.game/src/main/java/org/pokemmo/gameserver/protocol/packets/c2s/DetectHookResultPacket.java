package org.pokemmo.gameserver.protocol.packets.c2s;

import lombok.extern.slf4j.Slf4j;
import org.server.IncomingPacket;
import org.server.Session;
import org.server.bytes.ByteBufEx;

/**
 * 客户端上报的 Hook 检测结果。
 *
 * <p>封包依次给出结果标识、客户端声明的结果总长度和本次分片长度，随后是分片数据。
 * 客户端真实结构尚未通过抓包验证，因此这里只校验长度并原样保留分片，
 * 不根据 {@code totalBufferLength} 推断完整结果内容。</p>
 */
@Slf4j
public final class DetectHookResultPacket extends IncomingPacket {

    /** 结果头长度：long id + int totalBufferLength + short fragmentLength。 */
    private static final int HEADER_SIZE = Long.BYTES + Integer.BYTES + Short.BYTES;

    private long id;
    private int totalBufferLength;
    private short fragmentLength;
    private byte[] resultBuffer = new byte[0];

    @Override
    public void decode(ByteBufEx buffer) {
        int payloadSize = buffer.readableBytes();
        if (payloadSize < HEADER_SIZE) {
            throw new IllegalArgumentException("Hook 检测结果封包长度不足: expected>="
                    + HEADER_SIZE + ", actual=" + payloadSize);
        }

        id = buffer.readLongLE();
        totalBufferLength = buffer.readIntLE();
        fragmentLength = buffer.readShortLE();

        if (totalBufferLength < 0) {
            throw new IllegalArgumentException("非法的 Hook 检测结果总长度: " + totalBufferLength);
        }
        if (fragmentLength < 0 || fragmentLength != buffer.readableBytes()) {
            throw new IllegalArgumentException("非法的 Hook 检测分片长度: declared="
                    + fragmentLength + ", remaining=" + buffer.readableBytes());
        }

        resultBuffer = buffer.readByteArray(fragmentLength);
        log.debug("收到 Hook 检测结果: id={}, totalBufferLength={}, fragmentLength={}",
                id, totalBufferLength, fragmentLength);
    }

    @Override
    public void handle(Session session) {
        // 该结果只用于诊断，不参与业务状态：读取完整封包后直接丢弃，不打断客户端上报。
        log.debug("忽略 Hook 检测结果: id={}, fragmentLength={}", id, fragmentLength);
    }
}
