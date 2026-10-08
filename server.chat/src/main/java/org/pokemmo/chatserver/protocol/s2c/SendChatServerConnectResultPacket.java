package org.pokemmo.chatserver.protocol.s2c;

import lombok.RequiredArgsConstructor;
import org.server.OutgoingPacket;
import org.server.bytes.ByteBufEx;
@RequiredArgsConstructor
public class SendChatServerConnectResultPacket extends OutgoingPacket{
    private final boolean isSuccess;
    @Override
    public void encode(ByteBufEx buffer) {
        buffer.writeBoolean(isSuccess);
    }
}
