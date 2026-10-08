package org.pokemmo.gameserver.services.pokemon;

import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.pokemmo.db.Database;
import org.pokemmo.db.jooq.tables.records.InventoryRecord;
import org.pokemmo.db.jooq.tables.records.OwnedItemRecord;
import org.pokemmo.db.jooq.tables.records.PokemonRecord;
import org.pokemmo.gameserver.game.container.PokemonContainerType;
import org.pokemmo.gameserver.game.item.ItemData;
import org.pokemmo.gameserver.game.item.ItemManager;
import org.pokemmo.gameserver.game.pokemon.PokemonData;
import org.pokemmo.gameserver.game.pokemon.CaptureSpeciesDataManager;
import org.pokemmo.gameserver.game.pokemon.PokemonStatusType;
import org.pokemmo.gameserver.services.character.CharacterService;

import java.util.HashSet;
import java.util.BitSet;
import java.util.Set;

import static org.pokemmo.db.jooq.Tables.INVENTORY;
import static org.pokemmo.db.jooq.Tables.OWNED_ITEM;
import static org.pokemmo.db.jooq.Tables.POKEMON;
import static org.pokemmo.db.jooq.Tables.POKEMON_DEX;
import static org.pokemmo.db.jooq.Tables.CHARACTER;
import static org.pokemmo.gameserver.services.pokemon.PokemonServiceStore.PARTY_CONTAINER_ID;
import static org.pokemmo.gameserver.services.pokemon.PokemonServiceStore.PC_CONTAINER_ID;

/** Persists a wild Pokemon capture and consumes exactly one capture ball. */
@Slf4j
public final class PokemonCaptureService {
    public record ConsumedBall(long itemId, byte inventoryId, short remainingAmount) {
    }

    public record CaptureResult(
            boolean success,
            ConsumedBall consumedBall,
            boolean safariBallConsumed,
            PokemonData pokemon,
            PokemonContainerType container,
            short position) {
        public static CaptureResult rejected() {
            return new CaptureResult(false, null, false, null, null, (short) -1);
        }

        public static CaptureResult failed(ConsumedBall consumedBall) {
            return new CaptureResult(false, consumedBall, false, null, null, (short) -1);
        }

        public static CaptureResult failedSafari() {
            return new CaptureResult(false, null, true, null, null, (short) -1);
        }

        public boolean ballConsumed() {
            return consumedBall != null || safariBallConsumed;
        }
    }

    private final Database database;
    private final CharacterService characterService;

    public PokemonCaptureService(Database database, CharacterService characterService) {
        this.database = database;
        this.characterService = characterService;
    }

    public CaptureResult capture(long characterId, PokemonData wildPokemon,
                                 short ballItemIndexId, boolean caught) {
        if (characterId <= 0 || wildPokemon == null || wildPokemon.getPokemonId() <= 0
                || wildPokemon.getTrainerId() != 0) {
            return CaptureResult.rejected();
        }
        ItemData ball = ItemManager.getItemData(ballItemIndexId);
        if (ball == null || !ball.isCaptureBall()) {
            return CaptureResult.rejected();
        }

        try {
            return database.ctx().transactionResult(configuration -> {
                DSLContext transaction = org.jooq.impl.DSL.using(configuration);
                InventoryRecord inventory = transaction.selectFrom(INVENTORY)
                        .where(INVENTORY.NAME.eq("inventory"))
                        .fetchOne();
                if (inventory == null) {
                    return CaptureResult.rejected();
                }
                OwnedItemRecord ownedBall = transaction.selectFrom(OWNED_ITEM)
                        .where(OWNED_ITEM.OWNER_ID.eq(characterId))
                        .and(OWNED_ITEM.INVENTORY_ID.eq(inventory.getId()))
                        .and(OWNED_ITEM.ITEM_INDEX_ID.eq(ballItemIndexId))
                        .forUpdate()
                        .fetchAny();
                if (ownedBall == null || ownedBall.getItemAmount() == null
                        || ownedBall.getItemAmount() <= 0) {
                    return CaptureResult.rejected();
                }

                PokemonContainerType container = null;
                short position = -1;
                if (caught) {
                    container = chooseContainer(transaction, characterId);
                    if (container == null) {
                        return CaptureResult.rejected();
                    }
                    position = findFreePosition(transaction, characterId, container);
                    if (position < 0) {
                        return CaptureResult.rejected();
                    }
                }

                int remaining = ownedBall.getItemAmount() - 1;
                ConsumedBall consumedBall = new ConsumedBall(
                        ownedBall.getItemId(), inventory.getId().byteValue(), (short) remaining);
                if (remaining == 0) {
                    int deleted = transaction.deleteFrom(OWNED_ITEM)
                            .where(OWNED_ITEM.ITEM_ID.eq(ownedBall.getItemId()))
                            .and(OWNED_ITEM.OWNER_ID.eq(characterId))
                            .and(OWNED_ITEM.INVENTORY_ID.eq(inventory.getId()))
                            .execute();
                    if (deleted != 1) {
                        throw new IllegalStateException("捕获球扣除失败");
                    }
                } else {
                    int updated = transaction.update(OWNED_ITEM)
                            .set(OWNED_ITEM.ITEM_AMOUNT, (short) remaining)
                            .where(OWNED_ITEM.ITEM_ID.eq(ownedBall.getItemId()))
                            .and(OWNED_ITEM.OWNER_ID.eq(characterId))
                            .and(OWNED_ITEM.INVENTORY_ID.eq(inventory.getId()))
                            .execute();
                    if (updated != 1) {
                        throw new IllegalStateException("捕获球数量更新失败");
                    }
                }
                if (!caught) {
                    return CaptureResult.failed(consumedBall);
                }

                wildPokemon.setTrainerId(characterId);
                wildPokemon.setOriginalTrainerId(characterId);
                wildPokemon.setContainerId(container == PokemonContainerType.PARTY
                        ? PARTY_CONTAINER_ID : PC_CONTAINER_ID);
                wildPokemon.setContainerPosition(position);
                wildPokemon.setBallType(ball.getItemBallType());
                wildPokemon.setItem((short) -1);
                wildPokemon.setPokemonStatus(PokemonStatusType.NORMAL);
                int ballType = Byte.toUnsignedInt(ball.getItemBallType());
                int baseHappiness = CaptureSpeciesDataManager.lookup(
                        Short.toUnsignedInt(wildPokemon.getPokemonIndexId())).baseHappiness();
                wildPokemon.setFriendValue((short) (ballType == 21 ? 200 : baseHappiness));
                if (ballType == 13) {
                    wildPokemon.setCurrentHp(wildPokemon.getMaxHp());
                }
                if (wildPokemon.getOtName() == null || wildPokemon.getOtName().isBlank()) {
                    var character = characterService.getCharacter(characterId);
                    wildPokemon.setOtName(character == null || character.getPlayerEntity() == null
                            ? "" : character.getPlayerEntity().getEntityName());
                }
                PokemonRecord record = wildPokemon.toPokemonRecord();
                if (transaction.insertInto(POKEMON).set(record).execute() != 1) {
                    throw new IllegalStateException("捕获宝可梦写入失败");
                }
                updateCaughtDex(transaction, characterId,
                        Short.toUnsignedInt(wildPokemon.getPokemonIndexId()));
                return new CaptureResult(true, consumedBall, false, wildPokemon, container, position);
            });
        } catch (RuntimeException exception) {
            log.error("捕获野生宝可梦事务失败: characterId={}, pokemonId={}, ball={}",
                    characterId, wildPokemon.getPokemonId(), ballItemIndexId, exception);
            return CaptureResult.rejected();
        }
    }

    public CaptureResult captureSafari(long characterId, PokemonData wildPokemon,
                                      short ballItemIndexId, boolean caught) {
        if (characterId <= 0 || wildPokemon == null || wildPokemon.getPokemonId() <= 0
                || wildPokemon.getTrainerId() != 0) {
            return CaptureResult.rejected();
        }
        ItemData ball = ItemManager.getItemData(ballItemIndexId);
        if (ball == null || !ball.isCaptureBall()) {
            return CaptureResult.rejected();
        }
        try {
            return database.ctx().transactionResult(configuration -> {
                DSLContext transaction = org.jooq.impl.DSL.using(configuration);
                var character = transaction.selectFrom(CHARACTER)
                        .where(CHARACTER.ID.eq(characterId))
                        .forUpdate()
                        .fetchOne();
                if (character == null || character.getSafariBallAmount() == null
                        || character.getSafariBallAmount() <= 0) {
                    return CaptureResult.rejected();
                }
                PokemonContainerType container = null;
                short position = -1;
                if (caught) {
                    container = chooseContainer(transaction, characterId);
                    if (container == null) return CaptureResult.rejected();
                    position = findFreePosition(transaction, characterId, container);
                    if (position < 0) return CaptureResult.rejected();
                }
                int updated = transaction.update(CHARACTER)
                        .set(CHARACTER.SAFARI_BALL_AMOUNT,
                                (short) (character.getSafariBallAmount() - 1))
                        .where(CHARACTER.ID.eq(characterId))
                        .and(CHARACTER.SAFARI_BALL_AMOUNT.eq(character.getSafariBallAmount()))
                        .execute();
                if (updated != 1) {
                    throw new IllegalStateException("狩猎球扣除失败");
                }
                if (!caught) return CaptureResult.failedSafari();

                wildPokemon.setTrainerId(characterId);
                wildPokemon.setOriginalTrainerId(characterId);
                wildPokemon.setContainerId(container == PokemonContainerType.PARTY
                        ? PARTY_CONTAINER_ID : PC_CONTAINER_ID);
                wildPokemon.setContainerPosition(position);
                wildPokemon.setBallType(ball.getItemBallType());
                wildPokemon.setItem((short) -1);
                wildPokemon.setPokemonStatus(PokemonStatusType.NORMAL);
                int ballType = Byte.toUnsignedInt(ball.getItemBallType());
                int baseHappiness = CaptureSpeciesDataManager.lookup(
                        Short.toUnsignedInt(wildPokemon.getPokemonIndexId())).baseHappiness();
                wildPokemon.setFriendValue((short) (ballType == 21 ? 200 : baseHappiness));
                if (wildPokemon.getOtName() == null || wildPokemon.getOtName().isBlank()) {
                    var owner = characterService.getCharacter(characterId);
                    wildPokemon.setOtName(owner == null || owner.getPlayerEntity() == null
                            ? "" : owner.getPlayerEntity().getEntityName());
                }
                if (transaction.insertInto(POKEMON).set(wildPokemon.toPokemonRecord()).execute() != 1) {
                    throw new IllegalStateException("狩猎捕获宝可梦写入失败");
                }
                updateCaughtDex(transaction, characterId,
                        Short.toUnsignedInt(wildPokemon.getPokemonIndexId()));
                return new CaptureResult(true, null, true, wildPokemon, container, position);
            });
        } catch (RuntimeException exception) {
            log.error("狩猎球捕获事务失败: characterId={}, pokemonId={}, ball={}",
                    characterId, wildPokemon.getPokemonId(), ballItemIndexId, exception);
            return CaptureResult.rejected();
        }
    }

    private PokemonContainerType chooseContainer(DSLContext transaction, long characterId) {
        int partyId = PARTY_CONTAINER_ID;
        Integer partyCountValue = transaction.selectCount().from(POKEMON)
                .where(POKEMON.TRAINER_ID.eq(characterId))
                .and(POKEMON.CONTAINER_ID.eq(partyId))
                .fetchOne(0, Integer.class);
        int partyCount = partyCountValue == null ? 0 : partyCountValue;
        return partyCount < PokemonContainerType.PARTY.getSize()
                ? PokemonContainerType.PARTY : PokemonContainerType.PC;
    }

    private short findFreePosition(DSLContext transaction, long characterId,
                                   PokemonContainerType container) {
        int containerId = container == PokemonContainerType.PARTY
                ? PARTY_CONTAINER_ID : PC_CONTAINER_ID;
        var character = characterService.getCharacter(characterId);
        if (character == null) {
            return -1;
        }
        int capacity = container == PokemonContainerType.PARTY
                ? PokemonContainerType.PARTY.getSize()
                : PokemonContainerType.PC.getSize()
                + Math.max(0, character.getPcBoxExpansionNumber()) * 60;
        Set<Short> used = new HashSet<>(transaction.select(POKEMON.CONTAINER_POSITION)
                .from(POKEMON)
                .where(POKEMON.TRAINER_ID.eq(characterId))
                .and(POKEMON.CONTAINER_ID.eq(containerId))
                .fetch(POKEMON.CONTAINER_POSITION));
        for (int position = 0; position < capacity && position <= Short.MAX_VALUE; position++) {
            short shortPosition = (short) position;
            if (!used.contains(shortPosition)) {
                return shortPosition;
            }
        }
        return -1;
    }

    private void updateCaughtDex(DSLContext transaction, long characterId, int pokemonIndexId) {
        var dex = transaction.selectFrom(POKEMON_DEX)
                .where(POKEMON_DEX.PLAYER_ID.eq(characterId))
                .forUpdate()
                .fetchOne();
        BitSet caught = dex == null || dex.getCaughtLevel() == null
                ? new BitSet() : BitSet.valueOf(dex.getCaughtLevel());
        caught.set(pokemonIndexId);
        if (dex == null) {
            transaction.insertInto(POKEMON_DEX)
                    .set(POKEMON_DEX.PLAYER_ID, characterId)
                    .set(POKEMON_DEX.MEET_LEVEL, new byte[0])
                    .set(POKEMON_DEX.ALREADY_HAVE_LEVEL, new byte[0])
                    .set(POKEMON_DEX.CAUGHT_LEVEL, caught.toByteArray())
                    .set(POKEMON_DEX.CAUGHT_ALPHA_LEVEL, new byte[0])
                    .execute();
            return;
        }
        if (transaction.update(POKEMON_DEX)
                .set(POKEMON_DEX.CAUGHT_LEVEL, caught.toByteArray())
                .where(POKEMON_DEX.PLAYER_ID.eq(characterId))
                .execute() != 1) {
            throw new IllegalStateException("图鉴捕获状态更新失败");
        }
    }
}
