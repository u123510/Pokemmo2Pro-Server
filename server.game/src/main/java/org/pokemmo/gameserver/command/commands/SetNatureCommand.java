package org.pokemmo.gameserver.command.commands;

import com.google.inject.Inject;
import org.pokemmo.db.jooq.tables.records.ContainerRecord;
import org.pokemmo.gameserver.command.Command;
import org.pokemmo.gameserver.command.CommandContext;
import org.pokemmo.gameserver.game.container.PokemonContainerType;
import org.pokemmo.gameserver.game.pokemon.PokemonData;
import org.pokemmo.gameserver.game.pokemon.PokemonNatureType;
import org.pokemmo.gameserver.game.pokemon.UpdatePokemonData;
import org.pokemmo.gameserver.protocol.packets.s2c.SendPokemonContainerPacket;
import org.pokemmo.gameserver.protocol.packets.s2c.SendUpdatePokemonDataPacket;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class SetNatureCommand implements Command {
    private static final int NATURE_COUNT = PokemonNatureType.values().length;
    private static final String SUPPORTED_NATURES = Arrays.stream(PokemonNatureType.values())
            .map(PokemonNatureType::name)
            .collect(Collectors.joining(", "));

    @Inject
    public SetNatureCommand() {
    }

    @Override
    public String getName() {
        return "setnature";
    }

    @Override
    public String getUsage() {
        return "//setnature <party slot 0-5> <text containing an English nature>";
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

        PokemonNatureType natureType = parseNature(arguments[1]);
        if (natureType == null) {
            context.reply("Nature text must contain one of: " + SUPPORTED_NATURES + ".");
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

        Integer newPersonalityValue = withNature(targetPokemon, natureType);
        if (newPersonalityValue == null) {
            context.reply("The Pokemon nature could not be changed without changing its gender.");
            return;
        }

        long characterId = context.getCharacterManager().getCharacterData()
                .getPlayerEntity().getEntityGameId();
        if (!context.getGameServerService().updatePokemonPersonalityValue(
                characterId, targetPokemon.getPokemonId(), newPersonalityValue)) {
            context.reply("The Pokemon nature could not be saved.");
            return;
        }

        targetPokemon.setPersonalityValue(newPersonalityValue);
        targetPokemon.setNatureType(natureType);
        context.getCharacterManager().getPartyPokemons()[partyPosition] = targetPokemon;
        context.getSession().send(new SendUpdatePokemonDataPacket(new UpdatePokemonData.Builder()
                .setUpdatePokemon(targetPokemon)
                .setIsReloadPokemonAbilityValue(true)
                .setIsReloadPokemonCatchInfo(true)
                .setIsReloadNatureType(true)
                .build()));

        ContainerRecord partyContainer = context.getGameServerService()
                .getContainerByType(PokemonContainerType.PARTY);
        if (partyContainer != null) {
            List<PokemonData> partyPokemons = context.getGameServerService()
                    .getCharacterContainerPokemons(characterId, partyContainer);
            context.getSession().send(new SendPokemonContainerPacket(partyContainer, partyPokemons));
        }
        context.reply("Party slot " + partyPosition + " nature set to " + natureType.name() + ".");
    }

    private PokemonNatureType parseNature(String value) {
        String normalizedValue = value.toUpperCase(Locale.ROOT);
        return Arrays.stream(PokemonNatureType.values())
                .filter(natureType -> normalizedValue.contains(natureType.name()))
                .findFirst()
                .orElse(null);
    }

    private Integer withNature(PokemonData pokemon, PokemonNatureType natureType) {
        int personalityValue = pokemon.getPersonalityValue();
        int natureDelta = Byte.toUnsignedInt(natureType.getType())
                - Math.floorMod(personalityValue, NATURE_COUNT);
        short genderRatio = pokemon.getPokemonDexData().getGenderRatio();
        int currentGender = (personalityValue & 0xFF) >= genderRatio ? 0 : 1;

        Integer bestCandidate = null;
        long bestDistance = Long.MAX_VALUE;
        for (int offset = -256; offset <= 256; offset++) {
            long candidate = (long) personalityValue + natureDelta + (long) NATURE_COUNT * offset;
            if (candidate < Integer.MIN_VALUE || candidate > Integer.MAX_VALUE) {
                continue;
            }
            if (genderRatio != 255) {
                int candidateGender = (((int) candidate & 0xFF) >= genderRatio) ? 0 : 1;
                if (candidateGender != currentGender) {
                    continue;
                }
            }
            long distance = Math.abs(candidate - personalityValue);
            if (distance < bestDistance) {
                bestCandidate = (int) candidate;
                bestDistance = distance;
            }
        }
        return bestCandidate;
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
