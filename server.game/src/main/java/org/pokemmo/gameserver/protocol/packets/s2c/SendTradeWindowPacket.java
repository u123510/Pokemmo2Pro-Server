package org.pokemmo.gameserver.protocol.packets.s2c;

import lombok.RequiredArgsConstructor;
import org.server.OutgoingPacket;
import org.server.bytes.ByteBufEx;

@RequiredArgsConstructor
public final class SendTradeWindowPacket extends OutgoingPacket {
    private final byte flags;
    private final byte side;
    private final String playerName;

    @Override
    public void encode(ByteBufEx buffer) {
        buffer.writeByte(flags);
        buffer.writeByte(side);
        buffer.writeUtf16LE(playerName == null ? "" : playerName);
    }
}
