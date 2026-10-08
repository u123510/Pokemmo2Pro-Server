package org.pokemmo.gameserver.game.item;

/** Supported server-side item-use behaviors. */
public enum ItemUseHandlerType {
    NONE,
    BIKE,
    HEAL_HP,
    REVIVE_HP,
    CURE_STATUS,
    HEAL_PP,
    ADD_EXP,
    ADD_LEVEL,
    ADD_FRIENDSHIP,
    ADD_PARTICLE_EFFECT,
    ADD_EV,
    TEACH_MOVE
}
