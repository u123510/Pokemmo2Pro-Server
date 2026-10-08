package org.pokemmo.gameserver.protocol.packets.s2c;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.server.OutgoingPacket;
import org.server.bytes.ByteBufEx;

/**
 * 发送分级 PvP 统计数据列表 (S2C 0x5D, 对应客户端 f.IR)
 */
@NoArgsConstructor
@AllArgsConstructor
public class SendPvpTierStatisticsPacket extends OutgoingPacket {

    private byte month;
    private byte category;
    private byte tierId;

    @Override
    public void encode(ByteBufEx buffer) throws Exception {
        // hasData: >= 1 表示存在有效统计数据
        buffer.writeByte(1);
        // Um0: 月份
        buffer.writeByte(month);
        // Ks0: 分类 (对应客户端 Cq 枚举 ordinal)
        buffer.writeByte(category);
        // iT: 分级 (对应客户端 N2 枚举 yz 代码)
        buffer.writeByte(tierId);

        // Vd 时间戳 (4字节 int LE)
        int updatedTime = (int) (System.currentTimeMillis() / 1000L);
        buffer.writeIntLE(updatedTime);

        // 缓存超时时间偏移 (8字节 long LE, 1小时)
        buffer.writeLongLE(3600000L);

        // kz0: 总对战场次 (4字节 int LE, 当前为 0)
        buffer.writeIntLE(0);

        // count: 统计宝可梦条目数量 (2字节 short LE, 0 条目)
        buffer.writeShortLE(0);
    }
}
