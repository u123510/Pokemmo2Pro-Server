package org.pokemmo.gameserver.protocol.packets.c2s;

import lombok.extern.slf4j.Slf4j;
import org.pokemmo.gameserver.protocol.packets.s2c.SendTournamentListPacket;
import org.server.IncomingPacket;
import org.server.Session;
import org.server.bytes.ByteBufEx;

/**
 * 客户端请求锦标赛列表数据封包 (C2S 0x75, 对应客户端 f.am_1)
 */
@Slf4j
public class RequestTournamentListPacket extends IncomingPacket {

    private byte pageSequence;
    private boolean isHistory;
    private short pageIndex;

    @Override
    public void decode(ByteBufEx buffer) {
        this.pageSequence = buffer.readByte();
        this.isHistory = (buffer.readByte() & 0xFF) == 1;
        this.pageIndex = buffer.readShortLE();
    }

    @Override
    public void handle(Session session) throws Exception {
        log.debug("响应客户端请求锦标赛列表: session={}, seq={}, isHistory={}, pageIndex={}",
                session, pageSequence, isHistory, pageIndex);
        session.send(new SendTournamentListPacket(pageSequence, isHistory, pageIndex));
    }
}
