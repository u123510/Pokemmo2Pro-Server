package org.pokemmo.gameserver.services.trade;

/** Signals a validated rejection and rolls back the surrounding jOOQ transaction. */
final class TradeRejectedException extends RuntimeException {
    TradeRejectedException(String reason) {
        super(reason, null, false, false);
    }
}
