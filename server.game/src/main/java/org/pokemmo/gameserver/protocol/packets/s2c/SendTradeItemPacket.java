package org.pokemmo.gameserver.protocol.packets.s2c;

import lombok.RequiredArgsConstructor;
import org.pokemmo.gameserver.game.trade.TradeItemOffer;
import org.server.OutgoingPacket;
import org.server.bytes.ByteBufEx;

@RequiredArgsConstructor
public final class SendTradeItemPacket extends OutgoingPacket {
    private final byte side;
    private final short slot;
    private final TradeItemOffer item;

    @Override
    public void encode(ByteBufEx buffer) {
        buffer.writeByte(side);
        buffer.writeShortLE(slot);
        if (item == null) {
            buffer.writeLongLE(0L);
            buffer.writeShortLE(0);
            buffer.writeShortLE(0);
            buffer.writeByte(0);
            return;
        }
        buffer.writeLongLE(item.itemId());
        buffer.writeShortLE(item.itemIndexId());
        buffer.writeShortLE(item.amount());
        buffer.writeByte(item.colorId());
    }
}
