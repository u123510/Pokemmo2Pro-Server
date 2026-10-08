package org.pokemmo.gameserver.command.commands;

import com.google.inject.Inject;
import org.pokemmo.db.jooq.tables.records.ContainerRecord;
import org.pokemmo.gameserver.command.Command;
import org.pokemmo.gameserver.command.CommandContext;
import org.pokemmo.gameserver.game.container.PokemonContainerType;
import org.pokemmo.gameserver.game.pokemon.PokemonData;
import org.pokemmo.gameserver.game.pokemon.UpdatePokemonData;
import org.pokemmo.gameserver.protocol.packets.s2c.SendPokemonContainerPacket;
import org.pokemmo.gameserver.protocol.packets.s2c.SendUpdatePokemonDataPacket;

import java.util.List;

public class SetOtCommand implements Command {
    private static final int MAX_OT_NAME_LENGTH = 32;

    @Inject
    public SetOtCommand() {
    }

    @Override
    public String getName() {
        return "setot";
    }

    @Override
    public String getUsage() {
        return "//setot <party slot 0-5> <OT name or localization token>";
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

        String otName = arguments[1];
        if (otName.isBlank() || otName.length() > MAX_OT_NAME_LENGTH) {
            context.reply("OT name must contain 1 to 32 characters.");
            return;
        }

        PokemonData targetPokemon = findTargetPokemon(context, partyPosition);
        if (targetPokemon == null) {
            context.reply("There is no Pokemon in that party slot.");
            return;
        }

        long characterId = context.getCharacterManager().getCharacterData()
                .getPlayerEntity().getEntityGameId();
        if (!context.getGameServerService().updatePokemonOtName(
                characterId, targetPokemon.getPokemonId(), otName)) {
            context.reply("The Pokemon OT name could not be saved.");
            return;
        }

        targetPokemon.setOtName(otName);
        context.getCharacterManager().getPartyPokemons()[partyPosition] = targetPokemon;
        context.getSession().send(new SendUpdatePokemonDataPacket(new UpdatePokemonData.Builder()
                .setUpdatePokemon(targetPokemon)
                .setIsReloadPokemonCanRememberMove(true)
                .build()));
        refreshParty(context, characterId);
        context.reply("Party slot " + partyPosition + " OT name set to " + otName + ".");
    }

    private PokemonData findTargetPokemon(CommandContext context, int partyPosition) {
        PokemonData[] partyPokemons = context.getCharacterManager().getPartyPokemons();
        PokemonData targetPokemon = partyPokemons[partyPosition];
        if (targetPokemon != null && targetPokemon.getContainerPosition() == partyPosition) {
            return targetPokemon;
        }

        long characterId = context.getCharacterManager().getCharacterData()
                .getPlayerEntity().getEntityGameId();
        ContainerRecord partyContainer = context.getGameServerService()
                .getContainerByType(PokemonContainerType.PARTY);
        if (partyContainer == null) {
            return null;
        }
        return context.getGameServerService()
                .getCharacterContainerPokemons(characterId, partyContainer)
                .stream()
                .filter(pokemon -> pokemon.getContainerPosition() == partyPosition)
                .findFirst()
                .orElse(null);
    }

    private void refreshParty(CommandContext context, long characterId) {
        ContainerRecord partyContainer = context.getGameServerService()
                .getContainerByType(PokemonContainerType.PARTY);
        if (partyContainer == null) {
            return;
        }
        List<PokemonData> partyPokemons = context.getGameServerService()
                .getCharacterContainerPokemons(characterId, partyContainer);
        context.getSession().send(new SendPokemonContainerPacket(partyContainer, partyPokemons));
    }
}
