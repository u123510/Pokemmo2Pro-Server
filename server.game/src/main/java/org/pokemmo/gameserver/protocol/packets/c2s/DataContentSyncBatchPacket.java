package org.pokemmo.gameserver.protocol.packets.c2s;

import lombok.extern.slf4j.Slf4j;
import org.server.IncomingPacket;
import org.server.Session;
import org.server.bytes.ByteBufEx;

/**
 * 客户端上报的数据内容同步批次。
 *
 * <p>命名参考 OpenMMO 的 {@code DataContentSyncBatchPacket}。目前只确认了 opcode、方向和整包长度不固定，
 * 条目结构（键值对还是连续记录）尚未验证，因此这里原样保留整个 payload。</p>
 */
@Slf4j
public final class DataContentSyncBatchPacket extends IncomingPacket {

    private byte[] payload = new byte[0];

    @Override
    public void decode(ByteBufEx buffer) {
        payload = buffer.readByteArray(buffer.readableBytes());
    }

    @Override
    public void handle(Session session) {
    }
}
