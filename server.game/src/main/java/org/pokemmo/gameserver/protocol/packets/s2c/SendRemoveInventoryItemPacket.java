package org.pokemmo.gameserver.protocol.packets.s2c;

import lombok.RequiredArgsConstructor;
import org.server.OutgoingPacket;
import org.server.bytes.ByteBufEx;

/** Removes one inventory entry by container and object ID. */
@RequiredArgsConstructor
public final class SendRemoveInventoryItemPacket extends OutgoingPacket {
    private final byte inventoryId;
    private final long itemId;

    @Override
    public void encode(ByteBufEx buffer) {
        buffer.writeByte(inventoryId);
        buffer.writeLongLE(itemId);
    }
}
