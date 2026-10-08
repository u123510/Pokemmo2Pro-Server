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

/** Mail Claim Service database operation boundary. */
@Slf4j
final class MailClaimService {
    private final Database database;
    private volatile Boolean claimStateColumnsAvailable;
    MailClaimService(Database database) {
        this.database = database;
    }

    public MailClaimResult claimMail(long characterId, long mailId) {
        return claimMailAttachmentInternal(characterId, mailId, -1, -1);
    }

    public MailClaimResult claimMailAttachment(long characterId, long mailId,
                                               int slot, byte claimMode) {
        int mode = Byte.toUnsignedInt(claimMode);
        if (slot < 0 || slot > 255 || mode > 2) {
            return MailClaimResult.rejected();
        }
        return claimMailAttachmentInternal(characterId, mailId, slot, mode);
    }

    private MailClaimResult claimMailAttachmentInternal(long characterId, long mailId,
                                                         int targetSlot, int claimMode) {
        if (characterId <= 0 || mailId <= 0) {
            return MailClaimResult.rejected();
        }
        if (!hasClaimStateColumns()) {
            log.error("无法领取邮件附件：数据库缺少 claimed 列，请先执行邮件表迁移");
            return MailClaimResult.rejected();
        }
        try {
            return database.ctx().transactionResult(configuration -> {
                DSLContext transaction = DSL.using(configuration);
                Record mail = transaction.select(MAIL_SENDER_ID, MAIL_RECIPIENT_ID)
                        .from(MAIL_MESSAGE)
                        .where(MAIL_ID.eq(mailId))
                        .and(MAIL_RECIPIENT_ID.eq(characterId))
                        .forUpdate()
                        .fetchOne();
                if (mail == null) {
                    return MailClaimResult.rejected();
                }

                CharacterRecord recipient = transaction.selectFrom(CHARACTER)
                        .where(CHARACTER.ID.eq(characterId))
                        .forUpdate()
                        .fetchOne();
                if (recipient == null || recipient.getMoney() == null || recipient.getMoney() < 0) {
                    return MailClaimResult.rejected();
                }

                var itemRows = transaction.select(MAIL_ITEM_OBJECT_ID, MAIL_ITEM_AMOUNT,
                                MAIL_ITEM_CLAIMED)
                        .from(MAIL_ITEM_ATTACHMENT)
                        .where(MAIL_ITEM_MAIL_ID.eq(mailId))
                        .orderBy(MAIL_ITEM_OBJECT_ID.asc())
                        .forUpdate()
                        .fetch();
                var pokemonRows = transaction.select(MAIL_POKEMON_OBJECT_ID,
                                MAIL_POKEMON_CLAIMED)
                        .from(MAIL_POKEMON_ATTACHMENT)
                        .where(MAIL_POKEMON_MAIL_ID.eq(mailId))
                        .orderBy(MAIL_POKEMON_OBJECT_ID.asc())
                        .forUpdate()
                        .fetch();
                Record moneyRow = transaction.select(MAIL_MONEY_AMOUNT, MAIL_MONEY_CLAIMED)
                        .from(MAIL_MONEY_ATTACHMENT)
                        .where(MAIL_MONEY_MAIL_ID.eq(mailId))
                        .forUpdate()
                        .fetchOne();

                List<Record> unclaimedItems = new ArrayList<>();
                int clientSlot = 0;
                for (Record row : itemRows) {
                    Long itemId = row.get(MAIL_ITEM_OBJECT_ID);
                    Short amount = row.get(MAIL_ITEM_AMOUNT);
                    if (itemId == null || itemId <= 0 || amount == null || amount <= 0) {
                        continue;
                    }
                    if (!Boolean.TRUE.equals(row.get(MAIL_ITEM_CLAIMED))
                            && shouldClaimSlot(targetSlot, clientSlot)) {
                        unclaimedItems.add(row);
                    }
                    clientSlot++;
                }
                List<Record> unclaimedPokemon = new ArrayList<>();
                for (Record row : pokemonRows) {
                    Long pokemonId = row.get(MAIL_POKEMON_OBJECT_ID);
                    if (pokemonId == null || pokemonId <= 0) {
                        continue;
                    }
                    if (!Boolean.TRUE.equals(row.get(MAIL_POKEMON_CLAIMED))
                            && shouldClaimSlot(targetSlot, clientSlot)) {
                        unclaimedPokemon.add(row);
                    }
                    clientSlot++;
                }
                int moneySlot = clientSlot;
                boolean unclaimedMoney = moneyRow != null
                        && moneyRow.get(MAIL_MONEY_AMOUNT) != null
                        && moneyRow.get(MAIL_MONEY_AMOUNT) > 0
                        && !Boolean.TRUE.equals(moneyRow.get(MAIL_MONEY_CLAIMED))
                        && shouldClaimSlot(targetSlot, moneySlot);
                if (unclaimedItems.isEmpty() && unclaimedPokemon.isEmpty() && !unclaimedMoney) {
                    log.debug("拒绝领取邮件附件: characterId={}, mailId={}, slot={}, mode={}",
                            characterId, mailId, targetSlot, claimMode);
                    return MailClaimResult.rejected();
                }

                Map<Long, OwnedItemRecord> itemById = new LinkedHashMap<>();
                if (!unclaimedItems.isEmpty()) {
                    Set<Long> itemIds = new HashSet<>();
                    for (Record row : unclaimedItems) {
                        Long itemId = row.get(MAIL_ITEM_OBJECT_ID);
                        Short amount = row.get(MAIL_ITEM_AMOUNT);
                        if (itemId == null || itemId <= 0 || amount == null || amount <= 0
                                || !itemIds.add(itemId)) {
                            throw new IllegalStateException("邮件道具附件数据非法");
                        }
                    }
                    transaction.selectFrom(OWNED_ITEM)
                            .where(OWNED_ITEM.ITEM_ID.in(itemIds))
                            .and(OWNED_ITEM.OWNER_ID.eq(characterId))
                            .and(OWNED_ITEM.INVENTORY_ID.eq((short) MAIL_INVENTORY_ID))
                            .forUpdate()
                            .fetch().forEach(item -> itemById.put(item.getItemId(), item));
                    if (itemById.size() != itemIds.size()) {
                        throw new IllegalStateException("邮件道具附件资产不存在或归属错误");
                    }
                }

                Map<Long, PokemonRecord> pokemonById = new LinkedHashMap<>();
                if (!unclaimedPokemon.isEmpty()) {
                    Set<Long> pokemonIds = new HashSet<>();
                    for (Record row : unclaimedPokemon) {
                        Long pokemonId = row.get(MAIL_POKEMON_OBJECT_ID);
                        if (pokemonId == null || pokemonId <= 0 || !pokemonIds.add(pokemonId)) {
                            throw new IllegalStateException("邮件精灵附件数据非法");
                        }
                    }
                    transaction.selectFrom(POKEMON)
                            .where(POKEMON.ID.in(pokemonIds))
                            .and(POKEMON.TRAINER_ID.eq(characterId))
                            .and(POKEMON.CONTAINER_ID.eq(MAIL_CONTAINER_ID))
                            .forUpdate()
                            .fetch().forEach(pokemon -> pokemonById.put(pokemon.getId(), pokemon));
                    if (pokemonById.size() != pokemonIds.size()) {
                        throw new IllegalStateException("邮件精灵附件资产不存在或归属错误");
                    }
                }

                int moneyAmount = 0;
                if (unclaimedMoney) {
                    Integer amount = moneyRow.get(MAIL_MONEY_AMOUNT);
                    if (amount == null || amount <= 0) {
                        throw new IllegalStateException("邮件金钱附件数据非法");
                    }
                    moneyAmount = amount;
                    long newMoney = (long) recipient.getMoney() + amount;
                    if (newMoney > Integer.MAX_VALUE) {
                        throw new IllegalStateException("领取邮件后金钱溢出");
                    }
                }

                if (!unclaimedPokemon.isEmpty()) {
                    Set<String> occupiedSlots = new HashSet<>();
                    List<PokemonRecord> currentPokemon = transaction.selectFrom(POKEMON)
                            .where(POKEMON.TRAINER_ID.eq(characterId))
                            .and(POKEMON.CONTAINER_ID.in(PC_CONTAINER_ID, PARTY_CONTAINER_ID))
                            .forUpdate()
                            .fetch();
                    for (PokemonRecord pokemon : currentPokemon) {
                        occupiedSlots.add(slotKey(pokemon.getContainerId(),
                                pokemon.getContainerPosition()));
                    }
                    int pcExpansion = recipient.getPcBoxExpansionNumber() == null
                            ? 0 : Math.max(0, recipient.getPcBoxExpansionNumber());
                    int pcCapacity = Math.min(Short.MAX_VALUE + 1,
                            PokemonContainerType.PC.getSize() + pcExpansion * 60);
                    for (Record row : unclaimedPokemon) {
                        Long pokemonId = row.get(MAIL_POKEMON_OBJECT_ID);
                        PokemonRecord pokemon = pokemonById.get(pokemonId);
                        if (pokemon == null) {
                            throw new IllegalStateException("邮件精灵附件记录丢失");
                        }
                        int newContainerId = -1;
                        short newPosition = -1;
                        for (short position = 0; position < PokemonContainerType.PARTY.getSize(); position++) {
                            if (occupiedSlots.add(slotKey(PARTY_CONTAINER_ID, position))) {
                                newContainerId = PARTY_CONTAINER_ID;
                                newPosition = position;
                                break;
                            }
                        }
                        if (newContainerId < 0) {
                            for (int position = 0; position < pcCapacity; position++) {
                                short candidate = (short) position;
                                if (occupiedSlots.add(slotKey(PC_CONTAINER_ID, candidate))) {
                                    newContainerId = PC_CONTAINER_ID;
                                    newPosition = candidate;
                                    break;
                                }
                            }
                        }
                        if (newContainerId < 0) {
                            throw new IllegalStateException("收件人的 PARTY 和 PC 均无空槽");
                        }
                        if (transaction.update(POKEMON)
                                .set(POKEMON.CONTAINER_ID, newContainerId)
                                .set(POKEMON.CONTAINER_POSITION, newPosition)
                                .where(POKEMON.ID.eq(pokemon.getId()))
                                .and(POKEMON.TRAINER_ID.eq(characterId))
                                .and(POKEMON.CONTAINER_ID.eq(MAIL_CONTAINER_ID))
                                .execute() != 1) {
                            throw new IllegalStateException("邮件精灵领取失败");
                        }
                        if (transaction.update(MAIL_POKEMON_ATTACHMENT)
                                .set(MAIL_POKEMON_CLAIMED, true)
                                .where(MAIL_POKEMON_MAIL_ID.eq(mailId))
                                .and(MAIL_POKEMON_OBJECT_ID.eq(pokemon.getId()))
                                .and(MAIL_POKEMON_CLAIMED.eq(false))
                                .execute() != 1) {
                            throw new IllegalStateException("邮件精灵附件状态更新失败");
                        }
                    }
                }

                for (Record row : unclaimedItems) {
                    long itemId = row.get(MAIL_ITEM_OBJECT_ID);
                    short amount = row.get(MAIL_ITEM_AMOUNT);
                    OwnedItemRecord source = itemById.get(itemId);
                    if (source == null || source.getItemAmount() == null
                            || source.getItemAmount() != amount) {
                        throw new IllegalStateException("邮件道具数量与附件不一致");
                    }
                    claimItem(transaction, characterId, source);
                    if (transaction.update(MAIL_ITEM_ATTACHMENT)
                            .set(MAIL_ITEM_CLAIMED, true)
                            .where(MAIL_ITEM_MAIL_ID.eq(mailId))
                            .and(MAIL_ITEM_OBJECT_ID.eq(itemId))
                            .and(MAIL_ITEM_CLAIMED.eq(false))
                            .execute() != 1) {
                        throw new IllegalStateException("邮件道具附件状态更新失败");
                    }
                }

                int remainingMoney = recipient.getMoney();
                if (unclaimedMoney) {
                    remainingMoney += moneyAmount;
                    if (transaction.update(CHARACTER)
                            .set(CHARACTER.MONEY, remainingMoney)
                            .where(CHARACTER.ID.eq(characterId))
                            .and(CHARACTER.MONEY.eq(recipient.getMoney()))
                            .execute() != 1) {
                        throw new IllegalStateException("邮件金钱领取失败");
                    }
                    if (transaction.update(MAIL_MONEY_ATTACHMENT)
                            .set(MAIL_MONEY_CLAIMED, true)
                            .where(MAIL_MONEY_MAIL_ID.eq(mailId))
                            .and(MAIL_MONEY_CLAIMED.eq(false))
                            .execute() != 1) {
                        throw new IllegalStateException("邮件金钱附件状态更新失败");
                    }
                }

                if (transaction.update(MAIL_MESSAGE)
                        .set(MAIL_IS_READ, true)
                        .where(MAIL_ID.eq(mailId))
                        .and(MAIL_RECIPIENT_ID.eq(characterId))
                        .execute() != 1) {
                    throw new IllegalStateException("领取邮件后已读状态更新失败");
                }
                return new MailClaimResult(true, remainingMoney);
            });
        } catch (RuntimeException exception) {
            log.error("领取邮件附件事务失败: characterId={}, mailId={}",
                    characterId, mailId, exception);
            return MailClaimResult.rejected();
        }
    }

    private void claimItem(DSLContext transaction, long characterId,
                           OwnedItemRecord source) {
        ItemData itemData = ItemManager.getItemData(source.getItemIndexId());
        if (itemData == null || source.getItemAmount() == null || source.getItemAmount() <= 0) {
            throw new IllegalStateException("邮件道具资源不存在");
        }
        short maxStack = itemData.getItemMaxStackSize();
        if (isOrdinaryStackableItem(source, itemData)) {
            OwnedItemRecord existing = transaction.selectFrom(OWNED_ITEM)
                    .where(OWNED_ITEM.OWNER_ID.eq(characterId))
                    .and(OWNED_ITEM.INVENTORY_ID.eq((short) MAIN_INVENTORY_ID))
                    .and(OWNED_ITEM.ITEM_INDEX_ID.eq(source.getItemIndexId()))
                    .and(OWNED_ITEM.COLOR_ID.eq((short) -1))
                    .and(OWNED_ITEM.ITEM_REGION_INDEX_ID.eq((short) -1))
                    .and(OWNED_ITEM.PVP_REWARD_LEVEL.eq((short) -1))
                    .and(OWNED_ITEM.PVP_REWARD_SEASON.eq((short) -1))
                    .orderBy(OWNED_ITEM.ITEM_ID.asc())
                    .forUpdate()
                    .fetchOne();
            if (existing != null && existing.getItemAmount() != null
                    && (long) existing.getItemAmount() + source.getItemAmount() <= maxStack) {
                int mergedAmount = existing.getItemAmount() + source.getItemAmount();
                if (transaction.update(OWNED_ITEM)
                        .set(OWNED_ITEM.ITEM_AMOUNT, (short) mergedAmount)
                        .where(OWNED_ITEM.ITEM_ID.eq(existing.getItemId()))
                        .and(OWNED_ITEM.OWNER_ID.eq(characterId))
                        .and(OWNED_ITEM.INVENTORY_ID.eq((short) MAIN_INVENTORY_ID))
                        .execute() != 1) {
                    throw new IllegalStateException("邮件道具合并失败");
                }
                if (transaction.update(OWNED_ITEM)
                        .set(OWNED_ITEM.INVENTORY_ID, (short) 0)
                        .where(OWNED_ITEM.ITEM_ID.eq(source.getItemId()))
                        .and(OWNED_ITEM.OWNER_ID.eq(characterId))
                        .and(OWNED_ITEM.INVENTORY_ID.eq((short) MAIL_INVENTORY_ID))
                        .execute() != 1) {
                    throw new IllegalStateException("邮件道具归档失败");
                }
                return;
            }
        }

        if (transaction.update(OWNED_ITEM)
                .set(OWNED_ITEM.INVENTORY_ID, (short) MAIN_INVENTORY_ID)
                .where(OWNED_ITEM.ITEM_ID.eq(source.getItemId()))
                .and(OWNED_ITEM.OWNER_ID.eq(characterId))
                .and(OWNED_ITEM.INVENTORY_ID.eq((short) MAIL_INVENTORY_ID))
                .execute() != 1) {
            throw new IllegalStateException("邮件道具转入主背包失败");
        }
    }

    private static String slotKey(Integer containerId, Short position) {
        return String.valueOf(containerId) + ':' + String.valueOf(position);
    }

    private static boolean shouldClaimSlot(int targetSlot, int slot) {
        return targetSlot < 0 || targetSlot == slot;
    }

    private static boolean isOrdinaryStackableItem(OwnedItemRecord source,
                                                    ItemData itemData) {
        return itemData.getItemMaxStackSize() > 1
                && isUnset(source.getColorId())
                && isUnset(source.getItemRegionIndexId())
                && isUnset(source.getPvpRewardLevel())
                && isUnset(source.getPvpRewardSeason());
    }

    private static boolean isUnset(Short value) {
        return value == null || value == -1;
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
}
