package org.pokemmo.gameserver.protocol.packets.c2s;

import lombok.Getter;
import lombok.ToString;
import org.pokemmo.gameserver.protocol.packets.s2c.SendPvpTierStatisticsPacket;
import org.server.IncomingPacket;
import org.server.Session;
import org.server.bytes.ByteBufEx;

/**
 * 客户端请求指定月份和分级的 PvP 统计使用率数据 (C2S 0x4D, 对应客户端 f.am0_0)
 */
@Getter
@ToString
public class RequestPvpTierStatisticsPacket extends IncomingPacket {

    private byte month;
    private byte tierId;
    private byte category;

    @Override
    public void decode(ByteBufEx buffer) {
        this.month = buffer.readByte();
        this.tierId = buffer.readByte();
        this.category = buffer.readByte();
    }

    @Override
    public void handle(Session session) throws Exception {
        // 回复对应分级的统计数据包 (S2C 0x5D)
        session.send(new SendPvpTierStatisticsPacket(month, category, tierId));
    }
}
