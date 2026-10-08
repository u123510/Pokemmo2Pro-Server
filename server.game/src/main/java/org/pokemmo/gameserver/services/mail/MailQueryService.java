package org.pokemmo.gameserver.services.mail;

import lombok.extern.slf4j.Slf4j;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.pokemmo.db.Database;
import org.pokemmo.db.jooq.tables.records.CharacterRecord;
import org.pokemmo.db.jooq.tables.records.OwnedItemRecord;
import org.pokemmo.db.jooq.tables.records.PokemonRecord;
import org.pokemmo.gameserver.game.item.ItemData;
import org.pokemmo.gameserver.game.item.ItemManager;
import org.pokemmo.gameserver.game.container.PokemonContainerType;
import org.pokemmo.gameserver.util.SnowflakeIdGenerator;
import org.pokemmo.gameserver.services.mail.MailService.ItemAttachment;
import org.pokemmo.gameserver.services.mail.MailService.MailAttachment;
import org.pokemmo.gameserver.services.mail.MailService.ItemMailAttachment;
import org.pokemmo.gameserver.services.mail.MailService.PokemonMailAttachment;
import org.pokemmo.gameserver.services.mail.MailService.MoneyMailAttachment;
import org.pokemmo.gameserver.services.mail.MailService.SendMailResult;
import org.pokemmo.gameserver.services.mail.MailService.MailClaimResult;
import org.pokemmo.gameserver.services.mail.MailService.MailCounts;
import org.pokemmo.gameserver.services.mail.MailService.MailListEntry;
import org.pokemmo.gameserver.services.mail.MailService.MailListPage;
import org.pokemmo.gameserver.services.mail.MailService.MailDetail;
import org.pokemmo.gameserver.services.mail.MailService.MailDeleteResult;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.pokemmo.db.jooq.Tables.CHARACTER;
import static org.pokemmo.db.jooq.Tables.OWNED_ITEM;
import static org.pokemmo.db.jooq.Tables.POKEMON;

import static org.pokemmo.gameserver.services.mail.MailServiceStore.*;

/** Mail Query Service database operation boundary. */
@Slf4j
final class MailQueryService {
    private final Database database;
    private volatile Boolean claimStateColumnsAvailable;
    MailQueryService(Database database) {
        this.database = database;
    }

    public MailCounts getMailCounts(long characterId) {
        if (characterId <= 0) {
            return MailCounts.empty();
        }
        try {
            DSLContext context = database.ctx();
            int received = count(context, MAIL_RECIPIENT_ID.eq(characterId));
            int unread = count(context, MAIL_RECIPIENT_ID.eq(characterId).and(MAIL_IS_READ.eq(false)));
            int sent = count(context, MAIL_SENDER_ID.eq(characterId));
            return new MailCounts(toShortCount(received), toShortCount(unread), toShortCount(sent));
        } catch (RuntimeException exception) {
            log.error("查询邮件计数失败: characterId={}", characterId, exception);
            return MailCounts.empty();
        }
    }

    public MailDetail getMailDetail(long characterId, long mailId) {
        if (characterId <= 0 || mailId <= 0) {
            return null;
        }
        try {
            return database.ctx().transactionResult(configuration -> {
                DSLContext transaction = DSL.using(configuration);
                var senderCharacter = CHARACTER.as("mail_detail_sender_character");
                var recipientCharacter = CHARACTER.as("mail_detail_recipient_character");
                var senderNameField = senderCharacter.NAME;
                var recipientNameField = recipientCharacter.NAME;
                Record row = transaction.select(
                                MAIL_ID, MAIL_SENDER_ID, MAIL_RECIPIENT_ID,
                                MAIL_IS_READ, MAIL_CREATED_AT, MAIL_TITLE, MAIL_BODY,
                                senderNameField, recipientNameField)
                        .from(MAIL_MESSAGE)
                        .join(senderCharacter).on(MAIL_SENDER_ID.eq(senderCharacter.ID))
                        .join(recipientCharacter).on(MAIL_RECIPIENT_ID.eq(recipientCharacter.ID))
                        .where(MAIL_ID.eq(mailId))
                        .and(DSL.or(MAIL_SENDER_ID.eq(characterId),
                                MAIL_RECIPIENT_ID.eq(characterId)))
                        .forUpdate()
                        .fetchOne();
                if (row == null) {
                    return null;
                }

                long senderId = valueOrZero(row.get(MAIL_SENDER_ID));
                long recipientId = valueOrZero(row.get(MAIL_RECIPIENT_ID));
                boolean sentMailbox = senderId == characterId && recipientId != characterId;
                boolean unread = !Boolean.TRUE.equals(row.get(MAIL_IS_READ));
                if (recipientId == characterId && unread) {
                    int updated = transaction.update(MAIL_MESSAGE)
                            .set(MAIL_IS_READ, true)
                            .where(MAIL_ID.eq(mailId))
                            .and(MAIL_RECIPIENT_ID.eq(characterId))
                            .execute();
                    if (updated != 1) {
                        throw new IllegalStateException("邮件已读状态更新失败");
                    }
                    unread = false;
                }

                // Sent-mail details do not expose attachment entries on the
                // client. Avoid touching attachment tables for that direction;
                // this also keeps sent mail readable on pre-claim schemas.
                List<MailAttachment> attachments = sentMailbox
                        ? List.of()
                        : loadAttachments(transaction, mailId, hasClaimStateColumns());

                return new MailDetail(
                        valueOrZero(row.get(MAIL_ID)),
                        senderId,
                        recipientId,
                        PLAYER_MAIL_TAG_TYPE,
                        valueOrEmpty(row.get(senderNameField)),
                        valueOrEmpty(row.get(recipientNameField)),
                        toEpochSeconds(row.get(MAIL_CREATED_AT)),
                        valueOrEmpty(row.get(MAIL_TITLE)),
                        valueOrEmpty(row.get(MAIL_BODY)),
                        unread && recipientId == characterId,
                        sentMailbox,
                        attachments);
            });
        } catch (RuntimeException exception) {
            log.error("查询邮件详情失败: characterId={}, mailId={}", characterId, mailId,
                    exception);
            return null;
        }
    }

    public boolean markMailRead(long characterId, long mailId) {
        if (characterId <= 0 || mailId <= 0) {
            return false;
        }
        try {
            return database.ctx().update(MAIL_MESSAGE)
                    .set(MAIL_IS_READ, true)
                    .where(MAIL_ID.eq(mailId))
                    .and(MAIL_RECIPIENT_ID.eq(characterId))
                    .execute() == 1;
        } catch (RuntimeException exception) {
            log.error("标记邮件已读失败: characterId={}, mailId={}", characterId, mailId,
                    exception);
            return false;
        }
    }

    public MailListPage getMailList(long characterId, boolean sent, int pageIndex) {
        if (characterId <= 0 || pageIndex < 0) {
            return MailListPage.empty();
        }
        try {
            DSLContext context = database.ctx();
            Field<Long> ownerField = sent ? MAIL_SENDER_ID : MAIL_RECIPIENT_ID;
            Condition ownerCondition = ownerField.eq(characterId);
            Integer total = context.selectCount()
                    .from(MAIL_MESSAGE)
                    .where(ownerCondition)
                    .fetchOne(0, Integer.class);
            int totalCount = total == null ? 0 : total;
            if (totalCount == 0) {
                return MailListPage.empty();
            }

            long offsetLong = (long) pageIndex * PAGE_SIZE;
            if (offsetLong > Integer.MAX_VALUE) {
                return new MailListPage(List.of(), totalCount);
            }
            var senderCharacter = CHARACTER.as("mail_sender_character");
            var recipientCharacter = CHARACTER.as("mail_recipient_character");
            var senderNameField = senderCharacter.NAME;
            var recipientNameField = recipientCharacter.NAME;
            var rows = context.select(MAIL_ID, MAIL_SENDER_ID, MAIL_RECIPIENT_ID,
                            MAIL_IS_READ, MAIL_CREATED_AT, MAIL_TITLE,
                            senderNameField, recipientNameField)
                    .from(MAIL_MESSAGE)
                    .join(senderCharacter).on(MAIL_SENDER_ID.eq(senderCharacter.ID))
                    .join(recipientCharacter).on(MAIL_RECIPIENT_ID.eq(recipientCharacter.ID))
                    .where(ownerCondition)
                    .orderBy(MAIL_CREATED_AT.desc(), MAIL_ID.desc())
                    .limit(PAGE_SIZE)
                    .offset((int) offsetLong)
                    .fetch();
            List<MailListEntry> entries = rows.map(row -> {
                Long mailId = row.get(MAIL_ID);
                Timestamp createdAt = row.get(MAIL_CREATED_AT);
                int epochSeconds = toEpochSeconds(createdAt);
                return new MailListEntry(
                        mailId == null ? 0L : mailId,
                        valueOrZero(row.get(MAIL_SENDER_ID)),
                        valueOrZero(row.get(MAIL_RECIPIENT_ID)),
                        PLAYER_MAIL_TAG_TYPE,
                        valueOrEmpty(row.get(senderNameField)),
                        valueOrEmpty(row.get(recipientNameField)),
                        epochSeconds,
                        valueOrEmpty(row.get(MAIL_TITLE)),
                        !sent && !Boolean.TRUE.equals(row.get(MAIL_IS_READ)));
            });
            return new MailListPage(entries, totalCount);
        } catch (RuntimeException exception) {
            log.error("查询邮件列表失败: characterId={}, sent={}, page={}",
                    characterId, sent, pageIndex, exception);
            return MailListPage.empty();
        }
    }

    private int count(DSLContext context, Condition condition) {
        return count(context, condition, MAIL_MESSAGE);
    }

    private int count(DSLContext context, Condition condition, Table<Record> table) {
        Integer value = context.selectCount().from(table).where(condition)
                .fetchOne(0, Integer.class);
        return value == null ? 0 : value;
    }

    private List<MailAttachment> loadAttachments(DSLContext transaction, long mailId,
                                                 boolean includeClaimState) {
        List<MailAttachment> attachments = new ArrayList<>();

        Map<Long, Short> itemAmounts = new LinkedHashMap<>();
        Map<Long, Boolean> itemClaimed = new LinkedHashMap<>();
        if (includeClaimState) {
            transaction.select(MAIL_ITEM_OBJECT_ID, MAIL_ITEM_AMOUNT, MAIL_ITEM_CLAIMED)
                    .from(MAIL_ITEM_ATTACHMENT)
                    .where(MAIL_ITEM_MAIL_ID.eq(mailId))
                    .orderBy(MAIL_ITEM_OBJECT_ID.asc())
                    .fetch().forEach(row -> {
                        Long itemObjectId = row.get(MAIL_ITEM_OBJECT_ID);
                        Short amount = row.get(MAIL_ITEM_AMOUNT);
                        if (itemObjectId != null && itemObjectId > 0 && amount != null && amount > 0) {
                            itemAmounts.put(itemObjectId, amount);
                            itemClaimed.put(itemObjectId,
                                    Boolean.TRUE.equals(row.get(MAIL_ITEM_CLAIMED)));
                        }
                    });
        } else {
            transaction.select(MAIL_ITEM_OBJECT_ID, MAIL_ITEM_AMOUNT)
                    .from(MAIL_ITEM_ATTACHMENT)
                    .where(MAIL_ITEM_MAIL_ID.eq(mailId))
                    .orderBy(MAIL_ITEM_OBJECT_ID.asc())
                    .fetch().forEach(row -> {
                        Long itemObjectId = row.get(MAIL_ITEM_OBJECT_ID);
                        Short amount = row.get(MAIL_ITEM_AMOUNT);
                        if (itemObjectId != null && itemObjectId > 0 && amount != null && amount > 0) {
                            itemAmounts.put(itemObjectId, amount);
                            itemClaimed.put(itemObjectId, false);
                        }
                    });
        }
        if (!itemAmounts.isEmpty()) {
            Map<Long, OwnedItemRecord> itemsById = new LinkedHashMap<>();
            transaction.selectFrom(OWNED_ITEM)
                    .where(OWNED_ITEM.ITEM_ID.in(itemAmounts.keySet()))
                    .fetch().forEach(item -> itemsById.put(item.getItemId(), item));
            itemAmounts.forEach((itemObjectId, amount) -> {
                OwnedItemRecord item = itemsById.get(itemObjectId);
                if (item == null) {
                    log.warn("邮件道具附件对应的 owned_item 不存在: mailId={}, itemObjectId={}",
                            mailId, itemObjectId);
                }
                // Keep the attachment slot even when the underlying asset is
                // missing. The detail encoder emits an exists=0 marker so the
                // client can continue parsing later attachments.
                attachments.add(new ItemMailAttachment(item, amount,
                        Boolean.TRUE.equals(itemClaimed.get(itemObjectId))));
            });
        }

        List<Long> pokemonIds = new ArrayList<>();
        Map<Long, Boolean> pokemonClaimed = new LinkedHashMap<>();
        if (includeClaimState) {
            transaction.select(MAIL_POKEMON_OBJECT_ID, MAIL_POKEMON_CLAIMED)
                    .from(MAIL_POKEMON_ATTACHMENT)
                    .where(MAIL_POKEMON_MAIL_ID.eq(mailId))
                    .orderBy(MAIL_POKEMON_OBJECT_ID.asc())
                    .fetch().forEach(row -> {
                        Long pokemonId = row.get(MAIL_POKEMON_OBJECT_ID);
                        if (pokemonId != null && pokemonId > 0) {
                            pokemonIds.add(pokemonId);
                            pokemonClaimed.put(pokemonId,
                                    Boolean.TRUE.equals(row.get(MAIL_POKEMON_CLAIMED)));
                        }
                    });
        } else {
            transaction.select(MAIL_POKEMON_OBJECT_ID)
                    .from(MAIL_POKEMON_ATTACHMENT)
                    .where(MAIL_POKEMON_MAIL_ID.eq(mailId))
                    .orderBy(MAIL_POKEMON_OBJECT_ID.asc())
                    .fetch().forEach(row -> {
                        Long pokemonId = row.get(MAIL_POKEMON_OBJECT_ID);
                        if (pokemonId != null && pokemonId > 0) {
                            pokemonIds.add(pokemonId);
                            pokemonClaimed.put(pokemonId, false);
                        }
                    });
        }
        if (!pokemonIds.isEmpty()) {
            Map<Long, PokemonRecord> pokemonById = new LinkedHashMap<>();
            transaction.selectFrom(POKEMON)
                    .where(POKEMON.ID.in(pokemonIds))
                    .fetch().forEach(pokemon -> pokemonById.put(pokemon.getId(), pokemon));
            for (Long pokemonId : pokemonIds) {
                PokemonRecord pokemon = pokemonById.get(pokemonId);
                if (pokemon == null) {
                    log.warn("邮件精灵附件对应的 pokemon 不存在: mailId={}, pokemonObjectId={}",
                            mailId, pokemonId);
                }
                // Preserve the attachment count and slot ordering on corrupt
                // rows; SendEmailDetailPacket writes exists=0 for this case.
                attachments.add(new PokemonMailAttachment(pokemon,
                        Boolean.TRUE.equals(pokemonClaimed.get(pokemonId))));
            }
        }

        Record money;
        if (includeClaimState) {
            money = transaction.select(MAIL_MONEY_AMOUNT, MAIL_MONEY_CLAIMED)
                    .from(MAIL_MONEY_ATTACHMENT)
                    .where(MAIL_MONEY_MAIL_ID.eq(mailId))
                    .fetchOne();
        } else {
            money = transaction.select(MAIL_MONEY_AMOUNT)
                    .from(MAIL_MONEY_ATTACHMENT)
                    .where(MAIL_MONEY_MAIL_ID.eq(mailId))
                    .fetchOne();
        }
        Integer moneyAmount = money == null ? null : money.get(MAIL_MONEY_AMOUNT);
        if (moneyAmount != null && moneyAmount > 0) {
            attachments.add(new MoneyMailAttachment(moneyAmount,
                    includeClaimState && Boolean.TRUE.equals(money.get(MAIL_MONEY_CLAIMED))));
        }
        return List.copyOf(attachments);
    }

    private boolean hasClaimStateColumns() {
        Boolean cached = claimStateColumnsAvailable;
        if (cached != null) {
            return cached;
        }
        try {
            Object available = database.ctx().fetchValue(
                    "SELECT COUNT(*) = 3 FROM information_schema.columns "
                            + "WHERE table_schema = current_schema() "
                            + "AND table_name IN "
                            + "('mail_item_attachment','mail_pokemon_attachment',"
                            + "'mail_money_attachment') AND column_name = 'claimed'");
            boolean result = Boolean.TRUE.equals(available);
            claimStateColumnsAvailable = result;
            if (!result) {
                log.warn("邮件附件表缺少 claimed 列；详情将按未领取状态读取，领取功能不可用");
            }
            return result;
        } catch (RuntimeException exception) {
            log.warn("检查邮件 claimed 列失败，按旧表结构读取详情", exception);
            claimStateColumnsAvailable = false;
            return false;
        }
    }

    private static long valueOrZero(Long value) {
        return value == null ? 0L : value;
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private static int toEpochSeconds(Timestamp value) {
        if (value == null) {
            return 0;
        }
        long epochSeconds = value.toLocalDateTime().toEpochSecond(ZoneOffset.UTC);
        if (epochSeconds <= Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }
        if (epochSeconds >= Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) epochSeconds;
    }

    private static short toShortCount(int count) {
        return (short) Math.min(Short.MAX_VALUE, Math.max(0, count));
    }
}
