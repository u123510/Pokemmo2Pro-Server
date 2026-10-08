package org.pokemmo.gameserver.protocol.packets.s2c;

import org.pokemmo.gameserver.services.mail.MailService;
import org.server.OutgoingPacket;
import org.server.bytes.ByteBufEx;

import java.util.List;

/**
 * Sends one mailbox page using the field order consumed by the client mail UI.
 */
public final class SendEmailListPacket extends OutgoingPacket {
    private final short pageIndex;
    private final boolean sendMailType;
    private final List<MailService.MailListEntry> entries;

    public SendEmailListPacket(short pageIndex, boolean sendMailType) {
        this(pageIndex, sendMailType, List.of());
    }

    public SendEmailListPacket(short pageIndex, boolean sendMailType,
                               List<MailService.MailListEntry> entries) {
        this.pageIndex = pageIndex;
        this.sendMailType = sendMailType;
        this.entries = entries == null ? List.of() : List.copyOf(entries);
    }

    @Override
    public void encode(ByteBufEx buffer) {
        buffer.writeShortLE(pageIndex);
        buffer.writeBoolean(sendMailType);
        buffer.writeShortLE(entries.size());
        for (MailService.MailListEntry entry : entries) {
            buffer.writeLongLE(entry.mailId());
            // Keep the same St0 identity order as the detail packet:
            // recipient, then sender.
            buffer.writeLongLE(entry.recipientId());
            buffer.writeLongLE(entry.senderId());
            buffer.writeByte(entry.tagType());
            // In a received-mail row the client consumes senderName only when
            // tagType is zero. A sent-mail row has no senderName field and
            // consumes recipientName in the next branch.
            if (!sendMailType && entry.tagType() == 0) {
                buffer.writeUtf16LE(entry.senderName());
            }
            if (sendMailType) {
                buffer.writeUtf16LE(entry.recipientName());
            }
            buffer.writeIntLE(entry.createdAtEpochSeconds());
            buffer.writeUtf16LE(entry.title());
            buffer.writeByte(entry.unread() ? 1 : 0);
            buffer.writeByte(0);
        }
    }
}
