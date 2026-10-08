package org.pokemmo.gameserver.protocol.packets.c2s;

import lombok.extern.slf4j.Slf4j;
import org.server.IncomingPacket;
import org.server.Session;
import org.server.bytes.ByteBufEx;

/**
 * 客户端点击观战指定匹配对局封包 (C2S 0x4C, 对应客户端 f.Nx0)
 */
@Slf4j
public class MatchmakingSpectateBattlePacket extends IncomingPacket {

    private boolean isPlayerSpectate;
    private int matchId;
    private long targetPlayerId;
    private short subId;

    @Override
    public void decode(ByteBufEx buffer) {
        this.isPlayerSpectate = (buffer.readByte() & 0xFF) == 1;
        if (this.isPlayerSpectate) {
            this.targetPlayerId = buffer.readLongLE();
            this.subId = buffer.readShortLE();
        } else {
            this.matchId = buffer.readIntLE();
        }
    }

    @Override
    public void handle(Session session) throws Exception {
        log.debug("收到客户端观战请求: session={}, isPlayer={}, matchId={}, targetPlayerId={}",
                session, isPlayerSpectate, matchId, targetPlayerId);
    }
}
