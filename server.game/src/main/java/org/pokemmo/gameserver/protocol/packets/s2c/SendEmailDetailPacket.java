package org.pokemmo.gameserver.protocol.packets.s2c;

import io.netty.buffer.Unpooled;
import org.pokemmo.db.jooq.tables.records.OwnedItemRecord;
import org.pokemmo.db.jooq.tables.records.PokemonRecord;
import org.pokemmo.gameserver.codecs.Codecs;
import org.pokemmo.gameserver.game.pokemon.PokemonData;
import org.pokemmo.gameserver.services.mail.MailService;
import org.server.OutgoingPacket;
import org.server.bytes.ByteBufEx;

/** S2C 0x99: returns one mail detail in the client's mailbox projection. */
public final class SendEmailDetailPacket extends OutgoingPacket {
    private static final byte FAILURE = 0;
    private static final byte SUCCESS = 1;

    private final MailService.MailDetail detail;

    public SendEmailDetailPacket(MailService.MailDetail detail) {
        this.detail = detail;
    }

    @Override
    public void encode(ByteBufEx buffer) {
        if (detail == null) {
            // The client consumes a one-byte failure response and does not
            // attempt to parse the detail body in that case.
            buffer.writeByte(FAILURE);
            return;
        }

        boolean sentMailbox = detail.sentMailbox();
        // S2C 0x99 is wrapped by g_0: success, then mailbox direction, then
        // the common St0 detail payload parsed by yq0_0.m5(...). The client
        // passes direction == 1 for the sent-mail view and 0 for received mail.
        buffer.writeByte(SUCCESS);
        buffer.writeByte(sentMailbox ? 1 : 0);
        buffer.writeLongLE(detail.mailId());
        // St0 reads recipientId into O8 and senderId into switch$. For a
        // received mail O8 must therefore equal the local character, which is
        // also what enables the attachment controls in qu_2.A20(...).
        buffer.writeLongLE(detail.recipientId());
        buffer.writeLongLE(detail.senderId());
        byte tagType = detail.tagType();
        buffer.writeByte(tagType);
        // In the received-mail direction, yq0_0.m5(...) reads senderName only
        // for tag type zero. Sent mail reads recipientName in the next branch.
        if (!sentMailbox && tagType == 0) {
            buffer.writeUtf16LE(detail.senderName());
        }
        if (sentMailbox) {
            buffer.writeUtf16LE(detail.recipientName());
        }
        buffer.writeIntLE(detail.createdAtEpochSeconds());
        buffer.writeUtf16LE(detail.title());
        buffer.writeUtf16LE(detail.body());
        buffer.writeByte(detail.unread() ? 1 : 0);
        buffer.writeByte(0);

        // yq0_0.m5(..., true) always reads the attachment-count byte, including
        // sent-mail details. Sent mail simply has zero entries.
        int attachmentCount = sentMailbox
                ? 0 : Math.min(255, detail.attachments().size());
        buffer.writeByte(attachmentCount);
        if (!sentMailbox) {
            for (int slot = 0; slot < attachmentCount; slot++) {
                writeAttachment(buffer, (byte) slot, detail.attachments().get(slot));
            }
        }
    }

    private static void writeAttachment(ByteBufEx buffer, byte slot,
                                        MailService.MailAttachment attachment) {
        byte type = attachment == null ? 2 : attachment.type();
        if (type != 0 && type != 1 && type != 2) {
            type = 2;
        }
        buffer.writeByte(slot);
        buffer.writeByte(attachment != null && attachment.claimed() ? 1 : 0);
        buffer.writeByte(type);
        buffer.writeLongLE(attachment == null ? 0L : attachment.value());
        buffer.writeByte(0);

        if (attachment == null) {
            buffer.writeShortLE(0);
            buffer.writeShortLE(0);
            buffer.writeByte(0);
            return;
        }

        if (attachment instanceof MailService.ItemMailAttachment itemAttachment) {
            OwnedItemRecord item = itemAttachment.item();
            boolean hasItemFields = item != null && item.getItemIndexId() != null
                    && item.getItemAmount() != null && itemAttachment.amount() > 0;
            buffer.writeShortLE(hasItemFields ? item.getItemIndexId() : 0);
            buffer.writeShortLE(hasItemFields ? itemAttachment.amount() : 0);
            buffer.writeByte(hasItemFields && item.getInventoryId() != null
                    ? item.getInventoryId() : 0);
            if (!hasItemFields) {
                buffer.writeByte(0); // detail unavailable; keep the packet aligned
                return;
            }
            // Mail detail type 0 is parsed by the client's yq0_0.BM(), not by
            // the general ItemCodec. BM's minimal form is flags, object ID,
            // item index, amount and an A5 byte; flags=0 avoids optional data.
            buffer.writeByte(1); // item detail exists
            buffer.writeByte(0); // no owner/color/region/PVP extensions
            buffer.writeLongLE(item.getItemId());
            buffer.writeShortLE(item.getItemIndexId());
            buffer.writeShortLE(itemAttachment.amount());
            buffer.writeByte(0); // A5 enum value used by the item-slot widget
            return;
        }

        if (attachment instanceof MailService.PokemonMailAttachment pokemonAttachment) {
            PokemonRecord pokemon = pokemonAttachment.pokemon();
            boolean hasPokemonFields = pokemon != null && pokemon.getDexId() != null
                    && pokemon.getLevelValue() != null;
            buffer.writeShortLE(hasPokemonFields ? pokemon.getDexId() : 0);
            buffer.writeShortLE(hasPokemonFields ? pokemon.getLevelValue() : 0);
            buffer.writeByte(0);
            boolean pokemonDetailExists = false;
            if (!hasPokemonFields) {
                buffer.writeByte(0); // detail unavailable; keep the packet aligned
            } else {
                ByteBufEx encoded = new ByteBufEx(Unpooled.buffer());
                try {
                    PokemonData data = new PokemonData.Builder().setByRecord(pokemon).build();
                    Codecs.POKEMON_CODEC.encode(encoded, data, pokemon.getContainerId(),
                            pokemon.getContainerPosition());
                    buffer.writeByte(1); // Pokemon detail exists
                    buffer.writeBytes(encoded, encoded.readerIndex(), encoded.readableBytes());
                    pokemonDetailExists = true;
                } catch (RuntimeException exception) {
                    buffer.writeByte(0); // detail unavailable; keep the packet aligned
                } finally {
                    encoded.release();
                }
            }
            // The client consumes six reserved shorts only when the Pokemon
            // detail exists.
            if (pokemonDetailExists) {
                for (int index = 0; index < 6; index++) {
                    buffer.writeShortLE(0);
                }
            }
            return;
        }

        if (attachment instanceof MailService.MoneyMailAttachment) {
            buffer.writeShortLE(0);
            buffer.writeShortLE(0);
            buffer.writeByte(0);
            return;
        }

        // Unknown types have no client-side detail branch; the common fields are
        // complete, so leave the entry at this boundary rather than truncating
        // the whole mail packet.
    }
}
