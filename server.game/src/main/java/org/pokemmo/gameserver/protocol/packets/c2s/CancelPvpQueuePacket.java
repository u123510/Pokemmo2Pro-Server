package org.pokemmo.gameserver.protocol.packets.c2s;

import lombok.extern.slf4j.Slf4j;
import org.server.IncomingPacket;
import org.server.Session;
import org.server.bytes.ByteBufEx;

/**
 * 客户端取消匹配赛排队封包 (C2S 0x49, 对应客户端 f.a8_0)
 */
@Slf4j
public class CancelPvpQueuePacket extends IncomingPacket {

    @Override
    public void decode(ByteBufEx buffer) {
        // 无载荷字段
    }

    @Override
    public void handle(Session session) throws Exception {
        log.debug("收到客户端取消匹配赛排队请求: session={}", session);
    }
}
