package org.pokemmo.gameserver.protocol.packets.s2c;

import org.server.OutgoingPacket;
import org.server.bytes.ByteBufEx;
import lombok.RequiredArgsConstructor;
@RequiredArgsConstructor
public class SendHookDetectShellCodePacket extends OutgoingPacket {
    private final byte[] shellCodeBuffer  ;
    @Override
    public void encode(ByteBufEx buffer) throws Exception {
          buffer.writeBytes(shellCodeBuffer);
    }
}
