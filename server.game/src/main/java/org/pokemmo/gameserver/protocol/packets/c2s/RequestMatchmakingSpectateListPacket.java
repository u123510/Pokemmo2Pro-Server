package org.pokemmo.gameserver.protocol.packets.c2s;

import lombok.extern.slf4j.Slf4j;
import org.pokemmo.gameserver.protocol.packets.s2c.SendMatchmakingSpectateListPacket;
import org.server.IncomingPacket;
import org.server.Session;
import org.server.bytes.ByteBufEx;

/**
 * 客户端请求匹配赛观战对局列表封包 (C2S 0x4B, 对应客户端 f.ZI)
 */
@Slf4j
public class RequestMatchmakingSpectateListPacket extends IncomingPacket {

    private byte pageIndex;
    private String searchQuery;

    @Override
    public void decode(ByteBufEx buffer) {
        this.pageIndex = buffer.readByte();
        this.searchQuery = buffer.readUtf16LE();
    }

    @Override
    public void handle(Session session) throws Exception {
        log.debug("响应客户端请求匹配赛观战对局列表: session={}, pageIndex={}, query='{}'", session, pageIndex, searchQuery);
        session.send(new SendMatchmakingSpectateListPacket(this.pageIndex));
    }
}
