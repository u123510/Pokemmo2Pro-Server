package org.pokemmo.gameserver.protocol.packets.s2c;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.server.OutgoingPacket;
import org.server.bytes.ByteBufEx;

/**
 * 发送锦标赛列表数据封包 (S2C 0x75, 对应客户端 f.xf0_0)
 */
@NoArgsConstructor
@AllArgsConstructor
public class SendTournamentListPacket extends OutgoingPacket {

    private byte pageSequence;
    private boolean isHistory;
    private short pageIndex;

    @Override
    public void encode(ByteBufEx buffer) throws Exception {
        // Vh0 (1字节): 请求的序列号，客户端用于与当前请求同步
        buffer.writeByte(pageSequence);
        // B (1字节): 是否为历史锦标赛
        buffer.writeByte(isHistory ? 1 : 0);
        // d (2字节小端 short): 当前页码
        buffer.writeShortLE(pageIndex);
        // PV (4字节小端 int): 锦标赛总条数 (当前为 0)
        buffer.writeIntLE(0);
        // count (1字节): 当前列表中的锦标赛条数 (当前为 0)
        buffer.writeByte(0);
    }
}
