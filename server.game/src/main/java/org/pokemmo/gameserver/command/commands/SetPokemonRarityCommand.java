package org.pokemmo.gameserver.command.commands;

import org.pokemmo.gameserver.command.Command;
import org.pokemmo.gameserver.command.CommandContext;
import org.pokemmo.gameserver.game.character.CharacterData;
import org.pokemmo.gameserver.game.character.CharacterManager;
import org.pokemmo.gameserver.game.container.PokemonContainerType;
import org.pokemmo.gameserver.game.pokemon.PokemonData;
import org.pokemmo.gameserver.game.pokemon.UpdatePokemonData;
import org.pokemmo.gameserver.game.pool.GameSessionPool;
import org.pokemmo.gameserver.protocol.GameProtocol;
import org.pokemmo.gameserver.protocol.packets.s2c.SendUpdatePokemonDataPacket;
import org.server.Session;

public abstract class SetPokemonRarityCommand implements Command {
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

        Boolean enabled = parseBoolean(arguments[2]);
        if (enabled == null) {
            context.reply("Rarity state must be true or false.");
            return;
        }

        PokemonData targetPokemon = findTargetPokemon(context, targetCharacter.getPlayerEntity().getEntityGameId(), partyPosition);
        if (targetPokemon == null) {
            context.reply("There is no Pokemon in that party slot.");
            return;
        }

        if (!updateDatabase(context, targetCharacter.getPlayerEntity().getEntityGameId(), targetPokemon.getPokemonId(), enabled)) {
            context.reply("The Pokemon rarity state could not be saved.");
            return;
        }

        updateMemory(targetPokemon, enabled);
        Session targetSession = GameSessionPool.getPlayerSessionInPool(targetCharacter.getPlayerEntity().getEntityGameId());
        if (targetSession != null) {
            targetSession.send(new SendUpdatePokemonDataPacket(new UpdatePokemonData.Builder()
                    .setUpdatePokemon(targetPokemon)
                    .setIsReloadPokemonRarity(true)
                    .build()));
        }
        context.reply(getName() + " for " + targetCharacter.getPlayerEntity().getEntityName()
                + " party slot " + partyPosition + " set to " + enabled + ".");
    }

    private PokemonData findTargetPokemon(CommandContext context, long characterId, int partyPosition) {
        Session targetSession = GameSessionPool.getPlayerSessionInPool(characterId);
        if (targetSession != null) {
            CharacterManager targetManager = targetSession.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get();
            if (targetManager != null && targetManager.getPartyPokemons()[partyPosition] != null) {
                return targetManager.getPartyPokemons()[partyPosition];
            }
        }
        return context.getGameServerService()
                .getCharacterContainerPokemons(characterId,
                        context.getGameServerService().getContainerByType(PokemonContainerType.PARTY))
                .stream()
                .filter(pokemon -> pokemon.getContainerPosition() == partyPosition)
                .findFirst()
                .orElse(null);
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

    protected abstract boolean updateDatabase(CommandContext context, long characterId, long pokemonId, boolean enabled);

    protected abstract void updateMemory(PokemonData pokemon, boolean enabled);
}
