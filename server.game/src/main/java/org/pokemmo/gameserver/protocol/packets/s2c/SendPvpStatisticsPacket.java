package org.pokemmo.gameserver.protocol.packets.s2c;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.server.OutgoingPacket;
import org.server.bytes.ByteBufEx;

/**
 * 发送 PvP 统计界面配置 (S2C 0x60, 对应客户端 f.PE0)
 */
@NoArgsConstructor
@AllArgsConstructor
public class SendPvpStatisticsPacket extends OutgoingPacket {

    private byte monthCount = 3;

    @Override
    public void encode(ByteBufEx buffer) throws Exception {
        // bx: 可选月份数量 (1字节, 必须 >= 1，客户端会创建最近 N 个月的下拉选项并默认选中第 0 项)
        buffer.writeByte(monthCount);
        // nl0: 规则/禁用限制列表数量 (1字节, Q10() 返回 HZ[])
        buffer.writeByte(0);
    }
}
