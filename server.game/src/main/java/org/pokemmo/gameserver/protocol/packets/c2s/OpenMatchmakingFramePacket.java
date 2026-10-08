package org.pokemmo.gameserver.protocol.packets.c2s;

import lombok.extern.slf4j.Slf4j;
import org.pokemmo.gameserver.protocol.packets.s2c.SendMatchmakingFramePacket;
import org.server.IncomingPacket;
import org.server.Session;
import org.server.bytes.ByteBufEx;

/**
 * 客户端点击匹配赛/锦标赛报名请求封包 (C2S 0x46, 对应客户端 f.b4_0)
 */
@Slf4j
public class OpenMatchmakingFramePacket extends IncomingPacket {

    private boolean isTournament;

    @Override
    public void decode(ByteBufEx buffer) {
        this.isTournament = (buffer.readByte() & 0xFF) == 1;
    }

    @Override
    public void handle(Session session) throws Exception {
        log.debug("响应客户端请求打开匹配赛/锦标赛界面: session={}, isTournament={}", session, isTournament);
        session.send(new SendMatchmakingFramePacket(true, isTournament));
    }
}
