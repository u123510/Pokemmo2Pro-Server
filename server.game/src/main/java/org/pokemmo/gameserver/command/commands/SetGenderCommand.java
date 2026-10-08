package org.pokemmo.gameserver.command.commands;

import com.google.inject.Inject;
import org.pokemmo.gameserver.command.Command;
import org.pokemmo.gameserver.command.CommandContext;
import org.pokemmo.db.jooq.tables.records.ContainerRecord;
import org.pokemmo.gameserver.game.container.PokemonContainerType;
import org.pokemmo.gameserver.game.pokemon.PokemonData;
import org.pokemmo.gameserver.game.pokemon.PokemonGenderUtil;
import org.pokemmo.gameserver.game.pokemon.PokemonNatureType;
import org.pokemmo.gameserver.game.pokemon.UpdatePokemonData;
import org.pokemmo.gameserver.protocol.packets.s2c.SendPokemonContainerPacket;
import org.pokemmo.gameserver.protocol.packets.s2c.SendUpdatePokemonDataPacket;

import java.util.List;

public class SetGenderCommand implements Command {
    @Inject
    public SetGenderCommand() {
    }

    @Override
    public String getName() {
        return "setgender";
    }

    @Override
    public String getUsage() {
        return "//setgender <party slot 0-5> <0 male|1 female>";
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

        byte gender;
        try {
            gender = Byte.parseByte(arguments[1]);
        } catch (NumberFormatException exception) {
            context.reply("Gender must be 0 (male) or 1 (female).");
            return;
        }
        if (gender != 0 && gender != 1) {
            context.reply("Gender must be 0 (male) or 1 (female).");
            return;
        }

        PokemonData targetPokemon = findTargetPokemon(context, partyPosition);
        if (targetPokemon == null) {
            context.reply("There is no Pokemon in that party slot.");
            return;
        }

        if (targetPokemon.getPokemonDexData() == null) {
            context.reply("The Pokemon data is not loaded for that party slot.");
            return;
        }

        short genderRatio = targetPokemon.getPokemonDexData().getGenderRatio();
        if (genderRatio == 255) {
            context.reply("This Pokemon is genderless and cannot be set to male or female.");
            return;
        }
        if (gender == 1 && genderRatio == 0) {
            context.reply("This Pokemon is male-only and cannot be set to female.");
            return;
        }

        final int newPersonalityValue;
        try {
            newPersonalityValue = PokemonGenderUtil.withGender(
                    targetPokemon.getPersonalityValue(),
                    genderRatio,
                    gender
            );
        } catch (IllegalArgumentException exception) {
            context.reply("This Pokemon cannot have a selectable gender.");
            return;
        }

        byte calculatedGender = (byte) ((newPersonalityValue & 0xFF) >= genderRatio ? 0 : 1);
        if (calculatedGender != gender) {
            context.reply("The calculated personality value did not produce the requested gender.");
            return;
        }

        long characterId = context.getCharacterManager().getCharacterData().getPlayerEntity().getEntityGameId();
        if (!context.getGameServerService().updatePokemonPersonalityValue(
                characterId, targetPokemon.getPokemonId(), newPersonalityValue)) {
            context.reply("The Pokemon gender could not be saved.");
            return;
        }

        targetPokemon.setPersonalityValue(newPersonalityValue);
        targetPokemon.setNatureType(PokemonNatureType.getByPersonalityValue(newPersonalityValue));
        context.getSession().send(new SendUpdatePokemonDataPacket(new UpdatePokemonData.Builder()
                .setUpdatePokemon(targetPokemon)
                .setIsReloadPokemonCatchInfo(true)
                .setIsReloadNatureType(true)
                .build()));

        // Some clients only rebuild party gender data when the complete party
        // container is sent. Keep the in-memory array and container payload in
        // sync so the visible party slot, battle preview and follow Pokemon all
        // observe the same personality value.
        context.getCharacterManager().getPartyPokemons()[partyPosition] = targetPokemon;
        ContainerRecord partyContainer = context.getGameServerService()
                .getContainerByType(PokemonContainerType.PARTY);
        if (partyContainer != null) {
            List<PokemonData> partyPokemons = context.getGameServerService()
                    .getCharacterContainerPokemons(characterId, partyContainer);
            context.getSession().send(new SendPokemonContainerPacket(
                    partyContainer,
                    partyPokemons
            ));
        }
        context.reply("Party slot " + partyPosition + " gender set to " + (gender == 0 ? "male" : "female") + ".");
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
}
