package org.pokemmo.gameserver.protocol.packets.s2c;

import lombok.RequiredArgsConstructor;
import org.server.OutgoingPacket;
import org.server.bytes.ByteBufEx;

@RequiredArgsConstructor
public final class SendTradeMoneyPacket extends OutgoingPacket {
    private final byte side;
    private final int money;

    @Override
    public void encode(ByteBufEx buffer) {
        buffer.writeByte(side);
        buffer.writeIntLE(money);
    }
}
