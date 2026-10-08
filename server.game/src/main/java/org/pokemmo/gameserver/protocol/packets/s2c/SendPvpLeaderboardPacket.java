package org.pokemmo.gameserver.protocol.packets.s2c;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.server.OutgoingPacket;
import org.server.bytes.ByteBufEx;

/**
 * 发送PVP排行榜数据封包 (S2C 0x4C, 对应客户端 f.wf0_0)
 */
@NoArgsConstructor
@AllArgsConstructor
public class SendPvpLeaderboardPacket extends OutgoingPacket {

    private byte seasonIndex;
    private byte tierCode;

    @Override
    public void encode(ByteBufEx buffer) throws Exception {
        // EF0 (4字节小端 int: 排行榜总数/玩家排名)
        buffer.writeIntLE(0);
        // Qc0 (1字节: 赛季索引)
        buffer.writeByte(seasonIndex);
        // SW (1字节: 分级代码)
        buffer.writeByte(tierCode);
        // yS (2字节小端 short: 排行榜条目列表长度，0 条目)
        buffer.writeShortLE(0);
    }
}
