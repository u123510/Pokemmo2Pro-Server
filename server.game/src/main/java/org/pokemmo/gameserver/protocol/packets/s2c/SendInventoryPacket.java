package org.pokemmo.gameserver.protocol.packets.s2c;

import org.server.OutgoingPacket;
import org.server.bytes.ByteBufEx;
import org.pokemmo.db.jooq.tables.records.InventoryRecord;
import org.pokemmo.db.jooq.tables.records.OwnedItemRecord;
import org.pokemmo.gameserver.codecs.Codecs;
import lombok.RequiredArgsConstructor;

import java.util.List;

@RequiredArgsConstructor
public class SendInventoryPacket extends OutgoingPacket {
    private final InventoryRecord inventory;
    private final List<OwnedItemRecord> items;
    private final boolean isExist = true;

    @Override
    public void encode(ByteBufEx buffer) throws Exception {
        buffer.writeByte(inventory.getId());
        buffer.writeBoolean(isExist);
        buffer.writeShortLE(items.size());
        for (OwnedItemRecord item : items) {
            Codecs.ITEM_CODEC.encode(buffer, item);
        }
    }
}
