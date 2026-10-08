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

/** Mail Send Service database operation boundary. */
@Slf4j
final class MailSendService {
    private final Database database;
    MailSendService(Database database) {
        this.database = database;
    }

    public SendMailResult sendMail(long senderId, String recipientName, String title, String body,
                                   List<ItemAttachment> itemAttachments,
                                   List<Long> pokemonObjectIds, int moneyAmount,
                                   SnowflakeIdGenerator idGenerator) {
        if (senderId <= 0 || idGenerator == null || !isValidRecipient(recipientName)
                || !isValidTitle(title) || !isValidBody(body) || moneyAmount < 0
                || itemAttachments == null || pokemonObjectIds == null
                || itemAttachments.size() + pokemonObjectIds.size()
                + (moneyAmount > 0 ? 1 : 0) > 255) {
            return SendMailResult.rejected();
        }

        Set<Long> itemIds = new HashSet<>();
        for (ItemAttachment attachment : itemAttachments) {
            if (attachment == null || attachment.itemObjectId() <= 0 || attachment.amount() <= 0
                    || !itemIds.add(attachment.itemObjectId())) {
                return SendMailResult.rejected();
            }
        }
        Set<Long> pokemonIds = new HashSet<>();
        for (Long pokemonId : pokemonObjectIds) {
            if (pokemonId == null || pokemonId <= 0 || !pokemonIds.add(pokemonId)) {
                return SendMailResult.rejected();
            }
        }

        try {
            return database.ctx().transactionResult(configuration -> {
                DSLContext transaction = DSL.using(configuration);
                List<CharacterRecord> recipients = transaction.selectFrom(CHARACTER)
                        .where(DSL.lower(CHARACTER.NAME)
                                .eq(recipientName.toLowerCase(Locale.ROOT)))
                        .fetch();
                if (recipients.size() != 1) {
                    log.warn("邮件收件人不存在或名称不唯一: recipient={}", recipientName);
                    return SendMailResult.rejected();
                }

                long recipientId = recipients.get(0).getId();
                List<CharacterRecord> lockedCharacters = transaction.selectFrom(CHARACTER)
                        .where(CHARACTER.ID.in(senderId, recipientId))
                        .orderBy(CHARACTER.ID.asc())
                        .forUpdate()
                        .fetch();
                CharacterRecord sender = findCharacter(lockedCharacters, senderId);
                CharacterRecord recipient = findCharacter(lockedCharacters, recipientId);
                if (sender == null || recipient == null || sender.getMoney() == null
                        || sender.getMoney() < 0 || sender.getMoney() < moneyAmount) {
                    return SendMailResult.rejected();
                }

                List<OwnedItemRecord> ownedItems = itemIds.isEmpty()
                        ? List.of()
                        : transaction.selectFrom(OWNED_ITEM)
                        .where(OWNED_ITEM.ITEM_ID.in(itemIds))
                        .and(OWNED_ITEM.OWNER_ID.eq(senderId))
                        .and(OWNED_ITEM.INVENTORY_ID.eq((short) MAIN_INVENTORY_ID))
                        .forUpdate()
                        .fetch();
                if (ownedItems.size() != itemIds.size()) {
                    return SendMailResult.rejected();
                }
                for (OwnedItemRecord ownedItem : ownedItems) {
                    ItemData itemData = ItemManager.getItemData(ownedItem.getItemIndexId());
                    if (itemData == null || itemData.isBindAccount()
                            || !ItemManager.isTradeableForExchange(ownedItem.getItemIndexId())) {
                        return SendMailResult.rejected();
                    }
                    log.info("邮件道具解析: itemObjectId={}, itemIndexId={}, ownedAmount={}",
                            ownedItem.getItemId(), ownedItem.getItemIndexId(),
                            ownedItem.getItemAmount());
                }
                for (ItemAttachment requested : itemAttachments) {
                    OwnedItemRecord ownedItem = ownedItems.stream()
                            .filter(item -> item.getItemId() == requested.itemObjectId())
                            .findFirst()
                            .orElse(null);
                    if (ownedItem == null || ownedItem.getItemAmount() == null
                            || ownedItem.getItemAmount() < requested.amount()) {
                        return SendMailResult.rejected();
                    }
                }

                List<PokemonRecord> ownedPokemon = pokemonIds.isEmpty()
                        ? List.of()
                        : transaction.selectFrom(POKEMON)
                        .where(POKEMON.ID.in(pokemonIds))
                        .and(POKEMON.TRAINER_ID.eq(senderId))
                        .and(POKEMON.CONTAINER_ID.in(PC_CONTAINER_ID, PARTY_CONTAINER_ID))
                        .forUpdate()
                        .fetch();
                if (ownedPokemon.size() != pokemonIds.size()) {
                    return SendMailResult.rejected();
                }
                if (!pokemonIds.isEmpty()) {
                    int partyCount = transaction.fetchCount(transaction.selectFrom(POKEMON)
                            .where(POKEMON.TRAINER_ID.eq(senderId))
                            .and(POKEMON.CONTAINER_ID.eq(PARTY_CONTAINER_ID)));
                    long sentPartyCount = ownedPokemon.stream()
                            .filter(pokemon -> pokemon.getContainerId() == PARTY_CONTAINER_ID)
                            .count();
                    if (partyCount > 0 && partyCount == sentPartyCount) {
                        log.warn("拒绝发送会清空 PARTY 的邮件: senderId={}", senderId);
                        return SendMailResult.rejected();
                    }
                }

                LocalDateTime createdAt = LocalDateTime.now(ZoneOffset.UTC);
                Record insertedMail = transaction.insertInto(MAIL_MESSAGE)
                        .set(MAIL_SENDER_ID, senderId)
                        .set(MAIL_RECIPIENT_ID, recipientId)
                        .set(MAIL_TITLE, title)
                        .set(MAIL_BODY, body)
                        .set(MAIL_IS_READ, false)
                        .set(MAIL_CREATED_AT, Timestamp.valueOf(createdAt))
                        .returningResult(MAIL_ID)
                        .fetchOne();
                Long mailId = insertedMail == null ? null : insertedMail.get(MAIL_ID);
                if (mailId == null || mailId <= 0) {
                    throw new IllegalStateException("邮件主表写入失败");
                }

                for (ItemAttachment requested : itemAttachments) {
                    OwnedItemRecord ownedItem = ownedItems.stream()
                            .filter(item -> item.getItemId() == requested.itemObjectId())
                            .findFirst()
                            .orElseThrow(() -> new IllegalStateException("邮件道具记录丢失"));
                    if (ownedItem.getItemAmount() == null
                            || ownedItem.getItemAmount() < requested.amount()) {
                        throw new IllegalStateException("邮件道具数量不足");
                    }

                    long transferredItemId = requested.itemObjectId();
                    if (ownedItem.getItemAmount() == requested.amount()) {
                        int updated = transaction.update(OWNED_ITEM)
                                .set(OWNED_ITEM.OWNER_ID, recipientId)
                                .set(OWNED_ITEM.INVENTORY_ID, (short) MAIL_INVENTORY_ID)
                                .where(OWNED_ITEM.ITEM_ID.eq(requested.itemObjectId()))
                                .and(OWNED_ITEM.OWNER_ID.eq(senderId))
                                .and(OWNED_ITEM.INVENTORY_ID.eq((short) MAIN_INVENTORY_ID))
                                .execute();
                        if (updated != 1) {
                            throw new IllegalStateException("邮件道具转移失败");
                        }
                    } else {
                        transferredItemId = idGenerator.nextId();
                        if (transferredItemId <= 0 || transaction.fetchExists(
                                transaction.selectOne().from(OWNED_ITEM)
                                        .where(OWNED_ITEM.ITEM_ID.eq(transferredItemId)))) {
                            throw new IllegalStateException("邮件道具 Object ID 冲突");
                        }
                        short remaining = (short) (ownedItem.getItemAmount() - requested.amount());
                        int updated = transaction.update(OWNED_ITEM)
                                .set(OWNED_ITEM.ITEM_AMOUNT, remaining)
                                .where(OWNED_ITEM.ITEM_ID.eq(requested.itemObjectId()))
                                .and(OWNED_ITEM.OWNER_ID.eq(senderId))
                                .and(OWNED_ITEM.INVENTORY_ID.eq((short) MAIN_INVENTORY_ID))
                                .execute();
                        if (updated != 1) {
                            throw new IllegalStateException("邮件道具数量扣除失败");
                        }
                        OwnedItemRecord transferred = new OwnedItemRecord();
                        transferred.setItemId(transferredItemId);
                        transferred.setOwnerId(recipientId);
                        transferred.setItemIndexId(ownedItem.getItemIndexId());
                        transferred.setItemAmount(requested.amount());
                        transferred.setInventoryId((short) MAIL_INVENTORY_ID);
                        transferred.setColorId(ownedItem.getColorId());
                        transferred.setItemRegionIndexId(ownedItem.getItemRegionIndexId());
                        transferred.setPvpRewardLevel(ownedItem.getPvpRewardLevel());
                        transferred.setPvpRewardSeason(ownedItem.getPvpRewardSeason());
                        transferred.setPvpRewardTime(ownedItem.getPvpRewardTime());
                        if (transaction.insertInto(OWNED_ITEM).set(transferred).execute() != 1) {
                            throw new IllegalStateException("邮件道具记录创建失败");
                        }
                    }

                    if (transaction.insertInto(MAIL_ITEM_ATTACHMENT)
                            .set(MAIL_ITEM_MAIL_ID, mailId)
                            .set(MAIL_ITEM_OBJECT_ID, transferredItemId)
                            .set(MAIL_ITEM_AMOUNT, requested.amount())
                            .execute() != 1) {
                        throw new IllegalStateException("邮件道具附件写入失败");
                    }
                }

                for (PokemonRecord pokemon : ownedPokemon) {
                    if (transaction.update(POKEMON)
                            .set(POKEMON.TRAINER_ID, recipientId)
                            .set(POKEMON.CONTAINER_ID, MAIL_CONTAINER_ID)
                            .set(POKEMON.CONTAINER_POSITION, (short) 0)
                            .where(POKEMON.ID.eq(pokemon.getId()))
                            .and(POKEMON.TRAINER_ID.eq(senderId))
                            .and(POKEMON.CONTAINER_ID.in(PC_CONTAINER_ID, PARTY_CONTAINER_ID))
                            .execute() != 1) {
                        throw new IllegalStateException("邮件精灵转移失败");
                    }
                    if (transaction.insertInto(MAIL_POKEMON_ATTACHMENT)
                            .set(MAIL_POKEMON_MAIL_ID, mailId)
                            .set(MAIL_POKEMON_OBJECT_ID, pokemon.getId())
                            .execute() != 1) {
                        throw new IllegalStateException("邮件精灵附件写入失败");
                    }
                }

                int remainingMoney = sender.getMoney() - moneyAmount;
                if (moneyAmount > 0) {
                    if (transaction.update(CHARACTER)
                            .set(CHARACTER.MONEY, remainingMoney)
                            .where(CHARACTER.ID.eq(senderId))
                            .and(CHARACTER.MONEY.ge(moneyAmount))
                            .execute() != 1) {
                        throw new IllegalStateException("邮件金钱扣除失败");
                    }
                    if (transaction.insertInto(MAIL_MONEY_ATTACHMENT)
                            .set(MAIL_MONEY_MAIL_ID, mailId)
                            .set(MAIL_MONEY_AMOUNT, moneyAmount)
                            .execute() != 1) {
                        throw new IllegalStateException("邮件金钱附件写入失败");
                    }
                }
                return new SendMailResult(true, mailId, remainingMoney, recipientId);
            });
        } catch (RuntimeException exception) {
            log.error("邮件发送事务失败: senderId={}, recipient={}, itemCount={}, pokemonCount={}, money={}",
                    senderId, recipientName, itemAttachments.size(), pokemonObjectIds.size(),
                    moneyAmount, exception);
            return SendMailResult.rejected();
        }
    }

    private static CharacterRecord findCharacter(List<CharacterRecord> records, long id) {
        return records.stream().filter(record -> record.getId() == id).findFirst().orElse(null);
    }

    private static boolean isValidRecipient(String value) {
        return value != null && !value.isBlank() && value.indexOf('\0') < 0
                && value.length() <= MAX_RECIPIENT_LENGTH;
    }

    private static boolean isValidTitle(String value) {
        return value != null && value.indexOf('\0') < 0 && value.length() <= MAX_TITLE_LENGTH;
    }

    private static boolean isValidBody(String value) {
        return value != null && value.indexOf('\0') < 0 && value.length() <= MAX_BODY_LENGTH;
    }
}
