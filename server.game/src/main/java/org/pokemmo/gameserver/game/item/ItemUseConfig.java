package org.pokemmo.gameserver.game.item;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public final class ItemUseConfig {
    private List<ItemUseRule> itemEffects = new ArrayList<>();
}
