package org.pokemmo.gameserver.protocol.packets.s2c;

import java.util.List;
import java.util.Objects;

import org.pokemmo.gameserver.codecs.Codecs;
import org.pokemmo.gameserver.game.gtl.GtlItemListing;
import org.pokemmo.gameserver.game.gtl.GtlListingEntry;
import org.pokemmo.gameserver.game.gtl.GtlPokemonListing;
import org.pokemmo.gameserver.game.gtl.GtlListType;
import org.server.OutgoingPacket;
import org.server.bytes.ByteBufEx;

/**
 * Sends a GTL result page. The first byte echoes the client request sequence
 * so the client can discard stale search responses.
 */
public class SendGTLPacket extends OutgoingPacket {
    private final byte requestSequence;
    private final GtlListType listType;
    private final short pageIndex;
    private final int totalOrResult;
    private final List<GtlListingEntry> listings;

    public SendGTLPacket(byte requestSequence, GtlListType listType, short pageIndex, int totalOrResult) {
        this(requestSequence, listType, pageIndex, totalOrResult, List.of());
    }

    public SendGTLPacket(
            byte requestSequence,
            GtlListType listType,
            short pageIndex,
            int totalOrResult,
            List<GtlListingEntry> listings) {
        this.requestSequence = requestSequence;
        this.listType = Objects.requireNonNull(listType, "listType");
        this.pageIndex = pageIndex;
        this.totalOrResult = totalOrResult;
        this.listings = List.copyOf(listings);
    }

    @Override
    public void encode(ByteBufEx buffer) throws Exception {
        if (listings.size() > 0xFF) {
            throw new IllegalStateException("Too many GTL entries in one page: " + listings.size());
        }
        buffer.writeByte(requestSequence);
        buffer.writeByte(listType.getType());
        buffer.writeShortLE(pageIndex);
        buffer.writeIntLE(totalOrResult);
        buffer.writeByte(listings.size());

        for (GtlListingEntry listing : listings) {
            buffer.writeLongLE(listing.listingId());
            buffer.writeByte(listing.listingType().getType());
            buffer.writeIntLE(listing.unitPrice());
            buffer.writeIntLE(listing.createdAtEpochSeconds());
            buffer.writeIntLE(listing.expiresAtEpochSeconds());
            buffer.writeShortLE(listing.amount());

            if (listType == GtlListType.OWN_LISTINGS) {
                buffer.writeByte(listing.status());
                buffer.writeShortLE(listing.soldAmount());

            }

            if (listing instanceof GtlItemListing itemListing) {
                buffer.writeShortLE(itemListing.itemIndexId());
                buffer.writeByte(itemListing.colorId());
                continue;
            }

            GtlPokemonListing pokemonListing = (GtlPokemonListing) listing;
            if (listType == GtlListType.OWN_LISTINGS) {
                // OWN_LISTINGS always includes the two item fields before an
                // optional Pokemon payload, even for Pokemon entries.
                buffer.writeShortLE(0);
                buffer.writeByte(0);
            }
            buffer.writeByte(1);
            Codecs.POKEMON_CODEC.encode(buffer, pokemonListing.pokemon());
            for (short iv : pokemonListing.pokemon().getPokemonIvs()) {
                buffer.writeShortLE(iv);
            }
        }

        // The client always reads an item-price map after an ITEM result page.
        if (listType == GtlListType.ITEM) {
            buffer.writeByte(0);
        }
    }
}
