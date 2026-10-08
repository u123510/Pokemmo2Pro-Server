package org.pokemmo.gameserver.services.story;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.pokemmo.db.Database;
import org.pokemmo.db.jooq.tables.records.CharacterRecord;
import org.pokemmo.db.jooq.tables.records.InventoryRecord;
import org.pokemmo.db.jooq.tables.records.OwnedItemRecord;
import org.pokemmo.db.jooq.tables.records.PokemonRecord;
import org.pokemmo.gameserver.game.container.PokemonContainerType;
import org.pokemmo.gameserver.game.item.ItemData;
import org.pokemmo.gameserver.game.item.ItemManager;

import static org.pokemmo.db.jooq.Tables.CHARACTER;
import static org.pokemmo.db.jooq.Tables.INVENTORY;
import static org.pokemmo.db.jooq.Tables.OWNED_ITEM;
import static org.pokemmo.db.jooq.Tables.POKEMON;

/** Atomically applies one generic story node's rewards and durable flags. */
public final class StoryActionStore {
    public record ItemGrant(short itemIndexId, short amount, long objectId) {
    }

    public record ItemRemoval(short itemIndexId, short amount) {
    }

    public record Plan(
            String actionKey,
            List<ItemGrant> itemGrants,
            List<ItemRemoval> itemRemovals,
            List<PokemonRecord> pokemonRewards,
            List<Integer> storyBits,
            List<Integer> extraStoryBits,
            List<Integer> badgeBits,
            boolean champion) {
        public Plan {
            itemGrants = List.copyOf(itemGrants);
            itemRemovals = List.copyOf(itemRemovals);
            pokemonRewards = List.copyOf(pokemonRewards);
            storyBits = List.copyOf(storyBits);
            extraStoryBits = List.copyOf(extraStoryBits);
            badgeBits = List.copyOf(badgeBits);
        }
    }

    public record Result(
            boolean applied,
            short storyFlag,
            short extraStoryFlag,
            short badgeFlag,
            boolean champion,
            List<PokemonRecord> pokemonRewards) {
        public Result {
            pokemonRewards = List.copyOf(pokemonRewards);
        }
    }

    private final Database database;

    public StoryActionStore(Database database) {
        this.database = database;
        database.ctx().execute("""
                CREATE TABLE IF NOT EXISTS character_story_action (
                    character_id BIGINT NOT NULL,
                    action_key VARCHAR(255) NOT NULL,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (character_id, action_key),
                    FOREIGN KEY (character_id) REFERENCES character(id) ON DELETE CASCADE
                )
                """);
    }

    public Result commit(int accountId, long characterId, Plan plan) {
        if (accountId <= 0 || characterId <= 0 || plan == null
                || plan.actionKey() == null || plan.actionKey().isBlank()
                || plan.actionKey().length() > 255) {
            throw new IllegalArgumentException("剧情动作提交参数无效");
        }
        return database.ctx().transactionResult(configuration -> {
            DSLContext tx = DSL.using(configuration);
            CharacterRecord owner = tx.selectFrom(CHARACTER)
                    .where(CHARACTER.ID.eq(characterId))
                    .and(CHARACTER.ACCOUNT_ID.eq(accountId))
                    .forUpdate()
                    .fetchOne();
            if (owner == null) {
                throw new IllegalStateException("角色不存在或不属于当前账号");
            }
            if (tx.execute("""
                    INSERT INTO character_story_action (character_id, action_key)
                    VALUES (?, ?)
                    ON CONFLICT (character_id, action_key) DO NOTHING
                    """, characterId, plan.actionKey()) != 1) {
                return snapshot(owner, false, List.of());
            }

            InventoryRecord inventory = null;
            List<OwnedItemRecord> items = List.of();
            if (!plan.itemGrants().isEmpty() || !plan.itemRemovals().isEmpty()) {
                inventory = tx.selectFrom(INVENTORY)
                        .where(INVENTORY.NAME.eq("inventory"))
                        .fetchOne();
                if (inventory == null) {
                    throw new IllegalStateException("主背包尚未就绪");
                }
                items = new ArrayList<>(tx.selectFrom(OWNED_ITEM)
                        .where(OWNED_ITEM.OWNER_ID.eq(characterId))
                        .and(OWNED_ITEM.INVENTORY_ID.eq(inventory.getId()))
                        .forUpdate()
                        .fetch());
                for (ItemRemoval removal : plan.itemRemovals()) {
                    removeItem(tx, characterId, inventory, items,
                            removal.itemIndexId(), removal.amount());
                }
                for (ItemGrant grant : plan.itemGrants()) {
                    grantItem(tx, characterId, inventory, items, grant);
                }
            }

            List<PokemonRecord> insertedPokemon = insertPokemon(
                    tx, owner, characterId, plan.pokemonRewards());
            Short[] story = requiredShortArray(owner.getStoryLineFlag(), 2, "故事位");
            Short[] badges = requiredShortArray(owner.getBadgeFlag(), 1, "徽章位");
            Boolean[] championFlags = owner.getChampionFlag();
            if (championFlags == null || championFlags.length < 5 || championFlags[0] == null) {
                throw new IllegalStateException("冠军标志数组无效");
            }
            boolean champion = championFlags[0];
            boolean storyChanged = false;
            boolean badgeChanged = false;
            for (int bit : plan.storyBits()) {
                validateBit(bit);
                short next = (short) (story[0] | (1 << bit));
                storyChanged |= next != story[0];
                story[0] = next;
            }
            for (int bit : plan.extraStoryBits()) {
                validateBit(bit);
                short next = (short) (story[1] | (1 << bit));
                storyChanged |= next != story[1];
                story[1] = next;
            }
            for (int bit : plan.badgeBits()) {
                validateBit(bit);
                short next = (short) (badges[0] | (1 << bit));
                badgeChanged |= next != badges[0];
                badges[0] = next;
            }
            if (plan.champion()) {
                champion = true;
            }
            if (storyChanged && tx.update(CHARACTER)
                    .set(CHARACTER.STORY_LINE_FLAG, story)
                    .where(CHARACTER.ID.eq(characterId))
                    .and(CHARACTER.ACCOUNT_ID.eq(accountId))
                    .execute() != 1) {
                throw new IllegalStateException("剧情故事位保存失败");
            }
            if (badgeChanged && tx.update(CHARACTER)
                    .set(CHARACTER.BADGE_FLAG, badges)
                    .where(CHARACTER.ID.eq(characterId))
                    .and(CHARACTER.ACCOUNT_ID.eq(accountId))
                    .execute() != 1) {
                throw new IllegalStateException("剧情徽章保存失败");
            }
            if (plan.champion() && !Boolean.TRUE.equals(championFlags[0])
                    && tx.update(CHARACTER)
                    .set(CHARACTER.CHAMPION_FLAG, new Boolean[]{true,
                            championFlags[1], championFlags[2],
                            championFlags[3], championFlags[4]})
                    .where(CHARACTER.ID.eq(characterId))
                    .and(CHARACTER.ACCOUNT_ID.eq(accountId))
                    .execute() != 1) {
                throw new IllegalStateException("冠军标志保存失败");
            }
            return new Result(true, story[0], story[1], badges[0], champion, insertedPokemon);
        });
    }

    private static Result snapshot(CharacterRecord owner, boolean applied,
                                   List<PokemonRecord> pokemonRewards) {
        Short[] story = requiredShortArray(owner.getStoryLineFlag(), 2, "故事位");
        Short[] badges = requiredShortArray(owner.getBadgeFlag(), 1, "徽章位");
        Boolean[] champion = owner.getChampionFlag();
        if (champion == null || champion.length == 0 || champion[0] == null) {
            throw new IllegalStateException("冠军标志数组无效");
        }
        return new Result(applied, story[0], story[1], badges[0], champion[0], pokemonRewards);
    }

    private static void grantItem(DSLContext tx, long characterId, InventoryRecord inventory,
                                  List<OwnedItemRecord> items, ItemGrant grant) {
        ItemData metadata = ItemManager.getItemData(grant.itemIndexId());
        if (metadata == null || grant.amount() <= 0
                || metadata.getItemMaxStackSize() <= 0) {
            throw new IllegalArgumentException("剧情奖励道具参数无效");
        }
        int remaining = grant.amount();
        for (OwnedItemRecord row : items) {
            if (remaining == 0) break;
            if (row.getItemIndexId() != grant.itemIndexId()
                    || row.getColorId() != -1 || row.getItemRegionIndexId() != -1
                    || row.getPvpRewardLevel() != -1 || row.getPvpRewardSeason() != -1) {
                continue;
            }
            int addition = Math.min(remaining,
                    Math.max(0, metadata.getItemMaxStackSize() - row.getItemAmount()));
            if (addition > 0) {
                updateItemAmount(tx, characterId, inventory, row,
                        (short) (row.getItemAmount() + addition));
                remaining -= addition;
            }
        }
        if (remaining > 0) {
            if (grant.objectId() <= 0) throw new IllegalArgumentException("剧情道具编号无效");
            OwnedItemRecord row = new OwnedItemRecord();
            row.setItemId(grant.objectId());
            row.setOwnerId(characterId);
            row.setItemIndexId(grant.itemIndexId());
            row.setItemAmount((short) remaining);
            row.setInventoryId(inventory.getId());
            row.setColorId((short) -1);
            row.setItemRegionIndexId((short) -1);
            row.setPvpRewardLevel((short) -1);
            row.setPvpRewardSeason((short) -1);
            if (tx.insertInto(OWNED_ITEM).set(row).execute() != 1) {
                throw new IllegalStateException("剧情奖励道具发放失败");
            }
            items.add(row);
        }
    }

    private static void removeItem(DSLContext tx, long characterId, InventoryRecord inventory,
                                   List<OwnedItemRecord> items, short itemIndexId, short amount) {
        if (itemIndexId <= 0 || amount <= 0) {
            throw new IllegalArgumentException("剧情扣除道具参数无效");
        }
        int remaining = amount;
        for (OwnedItemRecord row : new ArrayList<>(items)) {
            if (remaining == 0) break;
            if (row.getItemIndexId() != itemIndexId || row.getItemAmount() <= 0) continue;
            int used = Math.min(remaining, row.getItemAmount());
            short next = (short) (row.getItemAmount() - used);
            if (next == 0) {
                if (tx.deleteFrom(OWNED_ITEM)
                        .where(OWNED_ITEM.ITEM_ID.eq(row.getItemId()))
                        .and(OWNED_ITEM.OWNER_ID.eq(characterId))
                        .and(OWNED_ITEM.INVENTORY_ID.eq(inventory.getId()))
                        .execute() != 1) {
                    throw new IllegalStateException("剧情道具扣除失败");
                }
                items.remove(row);
            } else {
                updateItemAmount(tx, characterId, inventory, row, next);
            }
            remaining -= used;
        }
        if (remaining > 0) {
            throw new IllegalStateException("剧情道具不足: " + itemIndexId);
        }
    }

    private static void updateItemAmount(DSLContext tx, long characterId,
                                         InventoryRecord inventory, OwnedItemRecord row, short amount) {
        if (tx.update(OWNED_ITEM).set(OWNED_ITEM.ITEM_AMOUNT, amount)
                .where(OWNED_ITEM.ITEM_ID.eq(row.getItemId()))
                .and(OWNED_ITEM.OWNER_ID.eq(characterId))
                .and(OWNED_ITEM.INVENTORY_ID.eq(inventory.getId()))
                .and(OWNED_ITEM.ITEM_AMOUNT.eq(row.getItemAmount()))
                .execute() != 1) {
            throw new IllegalStateException("剧情道具数量更新失败");
        }
        row.setItemAmount(amount);
    }

    private static List<PokemonRecord> insertPokemon(
            DSLContext tx, CharacterRecord owner, long characterId, List<PokemonRecord> rewards) {
        if (rewards.isEmpty()) return List.of();
        List<PokemonRecord> existing = tx.selectFrom(POKEMON)
                .where(POKEMON.TRAINER_ID.eq(characterId))
                .and(POKEMON.CONTAINER_ID.in(
                        (int) PokemonContainerType.PARTY.getType(),
                        (int) PokemonContainerType.PC.getType()))
                .forUpdate()
                .fetch();
        Set<String> occupied = new HashSet<>();
        for (PokemonRecord record : existing) {
            occupied.add(record.getContainerId() + ":" + record.getContainerPosition());
        }
        Set<String> requested = new HashSet<>();
        int pcCapacity = PokemonContainerType.PC.getSize()
                + Math.max(0, owner.getPcBoxExpansionNumber()) * 60;
        for (PokemonRecord reward : rewards) {
            if (reward == null || reward.getId() == null
                    || reward.getTrainerId() != characterId
                    || reward.getContainerId() == null || reward.getContainerPosition() == null
                    || (reward.getContainerId() == PokemonContainerType.PARTY.getType()
                    && (reward.getContainerPosition() < 0
                    || reward.getContainerPosition() >= PokemonContainerType.PARTY.getSize()))
                    || (reward.getContainerId() == PokemonContainerType.PC.getType()
                    && (reward.getContainerPosition() < 0
                    || reward.getContainerPosition() >= pcCapacity))) {
                throw new IllegalStateException("剧情奖励宝可梦容器位置无效");
            }
            String slot = reward.getContainerId() + ":" + reward.getContainerPosition();
            if (!occupied.add(slot) || !requested.add(slot)
                    || tx.insertInto(POKEMON).set(reward).execute() != 1) {
                throw new IllegalStateException("剧情奖励宝可梦写入失败");
            }
        }
        return rewards;
    }

    private static Short[] requiredShortArray(Short[] values, int minimum, String name) {
        if (values == null || values.length < minimum) {
            throw new IllegalStateException(name + "数组无效");
        }
        return values.clone();
    }

    private static void validateBit(int bit) {
        if (bit < 0 || bit > 15) throw new IllegalArgumentException("剧情标记参数无效");
    }
}
