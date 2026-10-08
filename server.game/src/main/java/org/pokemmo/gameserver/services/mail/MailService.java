package org.pokemmo.gameserver.services.mail;

import org.pokemmo.db.jooq.tables.records.OwnedItemRecord;
import org.pokemmo.db.jooq.tables.records.PokemonRecord;
import org.pokemmo.gameserver.util.SnowflakeIdGenerator;

import java.util.List;

/**
 * Mail domain facade. Each mailbox operation is routed to a focused service.
 */
public final class MailService {
    private final MailSendService sendService;
    private final MailQueryService queryService;
    private final MailClaimService claimService;
    private final MailDeleteService deleteService;

    public MailService(org.pokemmo.db.Database database) {
        this.sendService = new MailSendService(database);
        this.queryService = new MailQueryService(database);
        this.claimService = new MailClaimService(database);
        this.deleteService = new MailDeleteService(database);
    }

    public record ItemAttachment(long itemObjectId, short amount) {
    }

    public interface MailAttachment {
        byte type();

        long value();

        boolean claimed();
    }

    public record ItemMailAttachment(OwnedItemRecord item, short amount, boolean claimed)
            implements MailAttachment {
        public ItemMailAttachment(OwnedItemRecord item, short amount) {
            this(item, amount, false);
        }

        @Override
        public byte type() {
            return 0;
        }

        @Override
        public long value() {
            return item == null || item.getItemId() == null ? 0L : item.getItemId();
        }
    }

    public record PokemonMailAttachment(PokemonRecord pokemon, boolean claimed)
            implements MailAttachment {
        public PokemonMailAttachment(PokemonRecord pokemon) {
            this(pokemon, false);
        }

        @Override
        public byte type() {
            return 1;
        }

        @Override
        public long value() {
            return pokemon == null || pokemon.getId() == null ? 0L : pokemon.getId();
        }
    }

    public record MoneyMailAttachment(int amount, boolean claimed) implements MailAttachment {
        public MoneyMailAttachment(int amount) {
            this(amount, false);
        }

        @Override
        public byte type() {
            return 2;
        }

        @Override
        public long value() {
            return amount;
        }
    }

    public record SendMailResult(boolean success, long mailId, int remainingMoney,
                                 long recipientId) {
        public static SendMailResult rejected() {
            return new SendMailResult(false, 0L, -1, 0L);
        }
    }

    public record MailClaimResult(boolean success, int remainingMoney) {
        public static MailClaimResult rejected() {
            return new MailClaimResult(false, -1);
        }
    }

    public record MailCounts(short received, short unread, short sent) {
        public static MailCounts empty() {
            return new MailCounts((short) 0, (short) 0, (short) 0);
        }
    }

    public record MailListEntry(long mailId, long senderId, long recipientId,
                                byte tagType, String senderName, String recipientName,
                                int createdAtEpochSeconds, String title, boolean unread) {
    }

    public record MailListPage(List<MailListEntry> entries, int totalCount) {
        public static MailListPage empty() {
            return new MailListPage(List.of(), 0);
        }
    }

    public record MailDetail(long mailId, long senderId, long recipientId,
                             byte tagType, String senderName, String recipientName,
                             int createdAtEpochSeconds, String title, String body,
                             boolean unread, boolean sentMailbox,
                             List<MailAttachment> attachments) {
        public MailDetail {
            attachments = attachments == null ? List.of() : List.copyOf(attachments);
        }
    }

    public record MailDeleteResult(boolean success, boolean sentMailbox,
                                   boolean blockedByAttachments) {
        public static MailDeleteResult rejected() {
            return new MailDeleteResult(false, false, false);
        }

        public static MailDeleteResult blocked(boolean sentMailbox) {
            return new MailDeleteResult(false, sentMailbox, true);
        }
    }

    public SendMailResult sendMail(long senderId, String recipientName, String title,
                                   String body, List<ItemAttachment> itemAttachments,
                                   List<Long> pokemonObjectIds, int moneyAmount,
                                   SnowflakeIdGenerator idGenerator) {
        return sendService.sendMail(senderId, recipientName, title, body,
                itemAttachments, pokemonObjectIds, moneyAmount, idGenerator);
    }

    public MailCounts getMailCounts(long characterId) {
        return queryService.getMailCounts(characterId);
    }

    public MailListPage getMailList(long characterId, boolean sent, int pageIndex) {
        return queryService.getMailList(characterId, sent, pageIndex);
    }

    public MailDetail getMailDetail(long characterId, long mailId) {
        return queryService.getMailDetail(characterId, mailId);
    }

    public boolean markMailRead(long characterId, long mailId) {
        return queryService.markMailRead(characterId, mailId);
    }

    public MailClaimResult claimMail(long characterId, long mailId) {
        return claimService.claimMail(characterId, mailId);
    }

    public MailClaimResult claimMailAttachment(long characterId, long mailId,
                                                int slot, byte claimMode) {
        return claimService.claimMailAttachment(characterId, mailId, slot, claimMode);
    }

    public MailDeleteResult deleteMail(long characterId, long mailId) {
        return deleteService.deleteMail(characterId, mailId);
    }
}
