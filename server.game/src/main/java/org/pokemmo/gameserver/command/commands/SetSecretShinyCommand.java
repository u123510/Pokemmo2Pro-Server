package org.pokemmo.gameserver.command.commands;

import com.google.inject.Inject;
import org.pokemmo.gameserver.command.CommandContext;
import org.pokemmo.gameserver.game.pokemon.PokemonData;

public class SetSecretShinyCommand extends SetPokemonRarityCommand {
    @Inject
    public SetSecretShinyCommand() {
    }

    @Override
    public String getName() {
        return "setsecretshiny";
    }

    @Override
    public String getUsage() {
        return "//setsecretshiny <character name> <party slot 0-5> <true|false>";
    }

    @Override
    protected boolean updateDatabase(CommandContext context, long characterId, long pokemonId, boolean enabled) {
        return context.getGameServerService().updatePokemonSecret(characterId, pokemonId, enabled);
    }

    @Override
    protected void updateMemory(PokemonData pokemon, boolean enabled) {
        pokemon.setSecret(enabled);
    }
}
