package org.pokemmo.gameserver.command.commands;

import com.google.inject.Inject;
import org.pokemmo.gameserver.command.Command;
import org.pokemmo.gameserver.command.CommandContext;
import org.pokemmo.gameserver.game.pokemon.PokemonData;
import org.pokemmo.gameserver.game.pokemon.UpdatePokemonData;
import org.pokemmo.gameserver.protocol.packets.s2c.SendUpdatePokemonDataPacket;

public class SetAlphaCommand implements Command {
    @Inject
    public SetAlphaCommand() {
    }

    @Override
    public String getName() {
        return "setalpha";
    }

    @Override
    public String getUsage() {
        return "//setalpha <party slot 0-5> <true|false>";
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

        Boolean isAlpha = parseBoolean(arguments[1]);
        if (isAlpha == null) {
            context.reply("Alpha state must be true or false.");
            return;
        }

        PokemonData targetPokemon = context.getCharacterManager().getPartyPokemons()[partyPosition];
        if (targetPokemon == null) {
            context.reply("There is no Pokemon in that party slot.");
            return;
        }

        long characterId = context.getCharacterManager().getCharacterData().getPlayerEntity().getEntityGameId();
        if (!context.getGameServerService().updatePokemonAlpha(characterId, targetPokemon.getPokemonId(), isAlpha)) {
            context.reply("The Pokemon alpha state could not be saved.");
            return;
        }

        targetPokemon.setAlpha(isAlpha);
        UpdatePokemonData updatePokemonData = new UpdatePokemonData.Builder()
                .setUpdatePokemon(targetPokemon)
                .setIsReloadPokemonRarity(true)
                .build();
        context.getSession().send(new SendUpdatePokemonDataPacket(updatePokemonData));
        context.reply("Party slot " + partyPosition + " alpha state set to " + isAlpha + ".");
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
