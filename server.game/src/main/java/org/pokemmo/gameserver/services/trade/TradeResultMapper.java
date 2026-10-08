package org.pokemmo.gameserver.services.trade;

import org.jooq.Result;
import org.pokemmo.db.jooq.tables.records.PokemonRecord;
import org.pokemmo.gameserver.game.pokemon.PokemonData;
import org.pokemmo.gameserver.services.GameServerService;

import java.util.ArrayList;
import java.util.List;

/** Maps the transaction result into the service-facing result contract. */
public final class TradeResultMapper {
    public GameServerService.TradeCompletionResult completed(
            List<Long> firstReceivedIds, Result<PokemonRecord> firstReceived,
            List<Long> secondReceivedIds, Result<PokemonRecord> secondReceived) {
        return new GameServerService.TradeCompletionResult(
                true,
                toPokemonData(firstReceivedIds, firstReceived),
                toPokemonData(secondReceivedIds, secondReceived));
    }

    private List<PokemonData> toPokemonData(List<Long> ids, Result<PokemonRecord> records) {
        List<PokemonData> transferred = new ArrayList<>(ids.size());
        for (Long id : ids) {
            PokemonRecord record = records.stream()
                    .filter(candidate -> candidate.getId() != null
                            && id != null
                            && candidate.getId().longValue() == id.longValue())
                    .findFirst().orElse(null);
            if (record == null) {
                return List.of();
            }
            transferred.add(new PokemonData.Builder().setByRecord(record).build());
        }
        return transferred;
    }
}
