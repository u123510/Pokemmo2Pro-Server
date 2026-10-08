package org.pokemmo.gameserver.services.item;

import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.pokemmo.db.Database;
import org.pokemmo.db.jooq.tables.records.OwnedItemRecord;
import org.pokemmo.db.jooq.tables.records.PokemonRecord;
import org.pokemmo.gameserver.game.item.ItemData;
import org.pokemmo.gameserver.game.item.ItemEffectType;
import org.pokemmo.gameserver.game.item.ItemManager;
import org.pokemmo.gameserver.game.item.ItemUseHandlerType;
import org.pokemmo.gameserver.game.item.ItemUseManager;
import org.pokemmo.gameserver.game.item.ItemUseRule;
import org.pokemmo.gameserver.game.pokemon.PokemonData;
import org.pokemmo.gameserver.game.pokemon.PokemonGetExpSpeedType;
import org.pokemmo.gameserver.game.pokemon.PokemonStatusType;
import org.pokemmo.gameserver.game.pokemon.PokemonStatType;
import org.pokemmo.gameserver.util.ArrayUtil;

import java.util.Arrays;

import static org.pokemmo.db.jooq.Tables.CHARACTER;
import static org.pokemmo.db.jooq.Tables.OWNED_ITEM;
import static org.pokemmo.db.jooq.Tables.POKEMON;

/** Applies item effects and persists the result in one owner-scoped transaction. */
@Slf4j
public final class ItemUseService {
    private static final short MAIN_INVENTORY_ID = 1;

    private final Database database;
    private final ItemUseManager itemUseManager;

    public ItemUseService(Database database, ItemUseManager itemUseManager) {
        this.database = database;
        this.itemUseManager = itemUseManager;
    }

    public record Result(
            boolean success,
            String reason,
            PokemonData pokemon,
            int consumedAmount,
            boolean transportationChanged,
            byte transportation,
            boolean reloadLevel,
            boolean reloadMove,
            boolean reloadCurrentHp,
            boolean reloadStatus,
            boolean reloadFriendValue,
            boolean reloadParticleEffects) {
        public static Result rejected(String reason) {
            return new Result(false, reason, null, 0, false, (byte) 0,
                    false, false, false, false, false, false);
        }
    }

    public Result useItem(
            long characterId,
            short itemIndexId,
            long targetPokemonId,
            short usedItemAmount,
            byte targetMovePosition,
            byte unuse) {
        int itemId = Short.toUnsignedInt(itemIndexId);
        int useAmount = Short.toUnsignedInt(usedItemAmount);
        if (characterId <= 0 || itemId <= 0 || useAmount <= 0 || useAmount > 9999) {
            return Result.rejected("参数非法");
        }
        if (ItemManager.isStoryBound(itemIndexId)) {
            return Result.rejected("任务物品只能通过对应剧情交付");
        }

        ItemData itemData = ItemManager.getItemData(itemIndexId);
        ItemUseRule rule = itemUseManager == null ? null : itemUseManager.getRule(itemIndexId);
        ItemUseHandlerType handler = resolveHandler(itemData, rule);
        if (handler == ItemUseHandlerType.NONE) {
            return Result.rejected("道具没有可用效果");
        }
        if (handler == ItemUseHandlerType.BIKE) {
            return useBike(characterId, itemIndexId, rule);
        }
        if (itemData == null) {
            return Result.rejected("未知道具不能使用");
        }
        if (targetPokemonId <= 0) {
            return Result.rejected("该道具需要有效的目标宝可梦");
        }
        int consumeAmount = Math.max(1, rule == null ? 1 : rule.getConsumeAmount()) * useAmount;
        if (consumeAmount <= 0 || consumeAmount > 9999) {
            return Result.rejected("道具消耗数量非法");
        }

        try {
            return database.ctx().transactionResult(configuration -> {
                DSLContext transaction = DSL.using(configuration);
                OwnedItemRecord ownedItem = transaction.selectFrom(OWNED_ITEM)
                        .where(OWNED_ITEM.OWNER_ID.eq(characterId))
                        .and(OWNED_ITEM.INVENTORY_ID.eq(MAIN_INVENTORY_ID))
                        .and(OWNED_ITEM.ITEM_INDEX_ID.eq(itemIndexId))
                        .and(OWNED_ITEM.COLOR_ID.eq((short) -1))
                        .and(OWNED_ITEM.ITEM_REGION_INDEX_ID.eq((short) -1))
                        .and(OWNED_ITEM.PVP_REWARD_LEVEL.eq((short) -1))
                        .and(OWNED_ITEM.PVP_REWARD_SEASON.eq((short) -1))
                        .and(OWNED_ITEM.ITEM_AMOUNT.ge((short) consumeAmount))
                        .orderBy(OWNED_ITEM.ITEM_ID.asc())
                        .forUpdate()
                        .fetchOne();
                if (ownedItem == null) {
                    return Result.rejected("主背包中没有足够数量的道具");
                }

                PokemonRecord record = transaction.selectFrom(POKEMON)
                        .where(POKEMON.ID.eq(targetPokemonId))
                        .and(POKEMON.TRAINER_ID.eq(characterId))
                        .and(POKEMON.CONTAINER_ID.eq(1))
                        .forUpdate()
                        .fetchOne();
                if (record == null) {
                    return Result.rejected("目标宝可梦不存在或不属于当前角色");
                }

                PokemonData pokemon = new PokemonData.Builder().setByRecord(record).build();
                if (!applyEffects(record, pokemon, handler, itemData, rule,
                        useAmount, targetMovePosition)) {
                    return Result.rejected("道具效果不适用于当前目标");
                }

                consumeItem(transaction, characterId, ownedItem, consumeAmount);
                if (transaction.update(POKEMON)
                        .set(POKEMON.STATUS, record.getStatus())
                        .set(POKEMON.LEVEL_VALUE, record.getLevelValue())
                        .set(POKEMON.CURRENT_HP, record.getCurrentHp())
                        .set(POKEMON.EXP, record.getExp())
                        .set(POKEMON.FRIEND_VALUE, record.getFriendValue())
                        .set(POKEMON.MOVES_PP, record.getMovesPp())
                        .set(POKEMON.PARTICLE_EFFECTS, record.getParticleEffects())
                        .where(POKEMON.ID.eq(targetPokemonId))
                        .and(POKEMON.TRAINER_ID.eq(characterId))
                        .execute() != 1) {
                    throw new IllegalStateException("宝可梦道具效果保存失败");
                }
                boolean reloadLevel = handler == ItemUseHandlerType.ADD_EXP
                        || handler == ItemUseHandlerType.ADD_LEVEL;
                boolean reloadMove = handler == ItemUseHandlerType.HEAL_PP
                        || (itemData != null && itemData.getItemRecoverPpValue() > 0);
                boolean reloadCurrentHp = handler == ItemUseHandlerType.HEAL_HP
                        || handler == ItemUseHandlerType.REVIVE_HP
                        || reloadLevel
                        || (itemData != null && itemData.getItemRecoverHpValue() > 0);
                boolean reloadStatus = handler == ItemUseHandlerType.CURE_STATUS
                        || handler == ItemUseHandlerType.REVIVE_HP
                        || (itemData != null && itemData.getItemCureStatusConditionValue() != 0);
                boolean reloadFriendValue = handler == ItemUseHandlerType.ADD_FRIENDSHIP;
                boolean reloadParticleEffects = handler == ItemUseHandlerType.ADD_PARTICLE_EFFECT;
                Result result = new Result(true, "", new PokemonData.Builder().setByRecord(record).build(),
                        consumeAmount, false, (byte) 0, reloadLevel, reloadMove,
                        reloadCurrentHp, reloadStatus, reloadFriendValue, reloadParticleEffects);
                log.trace("道具使用成功: characterId={}, itemIndexId={}, handler={}, targetPokemonId={}, "
                        + "requestedAmount={}, consumedAmount={}, reloadLevel={}, reloadMove={}, "
                                + "reloadHp={}, reloadStatus={}, reloadFriendship={}, reloadParticles={}",
                        characterId, itemId, handler, targetPokemonId, useAmount, consumeAmount,
                        reloadLevel, reloadMove, reloadCurrentHp, reloadStatus, reloadFriendValue,
                        reloadParticleEffects);
                return result;
            });
        } catch (RuntimeException exception) {
            log.error("使用道具事务失败: characterId={}, itemIndexId={}, targetPokemonId={}",
                    characterId, itemId, targetPokemonId, exception);
            return Result.rejected("道具效果保存失败");
        }
    }

    private Result useBike(long characterId, short itemIndexId, ItemUseRule rule) {
        if (rule != null && rule.getRegionIndexId() >= 0) {
            Short regionId = database.ctx().select(CHARACTER.REGION_ID)
                    .from(CHARACTER).where(CHARACTER.ID.eq(characterId)).fetchOne(CHARACTER.REGION_ID);
            if (regionId == null || regionId != rule.getRegionIndexId()) {
                return Result.rejected("当前地区不能使用该道具");
            }
        }
        try {
            return database.ctx().transactionResult(configuration -> {
                DSLContext transaction = DSL.using(configuration);
                var character = transaction.selectFrom(CHARACTER)
                        .where(CHARACTER.ID.eq(characterId)).forUpdate().fetchOne();
                if (character == null) {
                    return Result.rejected("角色不存在");
                }
                byte current = character.getTransportation() == null
                        ? 0 : character.getTransportation().byteValue();
                byte next = (byte) ((current & 2) == 2 ? 0 : 2);
                if (transaction.update(CHARACTER).set(CHARACTER.TRANSPORTATION, (short) next)
                        .where(CHARACTER.ID.eq(characterId)).execute() != 1) {
                    return Result.rejected("自行车状态保存失败");
                }
                return new Result(true, "", null, 0, true, next,
                        false, false, false, false, false, false);
            });
        } catch (RuntimeException exception) {
            log.error("使用自行车失败: characterId={}, itemIndexId={}",
                    characterId, Short.toUnsignedInt(itemIndexId), exception);
            return Result.rejected("自行车状态保存失败");
        }
    }

    private ItemUseHandlerType resolveHandler(ItemData itemData, ItemUseRule rule) {
        if (rule != null && rule.getHandler() != null && rule.getHandler() != ItemUseHandlerType.NONE) {
            return rule.getHandler();
        }
        if (itemData == null) {
            return ItemUseHandlerType.NONE;
        }
        if (itemData.getItemParticleEffectType() != null
                && itemData.getItemParticleEffectType().getType() > 5) {
            return ItemUseHandlerType.ADD_PARTICLE_EFFECT;
        }
        if (itemData.getItemFirstEffectType() == ItemEffectType.MONSTER_BATTLE_SLOT_ONLY
                || itemData.getItemSecondEffectType() == ItemEffectType.MONSTER_BATTLE_SLOT_ONLY) {
            return ItemUseHandlerType.NONE;
        }
        if (itemData.getItemFirstEffectType() == ItemEffectType.NOT_USABLE) {
            if (itemData.getItemRecoverHpValue() > 0) return ItemUseHandlerType.REVIVE_HP;
            if (itemData.getItemExpValue() > 0) return ItemUseHandlerType.ADD_EXP;
            if (itemData.getItemIncreasePokemonLevelValue() > 0) return ItemUseHandlerType.ADD_LEVEL;
        }
        if (itemData.getItemRecoverHpValue() > 0
                && itemData.isRecoverHpPercent()
                && itemData.getItemFirstEffectType() == ItemEffectType.NOT_USABLE) {
            return ItemUseHandlerType.REVIVE_HP;
        }
        if (itemData.getItemRecoverHpValue() > 0) return ItemUseHandlerType.HEAL_HP;
        if (itemData.getItemCureStatusConditionValue() != 0) return ItemUseHandlerType.CURE_STATUS;
        if (itemData.getItemRecoverPpValue() > 0) return ItemUseHandlerType.HEAL_PP;
        if (itemData.getItemExpValue() > 0) return ItemUseHandlerType.ADD_EXP;
        if (itemData.getItemIncreasePokemonLevelValue() > 0) return ItemUseHandlerType.ADD_LEVEL;
        if (itemData.getItemIncreaseFriendshipValue() > 0) return ItemUseHandlerType.ADD_FRIENDSHIP;
        return ItemUseHandlerType.NONE;
    }

    private boolean applyEffects(
            PokemonRecord record,
            PokemonData pokemon,
            ItemUseHandlerType handler,
            ItemData itemData,
            ItemUseRule rule,
            int useAmount,
            byte targetMovePosition) {
        if (handler == ItemUseHandlerType.ADD_PARTICLE_EFFECT) {
            return applyEffect(record, pokemon, handler, itemData, rule,
                    useAmount, targetMovePosition);
        }
        if (rule != null) {
            int configuredAmount = rule.getAmount() > 0
                    ? rule.getAmount() * useAmount
                    : defaultEffectAmount(itemData, handler) * useAmount;
            if (configuredAmount <= 0) {
                return false;
            }
            return applyEffect(record, pokemon, handler, itemData, rule,
                    configuredAmount, targetMovePosition);
        }

        boolean changed = false;
        if (itemData.getItemRecoverHpValue() > 0) {
            ItemUseHandlerType hpHandler = itemData.getItemFirstEffectType() == ItemEffectType.NOT_USABLE
                    ? ItemUseHandlerType.REVIVE_HP : ItemUseHandlerType.HEAL_HP;
            changed |= applyEffect(record, pokemon, hpHandler, itemData, null,
                    itemData.getItemRecoverHpValue() * useAmount, targetMovePosition);
        }
        if (itemData.getItemCureStatusConditionValue() != 0) {
            changed |= applyEffect(record, pokemon, ItemUseHandlerType.CURE_STATUS, itemData, null,
                    0, targetMovePosition);
        }
        if (itemData.getItemRecoverPpValue() > 0) {
            changed |= applyEffect(record, pokemon, ItemUseHandlerType.HEAL_PP, itemData, null,
                    Byte.toUnsignedInt(itemData.getItemRecoverPpValue()) * useAmount,
                    targetMovePosition);
        }
        if (itemData.getItemExpValue() > 0) {
            changed |= applyEffect(record, pokemon, ItemUseHandlerType.ADD_EXP, itemData, null,
                    itemData.getItemExpValue() * useAmount, targetMovePosition);
        }
        if (itemData.getItemIncreasePokemonLevelValue() > 0) {
            changed |= applyEffect(record, pokemon, ItemUseHandlerType.ADD_LEVEL, itemData, null,
                    Byte.toUnsignedInt(itemData.getItemIncreasePokemonLevelValue()) * useAmount,
                    targetMovePosition);
        }
        if (itemData.getItemIncreaseFriendshipValue() > 0) {
            changed |= applyEffect(record, pokemon, ItemUseHandlerType.ADD_FRIENDSHIP, itemData, null,
                    itemData.getItemIncreaseFriendshipValue() * useAmount, targetMovePosition);
        }
        return changed;
    }

    private boolean applyEffect(
            PokemonRecord record,
            PokemonData pokemon,
            ItemUseHandlerType handler,
            ItemData itemData,
            ItemUseRule rule,
            int amount,
            byte targetMovePosition) {
        switch (handler) {
            case HEAL_HP -> {
                short currentHp = record.getCurrentHp();
                if (currentHp <= 0) return false;
                int maxHp = getMaxHp(pokemon, pokemon.getLevel());
                int recovery = rule != null && rule.isPercent()
                        ? (int) Math.ceil(maxHp * amount / 100.0)
                        : itemData.isRecoverHpPercent()
                        ? (int) Math.ceil(maxHp * amount / 100.0)
                        : amount;
                int nextHp = Math.min(maxHp, currentHp + Math.max(1, recovery));
                if (nextHp == currentHp) return false;
                record.setCurrentHp((short) nextHp);
            }
            case REVIVE_HP -> {
                if (record.getCurrentHp() > 0) return false;
                int maxHp = getMaxHp(pokemon, pokemon.getLevel());
                int recovery = (int) Math.ceil(maxHp * amount / 100.0);
                record.setCurrentHp((short) Math.max(1, Math.min(maxHp, recovery)));
                record.setStatus((short) PokemonStatusType.NORMAL.getType());
            }
            case CURE_STATUS -> {
                int mask = rule != null && rule.getStatusMask() != 0
                        ? rule.getStatusMask() : Byte.toUnsignedInt(itemData.getItemCureStatusConditionValue());
                if (itemData != null && itemData.getItemCureStatusConditionValue() == -1) mask = 0xFF;
                int current = record.getStatus() == null
                        ? 0 : Byte.toUnsignedInt(record.getStatus().byteValue());
                if (current == 0 || (mask != 0xFF && (current & mask) == 0)) return false;
                record.setStatus((short) PokemonStatusType.NORMAL.getType());
            }
            case HEAL_PP -> {
                short[] pps = pokemon.getMovesPp().clone();
                int position = targetMovePosition;
                boolean changed = false;
                for (int index = 0; index < pps.length; index++) {
                    if (position >= 0 && position != index) continue;
                    if (pokemon.getMoves()[index] <= 0) continue;
                    int maxPp = pokemon.getPokemonMoveMaxPp(index);
                    int next = Math.min(maxPp, pps[index] + amount);
                    changed |= next != pps[index];
                    pps[index] = (short) next;
                }
                if (!changed) return false;
                record.setMovesPp(org.pokemmo.gameserver.util.ArrayUtil.toShortObject(pps));
            }
            case ADD_EXP -> {
                int currentExp = Math.max(0, pokemon.getExp());
                int nextExp = (int) Math.min(Integer.MAX_VALUE, (long) currentExp + amount);
                PokemonGetExpSpeedType speed = pokemon.getPokemonDexData().getGetExpSpeedType();
                int nextLevel = speed.getLevelByExp(nextExp);
                int oldMaxHp = pokemon.getMaxHp();
                int nextMaxHp = getMaxHp(pokemon, nextLevel);
                record.setExp(nextExp);
                record.setLevelValue((short) nextLevel);
                if (record.getCurrentHp() > 0) {
                    record.setCurrentHp((short) Math.min(nextMaxHp,
                            record.getCurrentHp() + Math.max(0, nextMaxHp - oldMaxHp)));
                }
            }
            case ADD_LEVEL -> {
                int nextLevel = Math.min(100, pokemon.getLevel() + amount);
                if (nextLevel == pokemon.getLevel()) return false;
                PokemonGetExpSpeedType speed = pokemon.getPokemonDexData().getGetExpSpeedType();
                int oldMaxHp = pokemon.getMaxHp();
                int nextMaxHp = getMaxHp(pokemon, nextLevel);
                record.setLevelValue((short) nextLevel);
                record.setExp(Math.max(record.getExp(), speed.getExpByLevel(nextLevel)));
                if (record.getCurrentHp() > 0) {
                    record.setCurrentHp((short) Math.min(nextMaxHp,
                            record.getCurrentHp() + Math.max(0, nextMaxHp - oldMaxHp)));
                }
            }
            case ADD_FRIENDSHIP -> {
                int friendshipAmount = pokemon.getBallType() == 10 ? amount * 2 : amount;
                int next = Math.min(255, pokemon.getFriendValue() + friendshipAmount);
                if (next == pokemon.getFriendValue()) return false;
                record.setFriendValue((short) next);
            }
            case ADD_PARTICLE_EFFECT -> {
                if (itemData == null || itemData.getItemParticleEffectType() == null) {
                    return false;
                }
                short particleType = itemData.getItemParticleEffectType().getType();
                short[] existing = PokemonData.normalizeParticleEffects(
                        ArrayUtil.toShortPrimitive(record.getParticleEffects()));
                for (short effect : existing) {
                    if (effect == particleType) {
                        return false;
                    }
                }
                short[] updated = Arrays.copyOf(existing, existing.length + 1);
                updated[updated.length - 1] = particleType;
                record.setParticleEffects(ArrayUtil.toShortObject(updated));
            }
            case ADD_EV, TEACH_MOVE -> {
                return false;
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    private int getMaxHp(PokemonData pokemon, int level) {
        return pokemon.getPokemonDexData().getPokemonAbilityValue(
                PokemonStatType.HP,
                pokemon.getPokemonIvs()[PokemonStatType.HP.getType()],
                pokemon.getPokemonEvs()[PokemonStatType.HP.getType()],
                level,
                pokemon.getNatureType());
    }

    private int defaultEffectAmount(ItemData itemData, ItemUseHandlerType handler) {
        return switch (handler) {
            case HEAL_HP, REVIVE_HP -> itemData.getItemRecoverHpValue();
            case HEAL_PP -> Byte.toUnsignedInt(itemData.getItemRecoverPpValue());
            case ADD_EXP -> itemData.getItemExpValue();
            case ADD_LEVEL -> Byte.toUnsignedInt(itemData.getItemIncreasePokemonLevelValue());
            case ADD_FRIENDSHIP -> itemData.getItemIncreaseFriendshipValue();
            case CURE_STATUS -> 1;
            default -> 1;
        };
    }

    private void consumeItem(
            DSLContext transaction, long characterId, OwnedItemRecord item, int amount) {
        int remaining = item.getItemAmount() - amount;
        int updated = remaining == 0
                ? transaction.deleteFrom(OWNED_ITEM)
                .where(OWNED_ITEM.ITEM_ID.eq(item.getItemId()))
                .and(OWNED_ITEM.OWNER_ID.eq(characterId))
                .and(OWNED_ITEM.INVENTORY_ID.eq(MAIN_INVENTORY_ID)).execute()
                : transaction.update(OWNED_ITEM).set(OWNED_ITEM.ITEM_AMOUNT, (short) remaining)
                .where(OWNED_ITEM.ITEM_ID.eq(item.getItemId()))
                .and(OWNED_ITEM.OWNER_ID.eq(characterId))
                .and(OWNED_ITEM.INVENTORY_ID.eq(MAIN_INVENTORY_ID)).execute();
        if (updated != 1) {
            throw new IllegalStateException("道具扣除失败: itemId=" + item.getItemId());
        }
    }
}
