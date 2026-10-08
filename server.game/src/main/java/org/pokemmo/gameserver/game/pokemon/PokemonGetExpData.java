package org.pokemmo.gameserver.game.pokemon;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class PokemonGetExpData {
    private long pokemonId;
    private int baseExp;
    private boolean hasTrainerBattleBonus;
    private float trainerBattleBonus = 0.1f;
    private boolean hasTradeBonus;
    private float tradeBonus = 0.1f;
    private boolean hasCharmExpBonus;
    private float charmExpBonus = 0.1f;
    private boolean hasDonatorStatusBonus;
    private float donatorStatusBonus = 0.1f;
    private boolean hasHeldItemBonus;
    private float heldItemBonus = 0.1f;
    private boolean hasExpReamplifierBonus;
    private float expReamplifierBonus = 0.1f;
}
