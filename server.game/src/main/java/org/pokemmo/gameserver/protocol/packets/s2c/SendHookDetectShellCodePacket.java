package org.pokemmo.gameserver.protocol.packets.s2c;

import org.server.OutgoingPacket;
import org.server.bytes.ByteBufEx;

/**
 * 向客户端下发 Hook 检测使用的二进制载荷。
 *
 * <p>载荷内容由调用方提供，服务端只负责按原始字节写入。当前没有调用方，
 * 载荷用途（检测脚本还是原始机器码）尚未验证；构造时做防御性拷贝并拒绝空载荷。</p>
 */
public final class SendHookDetectShellCodePacket extends OutgoingPacket {

    private final byte[] shellCodeBuffer;

    public SendHookDetectShellCodePacket(byte[] shellCodeBuffer) {
        if (shellCodeBuffer == null || shellCodeBuffer.length == 0) {
            throw new IllegalArgumentException("Hook 检测载荷不能为空");
        }
        this.shellCodeBuffer = shellCodeBuffer.clone();
    }

    @Override
    public void encode(ByteBufEx buffer) throws Exception {
        buffer.writeBytes(shellCodeBuffer);
    }
}
