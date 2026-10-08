package org.pokemmo.gameserver.services.pokemon;

import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.pokemmo.db.Database;
import org.pokemmo.db.jooq.tables.records.PokemonRecord;

import static org.pokemmo.db.jooq.Tables.POKEMON;

/** Persists releasing a Pokemon from the PC without deleting its audit record. */
@Slf4j
final class PokemonReleaseService {
    private static final int PC_CONTAINER_ID = 0;
    private static final int DELETED_CONTAINER_ID = 8;

    private final Database database;

    PokemonReleaseService(Database database) {
        this.database = database;
    }

    boolean releaseFromPc(long characterId, long pokemonId) {
        if (characterId <= 0 || pokemonId <= 0) {
            return false;
        }

        try {
            return database.ctx().transactionResult(configuration -> {
                DSLContext transaction = DSL.using(configuration);
                PokemonRecord pokemon = transaction
                        .selectFrom(POKEMON)
                        .where(POKEMON.ID.eq(pokemonId))
                        .and(POKEMON.TRAINER_ID.eq(characterId))
                        .and(POKEMON.CONTAINER_ID.eq(PC_CONTAINER_ID))
                        .forUpdate()
                        .fetchOne();
                if (pokemon == null) {
                    return false;
                }

                int updated = transaction
                        .update(POKEMON)
                        .set(POKEMON.CONTAINER_ID, DELETED_CONTAINER_ID)
                        .set(POKEMON.CONTAINER_POSITION, (short) 0)
                        .where(POKEMON.ID.eq(pokemonId))
                        .and(POKEMON.TRAINER_ID.eq(characterId))
                        .and(POKEMON.CONTAINER_ID.eq(PC_CONTAINER_ID))
                        .execute();
                return updated == 1;
            });
        } catch (RuntimeException exception) {
            log.error("放生 PC 精灵事务失败: characterId={}, pokemonId={}",
                    characterId, pokemonId, exception);
            return false;
        }
    }
}
