package org.pokemmo.gameserver.game.item;

import lombok.Getter;
import lombok.Setter;

/** Optional override for an item whose behavior is not fully represented by Item.bin. */
@Getter
@Setter
public final class ItemUseRule {
    private int itemIndexId;
    private ItemUseHandlerType handler = ItemUseHandlerType.NONE;
    private int amount;
    private int consumeAmount = 1;
    private int statusMask;
    private boolean percent;
    private int regionIndexId = -1;
}
