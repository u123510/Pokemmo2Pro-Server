package org.pokemmo.gameserver.command.commands;

import com.google.inject.Inject;
import org.pokemmo.db.jooq.tables.records.ContainerRecord;
import org.pokemmo.gameserver.command.Command;
import org.pokemmo.gameserver.command.CommandContext;
import org.pokemmo.gameserver.game.character.CharacterData;
import org.pokemmo.gameserver.game.character.CharacterManager;
import org.pokemmo.gameserver.game.container.PokemonContainerType;
import org.pokemmo.gameserver.game.pokemon.PokemonData;
import org.pokemmo.gameserver.game.pokemon.UpdatePokemonData;
import org.pokemmo.gameserver.game.pool.GameSessionPool;
import org.pokemmo.gameserver.protocol.GameProtocol;
import org.pokemmo.gameserver.protocol.packets.s2c.SendPokemonContainerPacket;
import org.pokemmo.gameserver.protocol.packets.s2c.SendUpdatePokemonDataPacket;
import org.server.Session;

import java.util.List;

public class SetBallTypeCommand implements Command {
    private static final int MIN_BALL_TYPE = 0;
    private static final int MAX_BALL_TYPE = 24;

    @Inject
    public SetBallTypeCommand() {
    }

    @Override
    public String getName() {
        return "setballtype";
    }

    @Override
    public String getUsage() {
        return "//setballtype <character name> <party slot 0-5> <ball type 0-24>";
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
        int ballType;
        try {
            partyPosition = Integer.parseInt(arguments[1]);
            ballType = Integer.parseInt(arguments[2]);
        } catch (NumberFormatException exception) {
            context.reply("Party slot and ball type must be integers.");
            return;
        }
        if (partyPosition < 0 || partyPosition >= PokemonContainerType.PARTY.getSize()) {
            context.reply("Party slot must be an integer from 0 to 5.");
            return;
        }
        if (ballType < MIN_BALL_TYPE || ballType > MAX_BALL_TYPE) {
            context.reply("Ball type must be an integer from 0 to 24.");
            return;
        }

        long characterId = targetCharacter.getPlayerEntity().getEntityGameId();
        Session targetSession = GameSessionPool.getPlayerSessionInPool(characterId);
        CharacterManager targetManager = targetSession == null
                ? null
                : targetSession.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get();
        PokemonData targetPokemon = findTargetPokemon(
                context, characterId, partyPosition, targetManager);
        if (targetPokemon == null) {
            context.reply("There is no Pokemon in that party slot.");
            return;
        }

        if (!context.getGameServerService().updatePokemonBallType(
                characterId, targetPokemon.getPokemonId(), (short) ballType)) {
            context.reply("The Pokemon ball type could not be saved.");
            return;
        }

        targetPokemon.setBallType((short) ballType);
        if (targetSession != null && targetManager != null) {
            targetManager.getPartyPokemons()[partyPosition] = targetPokemon;
            targetSession.send(new SendUpdatePokemonDataPacket(new UpdatePokemonData.Builder()
                    .setUpdatePokemon(targetPokemon)
                    .setIsReloadPokemonBallType(true)
                    .build()));
            refreshParty(context, targetSession, characterId);
        }
        context.reply("Ball type for " + targetCharacter.getPlayerEntity().getEntityName()
                + " party slot " + partyPosition + " set to " + ballType + ".");
    }

    private PokemonData findTargetPokemon(
            CommandContext context,
            long characterId,
            int partyPosition,
            CharacterManager targetManager) {
        if (targetManager != null) {
            PokemonData targetPokemon = targetManager.getPartyPokemons()[partyPosition];
            if (targetPokemon != null && targetPokemon.getContainerPosition() == partyPosition) {
                return targetPokemon;
            }
        }

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

    private void refreshParty(CommandContext context, Session targetSession, long characterId) {
        ContainerRecord partyContainer = context.getGameServerService()
                .getContainerByType(PokemonContainerType.PARTY);
        if (partyContainer == null) {
            return;
        }
        List<PokemonData> partyPokemons = context.getGameServerService()
                .getCharacterContainerPokemons(characterId, partyContainer);
        targetSession.send(new SendPokemonContainerPacket(partyContainer, partyPokemons));
    }
}
