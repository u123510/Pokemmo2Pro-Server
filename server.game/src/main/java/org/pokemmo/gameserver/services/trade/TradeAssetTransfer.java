package org.pokemmo.gameserver.services.trade;

import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.Result;
import org.pokemmo.db.Database;
import org.pokemmo.db.jooq.tables.records.OwnedItemRecord;
import org.pokemmo.db.jooq.tables.records.PokemonRecord;
import org.pokemmo.gameserver.game.container.PokemonContainerType;
import org.pokemmo.gameserver.game.trade.TradeItemOffer;
import org.pokemmo.gameserver.services.GameServerService;
import org.pokemmo.gameserver.util.SnowflakeIdGenerator;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.pokemmo.db.jooq.Tables.CHARACTER;
import static org.pokemmo.db.jooq.Tables.CONTAINER;
import static org.pokemmo.db.jooq.Tables.INVENTORY;
import static org.pokemmo.db.jooq.Tables.OWNED_ITEM;
import static org.pokemmo.db.jooq.Tables.POKEMON;

/** Transaction boundary for transferring Pokemon, items, and money. */
@Slf4j
public final class TradeAssetTransfer {
    private static final int BASE_PC_SIZE = 660;
    private static final int PC_BOX_EXPANSION_SIZE = 60;

    private final Database database;
    private final TradeValidationService validationService;
    private final TradeResultMapper resultMapper;

    private record PokemonPlacement(int containerId, short position) {
    }

    TradeAssetTransfer(Database database, TradeValidationService validationService,
                       TradeResultMapper resultMapper) {
        this.database = database;
        this.validationService = validationService;
        this.resultMapper = resultMapper;
    }

    public GameServerService.TradeCompletionResult transfer(
            long firstId, long secondId, int firstMoney, int secondMoney,
            List<Long> firstPokemonIds, List<Long> secondPokemonIds,
            List<TradeItemOffer> firstItems, List<TradeItemOffer> secondItems,
            SnowflakeIdGenerator idGenerator) {
        String rejectionReason = validationService.requestRejectionReason(
                firstId, secondId, firstMoney, secondMoney,
                firstPokemonIds, secondPokemonIds, firstItems, secondItems);
        if (rejectionReason != null) {
            log.warn("交易参数预检查失败: firstId={}, secondId={}, firstMoney={}, secondMoney={}, "
                    + "firstPokemonIds={}, secondPokemonIds={}, firstItems={}, secondItems={}, reason={}",
                    firstId, secondId, firstMoney, secondMoney,
                    firstPokemonIds, secondPokemonIds, firstItems, secondItems, rejectionReason);
            return GameServerService.TradeCompletionResult.rejected();
        }
        try {
            return database.ctx().transactionResult(configuration -> {
                DSLContext tx = org.jooq.impl.DSL.using(configuration);
                var characters = tx.selectFrom(CHARACTER)
                        .where(CHARACTER.ID.in(firstId, secondId))
                        .orderBy(CHARACTER.ID.asc())
                        .forUpdate()
                        .fetch();
                if (characters.size() != 2) {
                    throw new TradeRejectedException("角色记录数量不是 2: actual=" + characters.size());
                }
                var firstCharacter = characters.stream()
                        .filter(record -> firstId == record.getId()).findFirst().orElse(null);
                var secondCharacter = characters.stream()
                        .filter(record -> secondId == record.getId()).findFirst().orElse(null);
                if (firstCharacter == null || secondCharacter == null
                        || firstCharacter.getMoney() == null || secondCharacter.getMoney() == null
                        || firstCharacter.getMoney() < 0 || secondCharacter.getMoney() < 0
                        || firstMoney > firstCharacter.getMoney() || secondMoney > secondCharacter.getMoney()) {
                    throw new TradeRejectedException("角色金钱校验失败");
                }

                var containers = tx.selectFrom(CONTAINER)
                        .where(CONTAINER.NAME.in("pc", "party"))
                        .orderBy(CONTAINER.ID.asc())
                        .forUpdate()
                        .fetch();
                var pcContainer = containers.stream()
                        .filter(record -> "pc".equals(record.getName()))
                        .findFirst().orElse(null);
                var partyContainer = containers.stream()
                        .filter(record -> "party".equals(record.getName()))
                        .findFirst().orElse(null);
                if (pcContainer == null || partyContainer == null
                        || pcContainer.getId() == null || partyContainer.getId() == null) {
                    throw new TradeRejectedException("PC/PARTY 容器记录缺失");
                }
                int pcContainerId = pcContainer.getId();
                int partyContainerId = partyContainer.getId();

                Result<PokemonRecord> firstPokemons = tx.selectFrom(POKEMON)
                        .where(POKEMON.ID.in(firstPokemonIds))
                        .and(POKEMON.TRAINER_ID.eq(firstId))
                        .and(POKEMON.CONTAINER_ID.in(pcContainerId, partyContainerId))
                        .forUpdate().fetch();
                Result<PokemonRecord> secondPokemons = tx.selectFrom(POKEMON)
                        .where(POKEMON.ID.in(secondPokemonIds))
                        .and(POKEMON.TRAINER_ID.eq(secondId))
                        .and(POKEMON.CONTAINER_ID.in(pcContainerId, partyContainerId))
                        .forUpdate().fetch();
                if (firstPokemons.size() != firstPokemonIds.size()
                        || secondPokemons.size() != secondPokemonIds.size()) {
                    throw new TradeRejectedException("交易宝可梦不存在、归属不匹配或不在 PC/PARTY: "
                            + "firstExpected=" + firstPokemonIds.size() + ", firstActual=" + firstPokemons.size()
                            + ", secondExpected=" + secondPokemonIds.size() + ", secondActual=" + secondPokemons.size()
                            + ", firstIds=" + firstPokemonIds + ", secondIds=" + secondPokemonIds);
                }
                if (!validationService.partyWouldRemain(tx, firstId, firstPokemonIds, partyContainerId)
                        || !validationService.partyWouldRemain(tx, secondId, secondPokemonIds, partyContainerId)) {
                    throw new TradeRejectedException("交易后 PARTY 为空");
                }

                var inventory = tx.selectFrom(INVENTORY)
                        .where(INVENTORY.NAME.eq("inventory"))
                        .fetchOne();
                if (inventory == null || inventory.getId() == null) {
                    throw new TradeRejectedException("主背包 inventory 记录缺失");
                }
                List<OwnedItemRecord> firstLockedItems = validationService.lockItems(
                        tx, firstId, firstItems, inventory.getId());
                List<OwnedItemRecord> secondLockedItems = validationService.lockItems(
                        tx, secondId, secondItems, inventory.getId());
                if (firstLockedItems == null || secondLockedItems == null) {
                    throw new TradeRejectedException("交易道具不存在、归属/数量/颜色不匹配或不可交易: "
                            + "firstItems=" + firstItems + ", secondItems=" + secondItems);
                }

                List<PokemonPlacement> firstReceivedPlacements = nextReceivedPositions(
                        tx, firstId, firstPokemonIds, secondPokemonIds.size(),
                        pcCapacity(firstCharacter.getPcBoxExpansionNumber()), pcContainerId, partyContainerId);
                List<PokemonPlacement> secondReceivedPlacements = nextReceivedPositions(
                        tx, secondId, secondPokemonIds, firstPokemonIds.size(),
                        pcCapacity(secondCharacter.getPcBoxExpansionNumber()), pcContainerId, partyContainerId);
                if (secondPokemonIds.size() != firstReceivedPlacements.size()
                        || firstPokemonIds.size() != secondReceivedPlacements.size()) {
                    throw new TradeRejectedException("接收方 PARTY/PC 没有足够空槽: "
                            + "firstPositions=" + firstReceivedPlacements.size()
                            + ", secondPositions=" + secondReceivedPlacements.size());
                }
                if (!movePokemons(tx, firstPokemonIds, firstId, secondId,
                        secondReceivedPlacements, firstPokemons)
                        || !movePokemons(tx, secondPokemonIds, secondId, firstId,
                        firstReceivedPlacements, secondPokemons)
                        || !transferItems(tx, firstItems, firstLockedItems, firstId, secondId,
                        inventory.getId(), idGenerator)
                        || !transferItems(tx, secondItems, secondLockedItems, secondId, firstId,
                        inventory.getId(), idGenerator)) {
                    throw new TradeRejectedException("宝可梦或道具转移 SQL 更新失败");
                }
                long firstRemainingMoney = (long) firstCharacter.getMoney() - firstMoney + secondMoney;
                long secondRemainingMoney = (long) secondCharacter.getMoney() - secondMoney + firstMoney;
                if (firstRemainingMoney < 0 || firstRemainingMoney > Integer.MAX_VALUE
                        || secondRemainingMoney < 0 || secondRemainingMoney > Integer.MAX_VALUE) {
                    throw new TradeRejectedException("交易后金钱溢出");
                }
                if (tx.update(CHARACTER).set(CHARACTER.MONEY, (int) firstRemainingMoney)
                        .where(CHARACTER.ID.eq(firstId)).execute() == 1
                        && tx.update(CHARACTER).set(CHARACTER.MONEY, (int) secondRemainingMoney)
                        .where(CHARACTER.ID.eq(secondId)).execute() == 1) {
                    return resultMapper.completed(
                            secondPokemonIds, secondPokemons, firstPokemonIds, firstPokemons);
                }
                throw new TradeRejectedException("角色金钱更新行数不是 1");
            });
        } catch (TradeRejectedException exception) {
            log.warn("交易被拒绝: firstId={}, secondId={}, firstMoney={}, secondMoney={}, "
                            + "firstPokemonIds={}, secondPokemonIds={}, firstItems={}, secondItems={}, reason={}",
                    firstId, secondId, firstMoney, secondMoney,
                    firstPokemonIds, secondPokemonIds, firstItems, secondItems, exception.getMessage());
            return GameServerService.TradeCompletionResult.rejected();
        } catch (RuntimeException exception) {
            log.error("交易提交事务失败: firstId={}, secondId={}, firstMoney={}, secondMoney={}, "
                            + "firstPokemonIds={}, secondPokemonIds={}, firstItems={}, secondItems={}",
                    firstId, secondId, firstMoney, secondMoney,
                    firstPokemonIds, secondPokemonIds, firstItems, secondItems, exception);
            return GameServerService.TradeCompletionResult.rejected();
        }
    }

    private boolean transferItems(DSLContext tx, List<TradeItemOffer> offers,
                                  List<OwnedItemRecord> lockedItems, long ownerId, long targetId,
                                  short inventoryId, SnowflakeIdGenerator idGenerator) {
        if (offers.size() != lockedItems.size()) {
            return false;
        }
        for (int index = 0; index < offers.size(); index++) {
            TradeItemOffer offer = offers.get(index);
            OwnedItemRecord item = lockedItems.get(index);
            if (offer == null || item == null || item.getItemAmount() == null
                    || offer.amount() <= 0 || offer.amount() > item.getItemAmount()) {
                return false;
            }
            if (offer.amount() == item.getItemAmount()) {
                if (tx.update(OWNED_ITEM).set(OWNED_ITEM.OWNER_ID, targetId)
                        .where(OWNED_ITEM.ITEM_ID.eq(offer.itemId()))
                        .and(OWNED_ITEM.OWNER_ID.eq(ownerId))
                        .and(OWNED_ITEM.INVENTORY_ID.eq(inventoryId)).execute() != 1) {
                    return false;
                }
                continue;
            }
            if (idGenerator == null) {
                return false;
            }
            if (tx.update(OWNED_ITEM)
                    .set(OWNED_ITEM.ITEM_AMOUNT, (short) (item.getItemAmount() - offer.amount()))
                    .where(OWNED_ITEM.ITEM_ID.eq(offer.itemId()))
                    .and(OWNED_ITEM.OWNER_ID.eq(ownerId))
                    .and(OWNED_ITEM.INVENTORY_ID.eq(inventoryId)).execute() != 1) {
                return false;
            }
            OwnedItemRecord split = new OwnedItemRecord();
            split.setItemId(idGenerator.nextId());
            split.setOwnerId(targetId);
            split.setItemIndexId(item.getItemIndexId());
            split.setItemAmount(offer.amount());
            split.setInventoryId(item.getInventoryId());
            split.setColorId(item.getColorId());
            split.setItemRegionIndexId(item.getItemRegionIndexId());
            split.setPvpRewardLevel(item.getPvpRewardLevel());
            split.setPvpRewardSeason(item.getPvpRewardSeason());
            split.setPvpRewardTime(item.getPvpRewardTime());
            if (tx.insertInto(OWNED_ITEM).set(split).execute() != 1) {
                return false;
            }
        }
        return true;
    }

    private boolean movePokemons(DSLContext tx, List<Long> ids, long ownerId, long targetId,
                                 List<PokemonPlacement> placements,
                                 Result<PokemonRecord> sourcePokemons) {
        if (ids.size() != placements.size()) {
            return false;
        }
        for (int index = 0; index < ids.size(); index++) {
            long pokemonId = ids.get(index);
            PokemonPlacement placement = placements.get(index);
            if (tx.update(POKEMON).set(POKEMON.TRAINER_ID, targetId)
                    .set(POKEMON.CONTAINER_ID, placement.containerId())
                    .set(POKEMON.CONTAINER_POSITION, placement.position())
                    .where(POKEMON.ID.eq(pokemonId)).and(POKEMON.TRAINER_ID.eq(ownerId)).execute() != 1) {
                return false;
            }
            PokemonRecord source = sourcePokemons.stream()
                    .filter(record -> record.getId() == pokemonId)
                    .findFirst().orElse(null);
            if (source == null) {
                return false;
            }
            source.setTrainerId(targetId);
            source.setContainerId(placement.containerId());
            source.setContainerPosition(placement.position());
        }
        return true;
    }

    private List<PokemonPlacement> nextReceivedPositions(
            DSLContext tx, long ownerId, List<Long> outgoingIds, int requiredPositions,
            int pcCapacity, int pcContainerId, int partyContainerId) {
        if (requiredPositions == 0) {
            return List.of();
        }
        Set<Integer> usedParty = usedPositions(tx, ownerId, outgoingIds, partyContainerId);
        List<PokemonPlacement> placements = new ArrayList<>(requiredPositions);
        for (int position = 0; position < PokemonContainerType.PARTY.getSize()
                && placements.size() < requiredPositions; position++) {
            if (!usedParty.contains(position)) {
                placements.add(new PokemonPlacement(partyContainerId, (short) position));
            }
        }
        Set<Integer> usedPc = usedPositions(tx, ownerId, outgoingIds, pcContainerId);
        for (int position = 0; position < pcCapacity && placements.size() < requiredPositions; position++) {
            if (!usedPc.contains(position)) {
                placements.add(new PokemonPlacement(pcContainerId, (short) position));
            }
        }
        return placements;
    }

    private Set<Integer> usedPositions(DSLContext tx, long ownerId, List<Long> outgoingIds,
                                       int containerId) {
        Set<Integer> positions = new HashSet<>();
        tx.select(POKEMON.CONTAINER_POSITION).from(POKEMON)
                .where(POKEMON.TRAINER_ID.eq(ownerId))
                .and(POKEMON.CONTAINER_ID.eq(containerId))
                .and(outgoingIds.isEmpty() ? org.jooq.impl.DSL.trueCondition() : POKEMON.ID.notIn(outgoingIds))
                .fetch(POKEMON.CONTAINER_POSITION)
                .forEach(position -> positions.add(position.intValue()));
        return positions;
    }

    private int pcCapacity(Short expansionNumber) {
        int expansion = expansionNumber == null ? 0 : Math.max(0, expansionNumber);
        return BASE_PC_SIZE + expansion * PC_BOX_EXPANSION_SIZE;
    }
}
