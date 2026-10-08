package org.pokemmo.gameserver.command.commands;

import com.google.inject.Inject;
import org.pokemmo.gameserver.command.Command;
import org.pokemmo.gameserver.command.CommandContext;
import org.pokemmo.gameserver.game.pokemon.PokemonData;
import org.pokemmo.gameserver.game.pokemon.UpdatePokemonData;
import org.pokemmo.gameserver.protocol.packets.s2c.SendUpdatePokemonDataPacket;

public class SetHappinessCommand implements Command {
    @Inject
    public SetHappinessCommand() {
    }

    @Override
    public String getName() {
        return "sethappiness";
    }

    @Override
    public String getUsage() {
        return "//sethappiness <party slot 0-5> <0-255>";
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

        int happiness;
        try {
            happiness = Integer.parseInt(arguments[1]);
        } catch (NumberFormatException exception) {
            context.reply("Happiness must be an integer from 0 to 255.");
            return;
        }
        if (happiness < 0 || happiness > 255) {
            context.reply("Happiness must be an integer from 0 to 255.");
            return;
        }

        PokemonData targetPokemon = context.getCharacterManager().getPartyPokemons()[partyPosition];
        if (targetPokemon == null) {
            context.reply("There is no Pokemon in that party slot.");
            return;
        }

        long characterId = context.getCharacterManager().getCharacterData().getPlayerEntity().getEntityGameId();
        if (!context.getGameServerService().updatePokemonFriendValue(
                characterId,
                targetPokemon.getPokemonId(),
                (short) happiness
        )) {
            context.reply("The Pokemon happiness could not be saved.");
            return;
        }

        targetPokemon.setFriendValue((short) happiness);
        context.getSession().send(new SendUpdatePokemonDataPacket(new UpdatePokemonData.Builder()
                .setUpdatePokemon(targetPokemon)
                .setIsReloadPokemonFriendValue(true)
                .build()));
        context.reply("Party slot " + partyPosition + " happiness set to " + happiness + ".");
    }
}
