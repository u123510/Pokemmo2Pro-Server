package org.pokemmo.loginserver.protocol.packets.s2c;

import lombok.RequiredArgsConstructor;
import org.server.OutgoingPacket;
import org.server.bytes.ByteBufEx;
@RequiredArgsConstructor
public class SendMfaChangePacket extends OutgoingPacket {
    private final byte flag;
    private final String email;
    @Override
    public void encode(ByteBufEx buffer){
        buffer.writeByte(flag);
        buffer.writeUtf16LE(email);
    }
}
