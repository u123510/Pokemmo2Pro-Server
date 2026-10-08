package org.pokemmo.gameserver.services.trade;

import org.pokemmo.db.Database;
import org.pokemmo.gameserver.game.trade.TradeItemOffer;
import org.pokemmo.gameserver.services.GameServerService;
import org.pokemmo.gameserver.util.SnowflakeIdGenerator;

import java.util.List;

/** Compatibility facade for the trade transaction service. */
public final class TradeService {
    private final TradeAssetTransfer assetTransfer;

    public TradeService(Database database) {
        TradeValidationService validationService = new TradeValidationService();
        this.assetTransfer = new TradeAssetTransfer(
                database, validationService, new TradeResultMapper());
    }

    public boolean completeTrade(long firstId, long secondId, int firstMoney, int secondMoney,
                                 List<Long> firstPokemonIds, List<Long> secondPokemonIds,
                                 List<TradeItemOffer> firstItems, List<TradeItemOffer> secondItems) {
        return completeTradeResult(firstId, secondId, firstMoney, secondMoney,
                firstPokemonIds, secondPokemonIds, firstItems, secondItems, null).success();
    }

    public boolean completeTrade(long firstId, long secondId, int firstMoney, int secondMoney,
                                 List<Long> firstPokemonIds, List<Long> secondPokemonIds,
                                 List<TradeItemOffer> firstItems, List<TradeItemOffer> secondItems,
                                 SnowflakeIdGenerator idGenerator) {
        return completeTradeResult(firstId, secondId, firstMoney, secondMoney,
                firstPokemonIds, secondPokemonIds, firstItems, secondItems, idGenerator).success();
    }

    public GameServerService.TradeCompletionResult completeTradeResult(
            long firstId, long secondId, int firstMoney, int secondMoney,
            List<Long> firstPokemonIds, List<Long> secondPokemonIds,
            List<TradeItemOffer> firstItems, List<TradeItemOffer> secondItems,
            SnowflakeIdGenerator idGenerator) {
        return assetTransfer.transfer(firstId, secondId, firstMoney, secondMoney,
                firstPokemonIds, secondPokemonIds, firstItems, secondItems, idGenerator);
    }
}
