package org.pokemmo.gameserver.protocol.packets.s2c;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.server.OutgoingPacket;
import org.server.bytes.ByteBufEx;

/**
 * 发送匹配赛观战对局列表封包 (S2C 0x4E, 对应客户端 f.o8_0)
 */
@NoArgsConstructor
@AllArgsConstructor
public class SendMatchmakingSpectateListPacket extends OutgoingPacket {

    private byte pageIndex;

    @Override
    public void encode(ByteBufEx buffer) throws Exception {
        // sa0 (2字节小端 short): 当前可观战对局总数 (0 条)
        buffer.writeShortLE(0);
        // Op (1字节): 当前页码/分级索引 (需与客户端当前选择的 D10 一致)
        buffer.writeByte(pageIndex);
        // Z9 数组长度 (1字节): 当前返回的对局条数 (0 条)
        buffer.writeByte(0);
    }
}
