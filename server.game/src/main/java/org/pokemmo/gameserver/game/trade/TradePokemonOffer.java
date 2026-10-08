package org.pokemmo.gameserver.game.trade;

import org.pokemmo.gameserver.game.pokemon.PokemonData;

/** Snapshot of a Pokemon's source slot while it is offered for trade. */
public record TradePokemonOffer(long pokemonId, int containerId, short position, PokemonData pokemon) {
}
