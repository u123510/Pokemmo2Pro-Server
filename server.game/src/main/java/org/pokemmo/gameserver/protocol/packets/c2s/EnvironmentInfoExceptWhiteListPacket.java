package org.pokemmo.gameserver.protocol.packets.c2s;

import lombok.extern.slf4j.Slf4j;
import org.server.IncomingPacket;
import org.server.Session;
import org.server.bytes.ByteBufEx;

/**
 * 客户端上报的“排除白名单”环境信息。
 *
 * <p>与 {@link EnvironmentInfoExceptBlackListPacket} 同属一类上报，opcode 不同。
 * 条目结构尚未验证，因此原样保留整个 payload，并把旧实现中声明后从未赋值的
 * {@code hashSize}/{@code value}/{@code listSize}/{@code listValue} 字段删除，避免误导。</p>
 */
@Slf4j
public final class EnvironmentInfoExceptWhiteListPacket extends IncomingPacket {

    private byte[] payload = new byte[0];

    @Override
    public void decode(ByteBufEx buffer) {
        payload = buffer.readByteArray(buffer.readableBytes());
        log.debug("收到排除白名单的环境信息: payloadLength={}", payload.length);
    }

    @Override
    public void handle(Session session) {
        // 环境信息只供客户端自检，服务端不持久化，读取完整封包后丢弃。
        log.debug("忽略排除白名单的环境信息: payloadLength={}", payload.length);
    }
}
