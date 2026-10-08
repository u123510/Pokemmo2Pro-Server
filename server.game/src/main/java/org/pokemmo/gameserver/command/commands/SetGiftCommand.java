package org.pokemmo.gameserver.command.commands;

import com.google.inject.Inject;
import org.pokemmo.db.jooq.tables.records.ContainerRecord;
import org.pokemmo.gameserver.command.Command;
import org.pokemmo.gameserver.command.CommandContext;
import org.pokemmo.gameserver.game.container.PokemonContainerType;
import org.pokemmo.gameserver.game.pokemon.PokemonData;
import org.pokemmo.gameserver.game.pokemon.PokemonNormalRibbonType;
import org.pokemmo.gameserver.game.pokemon.UpdatePokemonData;
import org.pokemmo.gameserver.protocol.packets.s2c.SendPokemonContainerPacket;
import org.pokemmo.gameserver.protocol.packets.s2c.SendUpdatePokemonDataPacket;

import java.util.List;

public class SetGiftCommand implements Command {
    private static final int GIFT_RIBBON_INDEX = Byte.toUnsignedInt(
            PokemonNormalRibbonType.GIFT_RIBBON.getIndex());

    @Inject
    public SetGiftCommand() {
    }

    @Override
    public String getName() {
        return "setgift";
    }

    @Override
    public String getUsage() {
        return "//setgift <party slot 0-5>";
    }

    @Override
    public void execute(CommandContext context, String[] arguments) {
        if (arguments.length != 1) {
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

        PokemonData targetPokemon = findTargetPokemon(context, partyPosition);
        if (targetPokemon == null) {
            context.reply("There is no Pokemon in that party slot.");
            return;
        }

        boolean[] normalRibbons = targetPokemon.getNormalRibbon();
        if (normalRibbons == null || normalRibbons.length != PokemonNormalRibbonType.values().length) {
            context.reply("The Pokemon ribbon data is invalid.");
            return;
        }
        if (normalRibbons[GIFT_RIBBON_INDEX]) {
            context.reply("Party slot " + partyPosition + " already has the Gift Ribbon.");
            return;
        }

        boolean[] updatedRibbons = normalRibbons.clone();
        updatedRibbons[GIFT_RIBBON_INDEX] = true;
        long characterId = context.getCharacterManager().getCharacterData()
                .getPlayerEntity().getEntityGameId();
        if (!context.getGameServerService().updatePokemonNormalRibbons(
                characterId, targetPokemon.getPokemonId(), updatedRibbons)) {
            context.reply("The Pokemon gift state could not be saved.");
            return;
        }

        targetPokemon.setNormalRibbon(updatedRibbons);
        context.getCharacterManager().getPartyPokemons()[partyPosition] = targetPokemon;
        context.getSession().send(new SendUpdatePokemonDataPacket(new UpdatePokemonData.Builder()
                .setUpdatePokemon(targetPokemon)
                .setIsReloadPokemonRibbonInfo(true)
                .build()));
        refreshParty(context, characterId);
        context.reply("Gift Ribbon added to party slot " + partyPosition + ".");
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
