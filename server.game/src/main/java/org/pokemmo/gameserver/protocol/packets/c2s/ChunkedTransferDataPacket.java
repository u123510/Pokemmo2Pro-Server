package org.pokemmo.gameserver.protocol.packets.c2s;

import lombok.extern.slf4j.Slf4j;
import org.server.IncomingPacket;
import org.server.Session;
import org.server.bytes.ByteBufEx;

/**
 * 客户端上报的分块传输数据。
 *
 * <p>命名参考 OpenMMO 的 {@code ChunkedTransferDataPacket}。字段按当前抓包顺序读取：
 * payloadSize + id + bufferRemain + fragmentLength + remainBuffer，字段语义尚未逐项验证。</p>
 */
@Slf4j
public final class ChunkedTransferDataPacket extends IncomingPacket {
    private static final int HEADER_SIZE = Long.BYTES + Integer.BYTES + Short.BYTES;

    private long id;
    private int bufferRemain;
    private short fragmentLength;
    private byte[] remainBuffer = new byte[0];

    @Override
    public void decode(ByteBufEx buffer) {
        long payloadSize = buffer.readLongLE();
        id = buffer.readLongLE();
        bufferRemain= buffer.readIntLE();
        fragmentLength = buffer.readShortLE();
        remainBuffer = buffer.readByteArray(fragmentLength);
    }

    @Override
    public void handle(Session session) {
    }
}