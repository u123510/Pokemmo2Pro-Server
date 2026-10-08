package org.pokemmo.gameserver.services.shop;

final class ShopRejectedException extends RuntimeException {
    ShopRejectedException(String message) {
        super(message);
    }
}
