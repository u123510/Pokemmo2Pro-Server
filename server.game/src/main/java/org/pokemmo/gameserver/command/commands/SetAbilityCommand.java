package org.pokemmo.gameserver.command.commands;

import com.google.inject.Inject;
import org.pokemmo.gameserver.command.Command;
import org.pokemmo.gameserver.command.CommandContext;
import org.pokemmo.gameserver.game.character.CharacterData;
import org.pokemmo.gameserver.game.character.CharacterManager;
import org.pokemmo.gameserver.game.container.PokemonContainerType;
import org.pokemmo.gameserver.game.pokemon.PokemonData;
import org.pokemmo.gameserver.game.pool.GameSessionPool;
import org.pokemmo.gameserver.protocol.GameProtocol;
import org.pokemmo.gameserver.protocol.packets.s2c.SendUpdatePokemonDataPacket;
import org.pokemmo.gameserver.game.pokemon.UpdatePokemonData;
import org.server.Session;

public class SetAbilityCommand implements Command {
    @Inject
    public SetAbilityCommand() {
    }

    @Override
    public String getName() {
        return "setability";
    }

    @Override
    public String getUsage() {
        return "//setability <character name> <party slot 0-5> <ability slot>";
    }

    @Override
    public void execute(CommandContext context, String[] arguments) {
        if (arguments.length != 3) {
            context.reply("Usage: " + getUsage());
            return;
        }

        CharacterData targetCharacter = context.getGameServerService().getCharacterByName(arguments[0]);
        if (targetCharacter == null) {
            context.reply("Character not found: " + arguments[0]);
            return;
        }

        int partyPosition;
        try {
            partyPosition = Integer.parseInt(arguments[1]);
        } catch (NumberFormatException exception) {
            context.reply("Party slot must be an integer from 0 to 5.");
            return;
        }
        if (partyPosition < 0 || partyPosition >= 6) {
            context.reply("Party slot must be an integer from 0 to 5.");
            return;
        }

        int abilityIndex;
        try {
            abilityIndex = Integer.parseInt(arguments[2]);
        } catch (NumberFormatException exception) {
            context.reply("Ability slot must be an integer.");
            return;
        }

        long characterId = targetCharacter.getPlayerEntity().getEntityGameId();
        Session targetSession = GameSessionPool.getPlayerSessionInPool(characterId);
        CharacterManager targetManager = targetSession == null
                ? null
                : targetSession.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get();
        PokemonData targetPokemon = targetManager == null || targetManager.getPartyPokemons()[partyPosition] == null
                ? context.getGameServerService()
                .getCharacterContainerPokemons(
                        characterId,
                        context.getGameServerService().getContainerByType(PokemonContainerType.PARTY)
                )
                .stream()
                .filter(pokemon -> pokemon.getContainerPosition() == partyPosition)
                .findFirst()
                .orElse(null)
                : targetManager.getPartyPokemons()[partyPosition];
        if (targetPokemon == null) {
            context.reply("There is no Pokemon in that party slot.");
            return;
        }

        if (targetPokemon.getPokemonDexData().getPokemonAbilities() == null
                || abilityIndex < 0
                || abilityIndex >= targetPokemon.getPokemonDexData().getPokemonAbilities().size()) {
            context.reply("Ability slot is not available for this Pokemon.");
            return;
        }

        if (!context.getGameServerService().updatePokemonAbilityIndex(
                characterId,
                targetPokemon.getPokemonId(),
                (short) abilityIndex
        )) {
            context.reply("The Pokemon ability could not be saved.");
            return;
        }

        targetPokemon.setPokemonAbilityIndex((short) abilityIndex);
        if (targetSession != null && targetManager != null
                && targetManager.getCharacterData() != null) {
            targetSession.send(new SendUpdatePokemonDataPacket(new UpdatePokemonData.Builder()
                    .setUpdatePokemon(targetPokemon)
                    .setIsReloadAbilityIndex(true)
                    .build()));
        }
        context.reply("Setability for " + targetCharacter.getPlayerEntity().getEntityName()
                + " party slot " + partyPosition + " to ability slot " + abilityIndex + ".");
    }
}
