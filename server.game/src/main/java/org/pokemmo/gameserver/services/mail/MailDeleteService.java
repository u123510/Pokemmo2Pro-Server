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

/** Mail Delete Service database operation boundary. */
@Slf4j
final class MailDeleteService {
    private final Database database;
    private volatile Boolean claimStateColumnsAvailable;
    MailDeleteService(Database database) {
        this.database = database;
    }

    public MailDeleteResult deleteMail(long characterId, long mailId) {
        if (characterId <= 0 || mailId <= 0) {
            return MailDeleteResult.rejected();
        }
        if (!hasClaimStateColumns()) {
            log.error("无法删除邮件：数据库缺少 claimed 列，请先执行邮件表迁移");
            return MailDeleteResult.rejected();
        }
        try {
            return database.ctx().transactionResult(configuration -> {
                DSLContext transaction = DSL.using(configuration);
                Record mail = transaction.select(MAIL_SENDER_ID, MAIL_RECIPIENT_ID)
                        .from(MAIL_MESSAGE)
                        .where(MAIL_ID.eq(mailId))
                        .and(DSL.or(MAIL_SENDER_ID.eq(characterId),
                                MAIL_RECIPIENT_ID.eq(characterId)))
                        .forUpdate()
                        .fetchOne();
                if (mail == null) {
                    return MailDeleteResult.rejected();
                }

                long senderId = valueOrZero(mail.get(MAIL_SENDER_ID));
                long recipientId = valueOrZero(mail.get(MAIL_RECIPIENT_ID));
                boolean sentMailbox = senderId == characterId && recipientId != characterId;
                boolean hasUnclaimedAttachments = transaction.fetchExists(
                                transaction.selectOne().from(MAIL_ITEM_ATTACHMENT)
                                        .where(MAIL_ITEM_MAIL_ID.eq(mailId))
                                        .and(MAIL_ITEM_CLAIMED.eq(false)))
                        || transaction.fetchExists(
                                transaction.selectOne().from(MAIL_POKEMON_ATTACHMENT)
                                        .where(MAIL_POKEMON_MAIL_ID.eq(mailId))
                                        .and(MAIL_POKEMON_CLAIMED.eq(false)))
                        || transaction.fetchExists(
                                transaction.selectOne().from(MAIL_MONEY_ATTACHMENT)
                                        .where(MAIL_MONEY_MAIL_ID.eq(mailId))
                                        .and(MAIL_MONEY_CLAIMED.eq(false)));
                if (hasUnclaimedAttachments) {
                    log.warn("拒绝删除仍含未领取附件的邮件: characterId={}, mailId={}",
                            characterId, mailId);
                    return MailDeleteResult.blocked(sentMailbox);
                }

                // A merged item keeps its original attachment Object ID in the
                // void inventory so the claimed detail can still be encoded.
                // It is no longer a player asset and can be removed with mail.
                List<Long> archivedItemIds = transaction
                        .select(MAIL_ITEM_OBJECT_ID)
                        .from(MAIL_ITEM_ATTACHMENT)
                        .where(MAIL_ITEM_MAIL_ID.eq(mailId))
                        .and(MAIL_ITEM_CLAIMED.eq(true))
                        .fetch(MAIL_ITEM_OBJECT_ID);
                if (!archivedItemIds.isEmpty()) {
                    transaction.deleteFrom(OWNED_ITEM)
                            .where(OWNED_ITEM.ITEM_ID.in(archivedItemIds))
                            .and(OWNED_ITEM.OWNER_ID.eq(recipientId))
                            .and(OWNED_ITEM.INVENTORY_ID.eq((short) 0))
                            .execute();
                }

                int deleted = transaction.deleteFrom(MAIL_MESSAGE)
                        .where(MAIL_ID.eq(mailId))
                        .and(DSL.or(MAIL_SENDER_ID.eq(characterId),
                                MAIL_RECIPIENT_ID.eq(characterId)))
                        .execute();
                return deleted == 1
                        ? new MailDeleteResult(true, sentMailbox, false)
                        : MailDeleteResult.rejected();
            });
        } catch (RuntimeException exception) {
            log.error("删除邮件失败: characterId={}, mailId={}", characterId, mailId,
                    exception);
            return MailDeleteResult.rejected();
        }
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
}
