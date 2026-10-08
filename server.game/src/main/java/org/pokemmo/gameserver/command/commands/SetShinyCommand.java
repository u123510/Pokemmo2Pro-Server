package org.pokemmo.gameserver.command.commands;

import com.google.inject.Inject;
import org.pokemmo.gameserver.command.CommandContext;
import org.pokemmo.gameserver.game.pokemon.PokemonData;

public class SetShinyCommand extends SetPokemonRarityCommand {
    @Inject
    public SetShinyCommand() {
    }

    @Override
    public String getName() {
        return "setshiny";
    }

    @Override
    public String getUsage() {
        return "//setshiny <character name> <party slot 0-5> <true|false>";
    }

    @Override
    protected boolean updateDatabase(CommandContext context, long characterId, long pokemonId, boolean enabled) {
        return context.getGameServerService().updatePokemonShiny(characterId, pokemonId, enabled);
    }

    @Override
    protected void updateMemory(PokemonData pokemon, boolean enabled) {
        pokemon.setShiny(enabled);
    }
}
