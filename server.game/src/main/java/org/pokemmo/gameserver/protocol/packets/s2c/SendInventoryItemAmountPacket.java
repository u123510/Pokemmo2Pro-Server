package org.pokemmo.gameserver.protocol.packets.s2c;

import lombok.RequiredArgsConstructor;
import org.server.OutgoingPacket;
import org.server.bytes.ByteBufEx;

/** Updates the amount of an existing inventory entry without replacing its container. */
@RequiredArgsConstructor
public final class SendInventoryItemAmountPacket extends OutgoingPacket {
    private final long itemId;
    private final short amount;

    @Override
    public void encode(ByteBufEx buffer) {
        buffer.writeLongLE(itemId);
        buffer.writeShortLE(amount);
    }
}
