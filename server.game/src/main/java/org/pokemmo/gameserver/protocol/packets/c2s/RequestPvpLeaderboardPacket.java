package org.pokemmo.gameserver.protocol.packets.c2s;

import lombok.extern.slf4j.Slf4j;
import org.pokemmo.gameserver.protocol.packets.s2c.SendPvpLeaderboardPacket;
import org.server.IncomingPacket;
import org.server.Session;
import org.server.bytes.ByteBufEx;

/**
 * 客户端请求指定赛季/分级排行榜封包 (C2S 0x4A, 对应客户端 f.Jp0)
 */
@Slf4j
public class RequestPvpLeaderboardPacket extends IncomingPacket {

    private byte seasonIndex;
    private byte tierCode;

    @Override
    public void decode(ByteBufEx buffer) {
        this.seasonIndex = buffer.readByte();
        this.tierCode = buffer.readByte();
    }

    @Override
    public void handle(Session session) throws Exception {
        log.debug("响应客户端请求PVP排行榜: session={}, season={}, tier={}", session, seasonIndex, tierCode);
        session.send(new SendPvpLeaderboardPacket(seasonIndex, tierCode));
    }
}
