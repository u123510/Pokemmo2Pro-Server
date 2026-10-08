package org.pokemmo.gameserver.protocol.packets.c2s;

import lombok.extern.slf4j.Slf4j;
import org.server.IncomingPacket;
import org.server.Session;
import org.server.bytes.ByteBufEx;

/**
 * 客户端关闭匹配赛/锦标赛界面封包 (C2S 0x47, 对应客户端 f.SA)
 */
@Slf4j
public class CloseMatchmakingFramePacket extends IncomingPacket {

    @Override
    public void decode(ByteBufEx buffer) {
        // 无载荷字段
    }

    @Override
    public void handle(Session session) throws Exception {
        log.debug("收到客户端关闭匹配赛/锦标赛界面请求: session={}", session);
    }
}
