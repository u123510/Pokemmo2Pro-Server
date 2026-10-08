package org.pokemmo.loginserver.protocol.packets.s2c;

import lombok.RequiredArgsConstructor;
import org.server.OutgoingPacket;
import org.server.bytes.ByteBufEx;
@RequiredArgsConstructor
public class SendUpdateLoginCredentialsKey extends OutgoingPacket {
    private final String username;
    private final String passwordCredentialsKey;

    @Override
    public void encode(ByteBufEx buffer){
        buffer.writeUtf16LE(username);
        buffer.writeUtf16LE(passwordCredentialsKey);
    }
}
