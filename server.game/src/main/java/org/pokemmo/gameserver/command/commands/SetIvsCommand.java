package org.pokemmo.gameserver.command.commands;

import com.google.inject.Inject;
import org.pokemmo.gameserver.command.Command;
import org.pokemmo.gameserver.command.CommandContext;
import org.pokemmo.gameserver.game.pokemon.PokemonData;
import org.pokemmo.gameserver.game.pokemon.PokemonStatType;
import org.pokemmo.gameserver.game.pokemon.UpdatePokemonData;
import org.pokemmo.gameserver.protocol.packets.s2c.SendUpdatePokemonDataPacket;

public class SetIvsCommand implements Command {
    @Inject
    public SetIvsCommand() {
    }

    @Override
    public String getName() {
        return "setivs";
    }

    @Override
    public String getUsage() {
        return "//setivs <party slot 0-5> <hp> <attack> <defense> <special attack> <special defense> <speed>";
    }

    @Override
    public void execute(CommandContext context, String[] arguments) {
        if (arguments.length != 7) {
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

        // The command uses display order: HP, Atk, Def, SpA, SpD, Spe.
        // PokemonData stores the last three as Spe, SpA, SpD.
        short[] ivValues = new short[6];
        int[] internalIndexes = {0, 1, 2, 4, 5, 3};
        for (int inputIndex = 0; inputIndex < internalIndexes.length; inputIndex++) {
            int ivValue;
            try {
                ivValue = Integer.parseInt(arguments[inputIndex + 1]);
            } catch (NumberFormatException exception) {
                context.reply("Each IV must be an integer from 0 to 31.");
                return;
            }
            if (ivValue < 0 || ivValue > 31) {
                context.reply("Each IV must be an integer from 0 to 31.");
                return;
            }
            ivValues[internalIndexes[inputIndex]] = (short) ivValue;
        }

        PokemonData targetPokemon = context.getCharacterManager().getPartyPokemons()[partyPosition];
        if (targetPokemon == null) {
            context.reply("There is no Pokemon in that party slot.");
            return;
        }

        long characterId = context.getCharacterManager().getCharacterData().getPlayerEntity().getEntityGameId();
        if (!context.getGameServerService().updatePokemonIvs(characterId, targetPokemon.getPokemonId(), ivValues)) {
            context.reply("The Pokemon IVs could not be saved.");
            return;
        }

        targetPokemon.setPokemonIvs(ivValues);
        targetPokemon.setMaxHp(targetPokemon.getPokemonDexData().getPokemonAbilityValue(
                PokemonStatType.HP,
                ivValues[PokemonStatType.HP.getType()],
                targetPokemon.getPokemonEvs()[PokemonStatType.HP.getType()],
                targetPokemon.getLevel(),
                targetPokemon.getNatureType()
        ));
        context.getSession().send(new SendUpdatePokemonDataPacket(new UpdatePokemonData.Builder()
                .setUpdatePokemon(targetPokemon)
                .setIsReloadPokemonAbilityValue(true)
                .setIsReloadPokemonIndividualValue(true)
                .build()));
        context.reply("Party slot " + partyPosition + " IVs updated.");
    }
}
