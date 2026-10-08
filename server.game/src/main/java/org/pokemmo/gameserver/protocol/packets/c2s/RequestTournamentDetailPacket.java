package org.pokemmo.gameserver.protocol.packets.c2s;

import lombok.extern.slf4j.Slf4j;
import org.server.IncomingPacket;
import org.server.Session;
import org.server.bytes.ByteBufEx;

/**
 * 客户端请求指定锦标赛详情封包 (C2S 0x77, 对应客户端 f.bk_1)
 */
@Slf4j
public class RequestTournamentDetailPacket extends IncomingPacket {

    private long tournamentId;

    @Override
    public void decode(ByteBufEx buffer) {
        this.tournamentId = buffer.readLongLE();
    }

    @Override
    public void handle(Session session) throws Exception {
        log.debug("收到客户端请求锦标赛详情: session={}, tournamentId={}", session, tournamentId);
    }
}
