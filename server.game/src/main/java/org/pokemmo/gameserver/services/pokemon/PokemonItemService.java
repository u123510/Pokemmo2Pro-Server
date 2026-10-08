package org.pokemmo.gameserver.services.pokemon;

import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Result;
import org.jooq.impl.DSL;
import org.pokemmo.db.Database;
import org.pokemmo.db.jooq.tables.records.ContainerRecord;
import org.pokemmo.db.jooq.tables.records.OwnedItemRecord;
import org.pokemmo.db.jooq.tables.records.PokemonRecord;
import org.pokemmo.gameserver.game.character.CharacterData;
import org.pokemmo.gameserver.game.container.PokemonContainerType;
import org.pokemmo.gameserver.game.item.ItemData;
import org.pokemmo.gameserver.game.item.ItemManager;
import org.pokemmo.gameserver.game.particleEffectType.ParticleEffectType;
import org.pokemmo.gameserver.game.pokemon.PokemonData;
import org.pokemmo.gameserver.game.pokemon.PokemonNormalRibbonType;
import org.pokemmo.gameserver.game.pokemon.PokemonRibbonMask;
import org.pokemmo.gameserver.services.GameServerService.PokemonPositionChange;
import org.pokemmo.gameserver.services.character.CharacterService;
import org.pokemmo.gameserver.services.world.WorldService;
import org.pokemmo.gameserver.util.SnowflakeIdGenerator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.pokemmo.db.jooq.Tables.POKEMON;
import static org.pokemmo.db.jooq.Tables.OWNED_ITEM;

import static org.pokemmo.gameserver.services.pokemon.PokemonServiceStore.*;

/** Atomic held-item equip and removal operations. */
@Slf4j
final class PokemonItemService {
    private final Database database;

    PokemonItemService(Database database) {
        this.database = database;
    }

  public boolean updatePokemonItem(
          long characterId,
          long pokemonId,
          int containerId,
          short requestedItemIndexId,
          SnowflakeIdGenerator idGenerator) {
    if (characterId <= 0 || pokemonId <= 0
            || (containerId != PC_CONTAINER_ID && containerId != PARTY_CONTAINER_ID)
            || idGenerator == null) {
      return false;
    }

    short newItemIndexId = requestedItemIndexId > 0
            ? requestedItemIndexId : NO_HELD_ITEM;
    ItemData newItemData = newItemIndexId > 0
            ? ItemManager.getItemData(newItemIndexId) : null;
    if (newItemIndexId > 0
            && (newItemData == null || !newItemData.isItemCanGiveToPokemon() || ItemManager.isStoryBound(newItemIndexId))) {
      log.warn("拒绝不可携带或未知的道具: itemIndexId={}", newItemIndexId);
      return false;
    }

    try {
      return database.ctx().transactionResult(configuration -> {
        DSLContext transaction = DSL.using(configuration);
        PokemonRecord pokemon = transaction.selectFrom(POKEMON)
                .where(POKEMON.ID.eq(pokemonId))
                .and(POKEMON.TRAINER_ID.eq(characterId))
                .forUpdate()
                .fetchOne();
        if (pokemon == null || pokemon.getContainerId() == null
                || pokemon.getContainerId() != containerId) {
          log.warn("拒绝修改不属于请求容器的宝可梦道具: characterId={}, pokemonId={}, containerId={}",
                  characterId, pokemonId, containerId);
          return false;
        }

        short oldItemIndexId = pokemon.getItem() == null
                ? NO_HELD_ITEM : pokemon.getItem();
        if (oldItemIndexId == newItemIndexId) {
          return true;
        }

        OwnedItemRecord selectedItem = null;
        if (newItemIndexId > 0) {
          selectedItem = transaction.selectFrom(OWNED_ITEM)
                  .where(OWNED_ITEM.OWNER_ID.eq(characterId))
                  .and(OWNED_ITEM.INVENTORY_ID.eq((short) MAIN_INVENTORY_ID))
                  .and(OWNED_ITEM.ITEM_INDEX_ID.eq(newItemIndexId))
                  .and(OWNED_ITEM.COLOR_ID.eq((short) -1))
                  .and(OWNED_ITEM.ITEM_REGION_INDEX_ID.eq((short) -1))
                  .and(OWNED_ITEM.PVP_REWARD_LEVEL.eq((short) -1))
                  .and(OWNED_ITEM.PVP_REWARD_SEASON.eq((short) -1))
                  .and(OWNED_ITEM.ITEM_AMOUNT.gt((short) 0))
                  .orderBy(OWNED_ITEM.ITEM_ID.asc())
                  .forUpdate()
                  .fetchOne();
          if (selectedItem == null || selectedItem.getItemAmount() == null) {
            log.warn("拒绝装备不在主背包中的道具: characterId={}, pokemonId={}, itemIndexId={}",
                    characterId, pokemonId, newItemIndexId);
            return false;
          }
        }

        if (oldItemIndexId > 0) {
          returnHeldItem(transaction, characterId, oldItemIndexId, idGenerator);
        }
        if (selectedItem != null) {
          consumeOneItem(transaction, characterId, selectedItem);
        }

        int updated = transaction.update(POKEMON)
                .set(POKEMON.ITEM, newItemIndexId)
                .where(POKEMON.ID.eq(pokemonId))
                .and(POKEMON.TRAINER_ID.eq(characterId))
                .and(POKEMON.CONTAINER_ID.eq(containerId))
                .execute();
        if (updated != 1) {
          throw new IllegalStateException("宝可梦携带道具更新失败: pokemonId=" + pokemonId);
        }
        return true;
      });
    } catch (RuntimeException exception) {
      log.error("保存宝可梦携带道具失败: characterId={}, pokemonId={}, containerId={}, itemIndexId={}",
              characterId, pokemonId, containerId, requestedItemIndexId, exception);
      return false;
    }
  }

  private void consumeOneItem(DSLContext transaction, long characterId, OwnedItemRecord item) {
    int amount = item.getItemAmount();
    int updated;
    if (amount == 1) {
      updated = transaction.deleteFrom(OWNED_ITEM)
              .where(OWNED_ITEM.ITEM_ID.eq(item.getItemId()))
              .and(OWNED_ITEM.OWNER_ID.eq(characterId))
              .and(OWNED_ITEM.INVENTORY_ID.eq((short) MAIN_INVENTORY_ID))
              .execute();
    } else {
      updated = transaction.update(OWNED_ITEM)
              .set(OWNED_ITEM.ITEM_AMOUNT, (short) (amount - 1))
              .where(OWNED_ITEM.ITEM_ID.eq(item.getItemId()))
              .and(OWNED_ITEM.OWNER_ID.eq(characterId))
              .and(OWNED_ITEM.INVENTORY_ID.eq((short) MAIN_INVENTORY_ID))
              .execute();
    }
    if (updated != 1) {
      throw new IllegalStateException("主背包道具扣除失败: itemId=" + item.getItemId());
    }
  }

  private void returnHeldItem(
          DSLContext transaction,
          long characterId,
          short itemIndexId,
          SnowflakeIdGenerator idGenerator) {
    OwnedItemRecord existing = transaction.selectFrom(OWNED_ITEM)
            .where(OWNED_ITEM.OWNER_ID.eq(characterId))
            .and(OWNED_ITEM.INVENTORY_ID.eq((short) MAIN_INVENTORY_ID))
            .and(OWNED_ITEM.ITEM_INDEX_ID.eq(itemIndexId))
            .and(OWNED_ITEM.COLOR_ID.eq((short) -1))
            .and(OWNED_ITEM.ITEM_REGION_INDEX_ID.eq((short) -1))
            .and(OWNED_ITEM.PVP_REWARD_LEVEL.eq((short) -1))
            .and(OWNED_ITEM.PVP_REWARD_SEASON.eq((short) -1))
            .orderBy(OWNED_ITEM.ITEM_ID.asc())
            .forUpdate()
            .fetchOne();
    if (existing != null) {
      int amount = existing.getItemAmount() == null ? 0 : existing.getItemAmount();
      if (amount >= Short.MAX_VALUE) {
        throw new IllegalStateException("主背包道具堆叠已满: itemIndexId=" + itemIndexId);
      }
      if (transaction.update(OWNED_ITEM)
              .set(OWNED_ITEM.ITEM_AMOUNT, (short) (amount + 1))
              .where(OWNED_ITEM.ITEM_ID.eq(existing.getItemId()))
              .and(OWNED_ITEM.OWNER_ID.eq(characterId))
              .and(OWNED_ITEM.INVENTORY_ID.eq((short) MAIN_INVENTORY_ID))
              .execute() != 1) {
        throw new IllegalStateException("旧携带道具返还失败: itemId=" + existing.getItemId());
      }
      return;
    }

    long itemId = idGenerator.nextId();
    if (itemId <= 0 || transaction.fetchExists(
            transaction.selectOne().from(OWNED_ITEM)
                    .where(OWNED_ITEM.ITEM_ID.eq(itemId)))) {
      throw new IllegalStateException("无法生成新的道具 Object ID: itemIndexId=" + itemIndexId);
    }
    OwnedItemRecord returned = new OwnedItemRecord();
    returned.setItemId(itemId);
    returned.setOwnerId(characterId);
    returned.setItemIndexId(itemIndexId);
    returned.setItemAmount((short) 1);
    returned.setInventoryId((short) MAIN_INVENTORY_ID);
    returned.setColorId((short) -1);
    returned.setItemRegionIndexId((short) -1);
    returned.setPvpRewardLevel((short) -1);
    returned.setPvpRewardSeason((short) -1);
    if (transaction.insertInto(OWNED_ITEM).set(returned).execute() != 1) {
      throw new IllegalStateException("旧携带道具新记录写入失败: itemIndexId=" + itemIndexId);
    }
  }
}
