package org.pokemmo.gameserver.protocol.packets.c2s;

import org.pokemmo.gameserver.protocol.packets.s2c.SendPvpStatisticsPacket;
import org.server.IncomingPacket;
import org.server.Session;
import org.server.bytes.ByteBufEx;

/**
 * 客户端请求打开 PvP 统计界面 (C2S 0x44, 对应客户端 f.sd_2)
 */
public class PvpStatisticsPacket extends IncomingPacket {

    @Override
    public void decode(ByteBufEx buffer) {
        // 无载荷 (0字节)
    }

    @Override
    public void handle(Session session) throws Exception {
        // 下发 S2C 0x60，配置提供最近 3 个月的统计选项
        session.send(new SendPvpStatisticsPacket((byte) 3));
    }
}
