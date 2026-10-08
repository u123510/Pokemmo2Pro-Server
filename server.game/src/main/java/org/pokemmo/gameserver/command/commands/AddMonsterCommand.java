package org.pokemmo.gameserver.command.commands;

import com.google.inject.Inject;
import org.pokemmo.gameserver.command.Command;
import org.pokemmo.gameserver.command.CommandContext;
import org.pokemmo.gameserver.game.character.CharacterData;
import org.pokemmo.gameserver.game.character.CharacterManager;
import org.pokemmo.gameserver.game.container.PokemonContainerType;
import org.pokemmo.gameserver.game.pokemon.PokemonData;
import org.pokemmo.gameserver.game.pokemon.PokemonManager;
import org.pokemmo.gameserver.game.pool.GameSessionPool;
import org.pokemmo.gameserver.protocol.GameProtocol;
import org.pokemmo.gameserver.protocol.packets.s2c.SendAddPokemonPacket;
import org.server.Session;

import java.util.Random;

public class AddMonsterCommand implements Command {
    @Inject
    public AddMonsterCommand() {
    }

    @Override
    public String getName() {
        return "addmonster";
    }

    @Override
    public String getUsage() {
        return "//addmonster 角色名称 宝可梦编号 等级";
    }

    @Override
    public void execute(CommandContext context, String[] arguments) {
        if (arguments.length != 3) {
            context.reply("使用方法: " + getUsage());
            return;
        }

        CharacterData targetCharacter = context.getGameServerService().getCharacterByName(arguments[0]);
        if (targetCharacter == null) {
            context.reply("未知使用方法: " + arguments[0]);
            return;
        }

        int pokemonIndexId;
        try {
            pokemonIndexId = Integer.parseInt(arguments[1]);
        } catch (NumberFormatException exception) {
            context.reply("Pokemon index id must be an integer.");
            return;
        }
        if (pokemonIndexId <= 0 || pokemonIndexId > Short.MAX_VALUE
                || PokemonManager.getPokemonoexData(pokemonIndexId) == null) {
            context.reply("Unknown Pokemon index id: " + arguments[1]);
            return;
        }

        int level;
        try {
            level = Integer.parseInt(arguments[2]);
        } catch (NumberFormatException exception) {
            context.reply("Pokemon level must be an integer from 1 to 100.");
            return;
        }
        if (level < 1 || level > 100) {
            context.reply("Pokemon level must be an integer from 1 to 100.");
            return;
        }

        long targetCharacterId = targetCharacter.getPlayerEntity().getEntityGameId();
        Session targetSession = GameSessionPool.getPlayerSessionInPool(targetCharacterId);
        CharacterManager targetManager = targetSession == null
                ? null
                : targetSession.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get();
        if (targetManager == null) {
            targetSession = null;
        }

        short partyPosition = targetManager == null
                ? context.getGameServerService().findNextFreePartyPosition(targetCharacterId)
                : findNextFreePartyPosition(targetManager);
        int containerId;
        short containerPosition;
        if (partyPosition >= 0) {
            containerId = context.getGameServerService()
                    .getContainerByType(PokemonContainerType.PARTY)
                    .getId();
            containerPosition = partyPosition;
        } else {
            containerId = context.getGameServerService()
                    .getContainerByType(PokemonContainerType.PC)
                    .getId();
            containerPosition = context.getGameServerService().findNextFreePcBoxPosition(targetCharacterId);
            if (containerPosition < 0) {
                context.reply("The target has no free party or PC slot.");
                return;
            }
        }

        short catchAddress = (short) targetCharacter.getPlayerEntity().getMapHeaderIdOrGbaMapGroupId();
        if (targetManager != null && targetManager.getCurrentMapDatas()[0] != null) {
            catchAddress = targetManager.getCurrentMapDatas()[0].getRomMapHeaderIndex();
        }

        Random random = targetManager == null
                ? context.getCharacterManager().getRandom()
                : targetManager.getRandom();
        PokemonData pokemon = PokemonManager.createWildPokemon(
                targetCharacterId,
                targetCharacter.getPlayerEntity().getEntityName(),
                (short) targetCharacter.getPlayerEntity().getRegionIndexId(),
                catchAddress,
                (short) pokemonIndexId,
                (short) level,
                containerId,
                containerPosition,
                random,
                context.getCharacterManager().getSnowflakeIdGenerator()
        );
        if (pokemon == null) {
            context.reply("The Pokemon could not be created.");
            return;
        }

        context.getGameServerService().addPokemon(pokemon.toPokemonRecord());
        if (targetManager != null && containerId == context.getGameServerService()
                .getContainerByType(PokemonContainerType.PARTY).getId()) {
            targetManager.getPartyPokemons()[containerPosition] = pokemon;
        }
        if (targetSession != null) {
            targetSession.send(new SendAddPokemonPacket(pokemon));
        }

        String containerName = containerId == context.getGameServerService()
                .getContainerByType(PokemonContainerType.PARTY).getId() ? "party" : "PC";
        context.reply("Added Pokemon " + pokemonIndexId + " at level " + level
                + " to " + targetCharacter.getPlayerEntity().getEntityName()
                + " " + containerName + " position " + containerPosition + ".");
    }

    private short findNextFreePartyPosition(CharacterManager characterManager) {
        PokemonData[] partyPokemons = characterManager.getPartyPokemons();
        for (short position = 0; position < partyPokemons.length; position++) {
            if (partyPokemons[position] == null) {
                return position;
            }
        }
        return -1;
    }
}
