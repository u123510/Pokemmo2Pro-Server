package org.pokemmo.gameserver.services.gtl;

import org.jooq.DSLContext;
import org.pokemmo.db.jooq.tables.records.PokemonRecord;
import org.pokemmo.gameserver.game.container.PokemonContainerType;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.pokemmo.db.jooq.Tables.POKEMON;

/** Finds legal PC positions for GTL returns and purchases. */
final class GtlPokemonSlotAllocator {
    private GtlPokemonSlotAllocator() {
    }

    static boolean isValidPokemonPosition(int containerId, short position, int pcCapacity) {
        if (position < 0) {
            return false;
        }
        if (containerId == GtlSchema.PARTY_CONTAINER_ID) {
            return position < PokemonContainerType.PARTY.getSize();
        }
        return containerId == GtlSchema.PC_CONTAINER_ID && position < pcCapacity;
    }

    static short findNextFreePcBoxPosition(
            DSLContext context, long characterId, Short pcBoxExpansionNumber) {
        List<Short> pcPositions = context
                .select(POKEMON.CONTAINER_POSITION)
                .from(POKEMON)
                .where(POKEMON.TRAINER_ID.eq(characterId))
                .and(POKEMON.CONTAINER_ID.eq(GtlSchema.PC_CONTAINER_ID))
                .fetch(POKEMON.CONTAINER_POSITION);
        Set<Short> usedPositions = new HashSet<>(pcPositions);

        int expansionAmount = pcBoxExpansionNumber == null
                ? 0
                : Math.max(0, pcBoxExpansionNumber);
        int capacity = Math.min(
                Short.MAX_VALUE + 1,
                PokemonContainerType.PC.getSize() + expansionAmount * 60);
        for (int position = 0; position < capacity; position++) {
            short slot = (short) position;
            if (!usedPositions.contains(slot)) {
                return slot;
            }
        }
        return -1;
    }
}
