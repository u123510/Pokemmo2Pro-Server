package org.pokemmo.gameserver.command.commands;

import com.google.inject.Inject;
import org.pokemmo.db.jooq.tables.records.ContainerRecord;
import org.pokemmo.gameserver.command.Command;
import org.pokemmo.gameserver.command.CommandContext;
import org.pokemmo.gameserver.game.container.PokemonContainerType;
import org.pokemmo.gameserver.game.pokemon.PokemonData;
import org.pokemmo.gameserver.game.pokemon.PokemonRibbonMask;
import org.pokemmo.gameserver.game.pokemon.UpdatePokemonData;
import org.pokemmo.gameserver.protocol.packets.s2c.SendPokemonContainerPacket;
import org.pokemmo.gameserver.protocol.packets.s2c.SendUpdatePokemonDataPacket;

import java.util.List;

public class SetRibbonsCommand implements Command {
    @Inject
    public SetRibbonsCommand() {
    }

    @Override
    public String getName() {
        return "setribbons";
    }

    @Override
    public String getUsage() {
        return "//setribbons <party slot 0-5> <ribbon mask>";
    }

    @Override
    public void execute(CommandContext context, String[] arguments) {
        if (arguments.length != 2) {
            context.reply("Usage: " + getUsage());
            return;
        }

        int partyPosition;
        long ribbonMask;
        try {
            partyPosition = Integer.parseInt(arguments[0]);
            ribbonMask = Long.parseLong(arguments[1]);
        } catch (NumberFormatException exception) {
            context.reply("Party slot and ribbon mask must be decimal integers.");
            return;
        }
        if (partyPosition < 0 || partyPosition >= context.getCharacterManager().getPartyPokemons().length) {
            context.reply("Party slot must be an integer from 0 to 5.");
            return;
        }

        PokemonRibbonMask.DecodedRibbons decodedRibbons;
        try {
            decodedRibbons = PokemonRibbonMask.decode(ribbonMask);
        } catch (IllegalArgumentException exception) {
            context.reply("Invalid ribbon mask: " + exception.getMessage());
            return;
        }

        PokemonData targetPokemon = findTargetPokemon(context, partyPosition);
        if (targetPokemon == null) {
            context.reply("There is no Pokemon in that party slot.");
            return;
        }

        long characterId = context.getCharacterManager().getCharacterData()
                .getPlayerEntity().getEntityGameId();
        if (!context.getGameServerService().updatePokemonRibbons(
                characterId,
                targetPokemon.getPokemonId(),
                decodedRibbons.contestRibbons(),
                decodedRibbons.normalRibbons())) {
            context.reply("The Pokemon ribbons could not be saved.");
            return;
        }

        targetPokemon.setContestRibbon(decodedRibbons.contestRibbons());
        targetPokemon.setNormalRibbon(decodedRibbons.normalRibbons());
        context.getCharacterManager().getPartyPokemons()[partyPosition] = targetPokemon;
        context.getSession().send(new SendUpdatePokemonDataPacket(new UpdatePokemonData.Builder()
                .setUpdatePokemon(targetPokemon)
                .setIsReloadPokemonRibbonInfo(true)
                .build()));
        refreshParty(context, characterId);
        context.reply("Ribbon mask for party slot " + partyPosition + " set to " + ribbonMask + ".");
    }

    private PokemonData findTargetPokemon(CommandContext context, int partyPosition) {
        PokemonData targetPokemon = context.getCharacterManager().getPartyPokemons()[partyPosition];
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
