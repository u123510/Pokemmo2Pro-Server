package org.pokemmo.gameserver.command.commands;

import com.google.inject.Inject;
import org.pokemmo.gameserver.command.Command;
import org.pokemmo.gameserver.command.CommandContext;
import org.pokemmo.gameserver.game.pokemon.PokemonData;
import org.pokemmo.gameserver.game.pokemon.UpdatePokemonData;
import org.pokemmo.gameserver.protocol.packets.s2c.SendUpdatePokemonDataPacket;

public class SetHiddenAbilityCommand implements Command {
    @Inject
    public SetHiddenAbilityCommand() {
    }

    @Override
    public String getName() {
        return "setha";
    }

    @Override
    public String getUsage() {
        return "//setha <party slot 0-5> <true|false>";
    }

    @Override
    public void execute(CommandContext context, String[] arguments) {
        if (arguments.length != 2) {
            context.reply("Usage: " + getUsage());
            return;
        }

        int partyPosition;
        try {
            partyPosition = Integer.parseInt(arguments[0]);
        } catch (NumberFormatException exception) {
            context.reply("Party slot must be an integer from 0 to 5.");
            return;
        }
        if (partyPosition < 0 || partyPosition >= context.getCharacterManager().getPartyPokemons().length) {
            context.reply("Party slot must be an integer from 0 to 5.");
            return;
        }

        Boolean enabled = parseBoolean(arguments[1]);
        if (enabled == null) {
            context.reply("Hidden ability state must be true or false.");
            return;
        }

        PokemonData targetPokemon = context.getCharacterManager().getPartyPokemons()[partyPosition];
        if (targetPokemon == null) {
            context.reply("There is no Pokemon in that party slot.");
            return;
        }

        long characterId = context.getCharacterManager().getCharacterData().getPlayerEntity().getEntityGameId();
        if (!context.getGameServerService().updatePokemonHiddenAbility(
                characterId,
                targetPokemon.getPokemonId(),
                enabled
        )) {
            context.reply("The Pokemon hidden ability state could not be saved.");
            return;
        }

        targetPokemon.setHasHiddenAbility(enabled);
        context.getSession().send(new SendUpdatePokemonDataPacket(new UpdatePokemonData.Builder()
                .setUpdatePokemon(targetPokemon)
                .setIsReloadPokemonRarity(true)
                .build()));
        context.reply("Party slot " + partyPosition + " hidden ability set to " + enabled + ".");
    }

    private Boolean parseBoolean(String value) {
        if (value.equalsIgnoreCase("true")) {
            return true;
        }
        if (value.equalsIgnoreCase("false")) {
            return false;
        }
        return null;
    }
}
