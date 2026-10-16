package org.pokemmo.gameserver.protocol.packets.s2c;

import org.server.OutgoingPacket;
import org.server.bytes.ByteBufEx;

/**
 * 要求客户端执行一次输入记录，携带执行时间参数。
 *
 * <p>当前没有调用方，时间单位（毫秒还是 tick）尚未验证，因此只做非负校验后原样写入。</p>
 */
public final class SendInputRecordPacket extends OutgoingPacket {

    private final int executeTime;

    public SendInputRecordPacket(int executeTime) {
        if (executeTime < 0) {
            throw new IllegalArgumentException("非法的输入记录执行时间: " + executeTime);
        }
        this.executeTime = executeTime;
    }

    @Override
    public void encode(ByteBufEx buffer) throws Exception {
        buffer.writeIntLE(executeTime);
    }
}
