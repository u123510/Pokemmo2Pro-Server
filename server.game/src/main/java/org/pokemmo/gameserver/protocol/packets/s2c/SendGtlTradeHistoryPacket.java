package org.pokemmo.gameserver.protocol.packets.s2c;

import java.util.List;

import org.pokemmo.gameserver.game.gtl.GtlPurchaseHistoryEntry;
import org.server.OutgoingPacket;
import org.server.bytes.ByteBufEx;

/**
 * Sends the recent-trade groups consumed by the GTL history tab.
 */
public final class SendGtlTradeHistoryPacket extends OutgoingPacket {
    private static final byte TRADE_TYPE_GTL = 2;
    private static final int MAX_HISTORY_ENTRIES = 0xFF;

    private final List<GtlPurchaseHistoryEntry> entries;

    public SendGtlTradeHistoryPacket(List<GtlPurchaseHistoryEntry> entries) {
        this.entries = entries == null ? List.of() : List.copyOf(entries);
    }

    @Override
    public void encode(ByteBufEx buffer) {
        if (entries.size() > MAX_HISTORY_ENTRIES) {
            throw new IllegalStateException(
                    "Too many GTL trade-history entries: " + entries.size());
        }

        buffer.writeByte(entries.size());
        for (GtlPurchaseHistoryEntry entry : entries) {
            buffer.writeLongLE(entry.tradedAtEpochMillis());
            buffer.writeByte(TRADE_TYPE_GTL);
            buffer.writeByte(2);

            // The buyer sends money and receives the purchased listing.
            writeLine(buffer, (short) 0, (short) 0, entry.totalPrice(), true);
            writeLine(buffer, entry.itemIndexId(), entry.pokemonDexId(),
                    entry.amount(), false);
        }
    }

    private static void writeLine(
            ByteBufEx buffer, short itemIndexId, short pokemonDexId,
            int amount, boolean sent) {
        buffer.writeShortLE(itemIndexId);
        buffer.writeShortLE(pokemonDexId);
        buffer.writeIntLE(amount);
        buffer.writeByte(sent ? 1 : 0);
    }
}
