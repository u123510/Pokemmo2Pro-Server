package org.pokemmo.gameserver.protocol.packets.s2c;

import lombok.RequiredArgsConstructor;
import org.pokemmo.gameserver.game.trade.TradeStatusType;
import org.server.OutgoingPacket;
import org.server.bytes.ByteBufEx;

/** S2C 0x51: notifies the client about the other side's trade state. */
@RequiredArgsConstructor
public final class SendTradeStatusPacket extends OutgoingPacket {
    private final TradeStatusType status;

    @Override
    public void encode(ByteBufEx buffer) {
        buffer.writeByte(status.getType());
    }
}
