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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.pokemmo.db.jooq.Tables.POKEMON;
import static org.pokemmo.db.jooq.Tables.OWNED_ITEM;

import static org.pokemmo.gameserver.services.pokemon.PokemonServiceStore.*;

/** Queries, validates, and persists party/PC placement and sorting changes. */
@Slf4j
final class PokemonContainerService {
    private static final int PC_BOX_SIZE = 60;
    private static final int LEVEL_SORT_FLAG = 0x0001;

    private record PokemonSlot(int containerId, short position) {
    }

    private final Database database;
    private final WorldService worldService;
    private final CharacterService characterService;

    PokemonContainerService(Database database, WorldService worldService, CharacterService characterService) {
        this.database = database;
        this.worldService = worldService;
        this.characterService = characterService;
    }

  public List<PokemonData> getCharacterContainerPokemons(long characterId, ContainerRecord container) {
    Result<Record> containerPokemons = database.ctx()
        .select().from(POKEMON)
        .where(POKEMON.TRAINER_ID.eq(characterId))
        .and(POKEMON.CONTAINER_ID.eq(container.getId()))
        .fetch();
    return containerPokemons
            .map(record -> record.into(POKEMON))
            .stream().map(pokemonRecord ->
                    new PokemonData.Builder()
                            .setByRecord(pokemonRecord)
                            .build()
                    )
            .toList();
  }

public Optional<List<PokemonData>> changePokemonPositions(
          long characterId, List<PokemonPositionChange> changes) {
    if (characterId <= 0 || changes == null || changes.isEmpty() || changes.size() > 127) {
      log.warn("拒绝非法的宝可梦换位请求: characterId={}, changes={}", characterId, changes);
      return Optional.empty();
    }
    try {
      CharacterData character = characterService.getCharacter(characterId);
      if (character == null) {
        log.warn("拒绝宝可梦换位请求，角色不存在: characterId={}", characterId);
        return Optional.empty();
      }
      int pcCapacity = PokemonContainerType.PC.getSize()
              + Math.max(0, character.getPcBoxExpansionNumber()) * 60;
      for (PokemonPositionChange change : changes) {
        if (change == null || !isPersistentPokemonContainer(change.oldContainerId())
                || !isPersistentPokemonContainer(change.newContainerId())
                || !isValidPokemonPosition(change.oldContainerId(), change.oldPosition(), pcCapacity)
                || !isValidPokemonPosition(change.newContainerId(), change.newPosition(), pcCapacity)
                || (change.oldContainerId() == change.newContainerId()
                && change.oldPosition() == change.newPosition())) {
          log.warn("拒绝包含非法槽位的宝可梦换位请求: characterId={}, change={}, pcCapacity={}",
                  characterId, change, pcCapacity);
          return Optional.empty();
        }
      }

      return database.ctx().transactionResult(configuration -> {
        DSLContext transaction = DSL.using(configuration);
        List<PokemonRecord> records = transaction
                .selectFrom(POKEMON)
                .where(POKEMON.TRAINER_ID.eq(characterId))
                .and(POKEMON.CONTAINER_ID.in(PC_CONTAINER_ID, PARTY_CONTAINER_ID))
                .forUpdate()
                .fetch();

        Map<PokemonSlot, PokemonRecord> occupiedSlots = new HashMap<>();
        Map<Long, PokemonSlot> originalSlots = new HashMap<>();
        Map<Long, PokemonRecord> recordsById = new HashMap<>();
        for (PokemonRecord record : records) {
          PokemonSlot slot = new PokemonSlot(record.getContainerId(), record.getContainerPosition());
          if (occupiedSlots.put(slot, record) != null) {
            log.error("角色 {} 存在重复的宝可梦容器槽位: {}", characterId, slot);
            return Optional.<List<PokemonData>>empty();
          }
          originalSlots.put(record.getId(), slot);
          recordsById.put(record.getId(), record);
        }

        Set<PokemonSlot> sourceSlots = new HashSet<>();
        Set<PokemonSlot> destinationSlots = new HashSet<>();
        Map<Long, PokemonSlot> finalSlots = new HashMap<>(originalSlots);
        Set<Long> movedPokemonIds = new HashSet<>();
        for (PokemonPositionChange change : changes) {
          PokemonSlot sourceSlot = new PokemonSlot(change.oldContainerId(), change.oldPosition());
          PokemonSlot destinationSlot = new PokemonSlot(change.newContainerId(), change.newPosition());
          PokemonRecord sourcePokemon = occupiedSlots.get(sourceSlot);
          if (sourcePokemon == null) {
            log.warn("拒绝宝可梦换位请求，数据库中不存在源槽位: characterId={}, source={}, destination={}, occupiedSlots={}",
                    characterId, sourceSlot, destinationSlot, occupiedSlots.keySet());
            return Optional.<List<PokemonData>>empty();
          }
          if (!sourceSlots.add(sourceSlot)
                  || !destinationSlots.add(destinationSlot)
                  || !movedPokemonIds.add(sourcePokemon.getId())) {
            log.warn("拒绝包含重复源或目标的宝可梦换位请求: characterId={}, changes={}",
                    characterId, changes);
            return Optional.<List<PokemonData>>empty();
          }
          finalSlots.put(sourcePokemon.getId(), destinationSlot);
        }

        // A destination occupied by a Pokemon outside this request is swapped
        // back into the corresponding source slot. Pokemon participating in
        // the same batch keep their own requested destinations instead.
        for (PokemonPositionChange change : changes) {
          PokemonSlot sourceSlot = new PokemonSlot(change.oldContainerId(), change.oldPosition());
          PokemonSlot destinationSlot = new PokemonSlot(change.newContainerId(), change.newPosition());
          PokemonRecord destinationPokemon = occupiedSlots.get(destinationSlot);
          if (destinationPokemon != null && !movedPokemonIds.contains(destinationPokemon.getId())) {
            finalSlots.put(destinationPokemon.getId(), sourceSlot);
          }
        }

        Set<PokemonSlot> finalOccupiedSlots = new HashSet<>();
        for (Map.Entry<Long, PokemonSlot> entry : finalSlots.entrySet()) {
          if (!finalOccupiedSlots.add(entry.getValue())) {
            log.warn("拒绝会生成重复最终槽位的宝可梦换位请求: characterId={}, slot={}, changes={}",
                    characterId, entry.getValue(), changes);
            return Optional.<List<PokemonData>>empty();
          }
        }
        if (finalOccupiedSlots.stream().anyMatch(slot ->
                !isValidPokemonPosition(slot.containerId(), slot.position(), pcCapacity))) {
          log.warn("拒绝会生成越界最终槽位的宝可梦换位请求: characterId={}, changes={}",
                  characterId, changes);
          return Optional.<List<PokemonData>>empty();
        }

        long originalPartyPokemonAmount = originalSlots.values().stream()
                .filter(slot -> slot.containerId() == PARTY_CONTAINER_ID)
                .count();
        long finalPartyPokemonAmount = finalOccupiedSlots.stream()
                .filter(slot -> slot.containerId() == PARTY_CONTAINER_ID)
                .count();
        if (originalPartyPokemonAmount > 0 && finalPartyPokemonAmount == 0) {
          log.warn("拒绝会清空 PARTY 的宝可梦换位请求: characterId={}, changes={}",
                  characterId, changes);
          return Optional.<List<PokemonData>>empty();
        }

        List<PokemonData> changedPokemons = new ArrayList<>();
        for (Map.Entry<Long, PokemonSlot> entry : finalSlots.entrySet()) {
          PokemonRecord record = recordsById.get(entry.getKey());
          if (record == null) {
            log.error("宝可梦换位的内部记录丢失: characterId={}, pokemonId={}",
                    characterId, entry.getKey());
            return Optional.<List<PokemonData>>empty();
          }
          PokemonSlot newSlot = entry.getValue();
          if (newSlot.equals(originalSlots.get(record.getId()))) {
            continue;
          }

          int updated = transaction
                  .update(POKEMON)
                  .set(POKEMON.CONTAINER_ID, newSlot.containerId())
                  .set(POKEMON.CONTAINER_POSITION, newSlot.position())
                  .where(POKEMON.ID.eq(record.getId()))
                  .and(POKEMON.TRAINER_ID.eq(characterId))
                  .execute();
          if (updated != 1) {
            throw new IllegalStateException("Failed to persist Pokemon position change for " + record.getId());
          }

          record.setContainerId(newSlot.containerId());
          record.setContainerPosition(newSlot.position());
          changedPokemons.add(new PokemonData.Builder().setByRecord(record).build());
        }
        return Optional.of(changedPokemons);
      });
    } catch (RuntimeException exception) {
      log.error("角色 {} 的宝可梦换位事务失败", characterId, exception);
      return Optional.empty();
    }
  }

  /**
   * Applies the client PC sort request to the selected box or party container.
   * The current client contract is verified for the level flag (0x0001); the
   * remaining sort flags are rejected until their ordering semantics are
   * confirmed from a packet trace.
   */
  public Optional<List<PokemonData>> sortPokemon(
          long characterId, int containerType, int[] containerIndexes, int sortFlags) {
    if (characterId <= 0 || containerIndexes == null || containerIndexes.length == 0
            || containerIndexes.length > 127
            || (sortFlags & LEVEL_SORT_FLAG) == 0
            || (sortFlags & ~LEVEL_SORT_FLAG) != 0) {
      log.warn("拒绝非法的宝可梦排序请求: characterId={}, containerType={}, containerIndexes={}, sortFlags={}",
              characterId, containerType, containerIndexes, sortFlags);
      return Optional.empty();
    }

    CharacterData character = characterService.getCharacter(characterId);
    if (character == null) {
      log.warn("拒绝宝可梦排序请求，角色不存在: characterId={}", characterId);
      return Optional.empty();
    }

    int containerId;
    int slotSize;
    int availableBoxAmount;
    if (containerType == PokemonContainerType.PC.getType()) {
      containerId = PC_CONTAINER_ID;
      slotSize = PC_BOX_SIZE;
      int expansionAmount = Math.max(0, character.getPcBoxExpansionNumber());
      availableBoxAmount = PokemonContainerType.PC.getSize() / PC_BOX_SIZE + expansionAmount;
    } else if (containerType == PokemonContainerType.PARTY.getType()) {
      containerId = PARTY_CONTAINER_ID;
      slotSize = PokemonContainerType.PARTY.getSize();
      availableBoxAmount = 1;
    } else {
      log.warn("拒绝未知容器的宝可梦排序请求: characterId={}, containerType={}",
              characterId, containerType);
      return Optional.empty();
    }

    Set<Integer> requestedIndexes = new HashSet<>();
    for (int containerIndex : containerIndexes) {
      if (containerIndex < 0 || containerIndex >= availableBoxAmount
              || !requestedIndexes.add(containerIndex)) {
        log.warn("拒绝非法或重复的宝可梦排序容器索引: characterId={}, containerType={}, index={}",
                characterId, containerType, containerIndex);
        return Optional.empty();
      }
    }

    try {
      return database.ctx().transactionResult(configuration -> {
        DSLContext transaction = DSL.using(configuration);
        List<PokemonRecord> records = transaction
                .selectFrom(POKEMON)
                .where(POKEMON.TRAINER_ID.eq(characterId))
                .and(POKEMON.CONTAINER_ID.eq(containerId))
                .forUpdate()
                .fetch();

        Map<Integer, List<PokemonRecord>> recordsByContainer = new HashMap<>();
        Map<Integer, Set<Short>> occupiedPositions = new HashMap<>();
        for (PokemonRecord record : records) {
          int position = record.getContainerPosition();
          int containerIndex = position / slotSize;
          if (!requestedIndexes.contains(containerIndex)) {
            continue;
          }
          int containerStart = containerIndex * slotSize;
          if (position < containerStart || position >= containerStart + slotSize) {
            log.warn("角色 {} 的排序目标槽位越界: containerIndex={}, position={}",
                    characterId, containerIndex, position);
            return Optional.<List<PokemonData>>empty();
          }
          if (!occupiedPositions
                  .computeIfAbsent(containerIndex, ignored -> new HashSet<>())
                  .add(record.getContainerPosition())) {
            log.error("角色 {} 的排序目标容器存在重复槽位: containerIndex={}, position={}",
                    characterId, containerIndex, position);
            return Optional.<List<PokemonData>>empty();
          }
          recordsByContainer
                  .computeIfAbsent(containerIndex, ignored -> new ArrayList<>())
                  .add(record);
        }

        List<PokemonData> changedPokemons = new ArrayList<>();
        Comparator<PokemonRecord> levelComparator = Comparator
                .comparingInt((PokemonRecord record) -> record.getLevelValue() == null
                        ? 0 : record.getLevelValue().intValue())
                .thenComparingInt(PokemonRecord::getContainerPosition)
                .thenComparingLong(PokemonRecord::getId);
        for (int containerIndex : requestedIndexes) {
          List<PokemonRecord> containerRecords = recordsByContainer.get(containerIndex);
          if (containerRecords == null || containerRecords.isEmpty()) {
            continue;
          }
          containerRecords.sort(levelComparator);
          int containerStart = containerIndex * slotSize;
          for (int index = 0; index < containerRecords.size(); index++) {
            PokemonRecord record = containerRecords.get(index);
            short newPosition = (short) (containerStart + index);
            if (record.getContainerPosition() == newPosition) {
              continue;
            }
            int updated = transaction
                    .update(POKEMON)
                    .set(POKEMON.CONTAINER_POSITION, newPosition)
                    .where(POKEMON.ID.eq(record.getId()))
                    .and(POKEMON.TRAINER_ID.eq(characterId))
                    .and(POKEMON.CONTAINER_ID.eq(containerId))
                    .execute();
            if (updated != 1) {
              throw new IllegalStateException("Failed to persist Pokemon sort for " + record.getId());
            }
            record.setContainerPosition(newPosition);
            changedPokemons.add(new PokemonData.Builder().setByRecord(record).build());
          }
        }
        return Optional.of(changedPokemons);
      });
    } catch (RuntimeException exception) {
      log.error("角色 {} 的宝可梦排序事务失败", characterId, exception);
      return Optional.empty();
    }
  }

  private boolean isPersistentPokemonContainer(int containerId) {
    return containerId == PC_CONTAINER_ID || containerId == PARTY_CONTAINER_ID;
  }

  private boolean isValidPokemonPosition(int containerId, short position, int pcCapacity) {
    if (position < 0) {
      return false;
    }
    if (containerId == PARTY_CONTAINER_ID) {
      return position < PokemonContainerType.PARTY.getSize();
    }
    return containerId == PC_CONTAINER_ID && position < pcCapacity;
  }

  public short findNextFreePartyPosition(long characterId) {
    Set<Short> usedPositions = new HashSet<>();
    for (PokemonData pokemon : getCharacterContainerPokemons(
            characterId,
            worldService.getContainerByType(PokemonContainerType.PARTY))) {
      usedPositions.add(pokemon.getContainerPosition());
    }
    for (short position = 0; position < PokemonContainerType.PARTY.getSize(); position++) {
      if (!usedPositions.contains(position)) {
        return position;
      }
    }
    return -1;
  }

  public short findNextFreePcBoxPosition(long characterId) {
    if (characterId <= 0) {
      return -1;
    }

    CharacterData character = characterService.getCharacter(characterId);
    if (character == null) {
      return -1;
    }

    int expansionAmount = Math.max(0, character.getPcBoxExpansionNumber());
    int capacity = Math.min(
            Short.MAX_VALUE + 1,
            PokemonContainerType.PC.getSize() + expansionAmount * 60);
    ContainerRecord pcContainer = worldService.getContainerByType(PokemonContainerType.PC);
    if (pcContainer == null || pcContainer.getId() == null) {
      return -1;
    }

    Set<Short> usedPositions = new HashSet<>();
    for (PokemonData pokemon : getCharacterContainerPokemons(
            characterId,
            pcContainer)) {
      short position = pokemon.getContainerPosition();
      if (position >= 0) {
        usedPositions.add(position);
      }
    }

    for (int position = 0; position < capacity; position++) {
      short slot = (short) position;
      if (!usedPositions.contains(slot)) {
        return slot;
      }
    }
    return -1;
  }
}
