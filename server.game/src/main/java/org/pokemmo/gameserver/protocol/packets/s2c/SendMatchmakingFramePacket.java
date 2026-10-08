package org.pokemmo.gameserver.protocol.packets.s2c;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.server.OutgoingPacket;
import org.server.bytes.ByteBufEx;

/**
 * 发送匹配赛/锦标赛界面数据 (S2C 0x47, 对应客户端 f.el_1)
 */
@NoArgsConstructor
@AllArgsConstructor
public class SendMatchmakingFramePacket extends OutgoingPacket {

    private boolean open = true;
    private boolean isTournament = false;

    @Override
    public void encode(ByteBufEx buffer) throws Exception {
        // Cy: 1 字节布尔值 (1 = 打开/更新, 0 = 关闭)
        buffer.writeByte(open ? 1 : 0);
        if (!open) {
            return;
        }

        // HE0: 1 字节布尔值 (0 = 匹配赛, 1 = 锦标赛)
        buffer.writeByte(isTournament ? 1 : 0);
        if (isTournament) {
            // 锦标赛分支：0 个进行中的锦标赛
            buffer.writeByte(0);
        } else {
            // 匹配赛分支：
            // 1. Jm0: 赛季结束时间戳 (4字节小端 int, 秒)
            int seasonEndSeconds = (int) (System.currentTimeMillis() / 1000L + 30L * 86400L);
            buffer.writeIntLE(seasonEndSeconds);

            // 2. b: 分级状态数量 (1字节)
            // av_1 代码: 0=OU单打, 1=Ubers单打, 2=OU双打, 3=Ubers双打, 4=UU单打, 5=UU双打, 6=NU单打, 7=NU双打, 8=随机单打, 9=随机双打
            byte tierCount = 10;
            buffer.writeByte(tierCount);
            for (byte tier = 0; tier < tierCount; tier++) {
                buffer.writeByte(tier); // 分级代码 (0..9)
                buffer.writeByte(1);    // 启用状态 (1 = 启用)
                buffer.writeLongLE(0L); // vn
                buffer.writeLongLE(0L); // unused
            }

            // 3. n: 玩家各分级战绩 (1字节, pz_2[])
            buffer.writeByte(tierCount);
            for (byte tier = 0; tier < tierCount; tier++) {
                buffer.writeShortLE(1000); // 初始积分 / MMR (1000)
                buffer.writeShortLE(0);    // 排位 / 场次
                buffer.writeByte(tier);    // 分级代码
                buffer.writeByte(0);       // 奖励条目数量 (0)
                buffer.writeByte(0);       // 记录条目数量 (0)
            }
        }

        // 4. hT: 规则 / 禁用列表数量 (1字节, Q10())
        buffer.writeByte(0);
    }
}
