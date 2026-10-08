package org.pokemmo.gameserver.command.commands;

import com.google.inject.Inject;
import org.pokemmo.gameserver.command.Command;
import org.pokemmo.gameserver.command.CommandContext;
import org.pokemmo.gameserver.game.pokemon.PokemonData;
import org.pokemmo.gameserver.game.pokemon.PokemonStatType;
import org.pokemmo.gameserver.game.pokemon.UpdatePokemonData;
import org.pokemmo.gameserver.protocol.packets.s2c.SendUpdatePokemonDataPacket;

import java.util.Locale;

public class SetEvsCommand implements Command {
    @Inject
    public SetEvsCommand() {
    }

    @Override
    public String getName() {
        return "setevs";
    }

    @Override
    public String getUsage() {
        return "//setevs <party slot 0-5> <HP|ATTACK|DEFENSE|SPEED|SP_ATTACK|SP_DEFENSE> <0-252>";
    }

    @Override
    public void execute(CommandContext context, String[] arguments) {
        if (arguments.length != 3) {
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

        PokemonStatType statType = parseStatType(arguments[1]);
        if (statType == null || statType.getType() >= PokemonStatType.ACCURACY.getType()) {
            context.reply("EV stat must be HP, ATTACK, DEFENSE, SPEED, SP_ATTACK or SP_DEFENSE.");
            return;
        }

        int evValue;
        try {
            evValue = Integer.parseInt(arguments[2]);
        } catch (NumberFormatException exception) {
            context.reply("EV value must be an integer from 0 to 252.");
            return;
        }
        if (evValue < 0 || evValue > 252) {
            context.reply("EV value must be an integer from 0 to 252.");
            return;
        }

        PokemonData targetPokemon = context.getCharacterManager().getPartyPokemons()[partyPosition];
        if (targetPokemon == null) {
            context.reply("There is no Pokemon in that party slot.");
            return;
        }

        short[] evValues = targetPokemon.getPokemonEvs().clone();
        evValues[statType.getType()] = (short) evValue;
        long characterId = context.getCharacterManager().getCharacterData().getPlayerEntity().getEntityGameId();
        if (!context.getGameServerService().updatePokemonEvs(characterId, targetPokemon.getPokemonId(), evValues)) {
            context.reply("The Pokemon EVs could not be saved.");
            return;
        }

        targetPokemon.setPokemonEvs(evValues);
        targetPokemon.setMaxHp(targetPokemon.getPokemonDexData().getPokemonAbilityValue(
                PokemonStatType.HP,
                targetPokemon.getPokemonIvs()[PokemonStatType.HP.getType()],
                evValues[PokemonStatType.HP.getType()],
                targetPokemon.getLevel(),
                targetPokemon.getNatureType()
        ));
        context.getSession().send(new SendUpdatePokemonDataPacket(new UpdatePokemonData.Builder()
                .setUpdatePokemon(targetPokemon)
                .setIsReloadPokemonAbilityValue(true)
                .setIsReloadPokemonEvs(true)
                .build()));
        context.reply("Party slot " + partyPosition + " " + arguments[1].toUpperCase(Locale.ROOT)
                + " EVs set to " + evValue + ".");
    }

    private PokemonStatType parseStatType(String value) {
        return switch (value.toUpperCase(Locale.ROOT)) {
            case "HP" -> PokemonStatType.HP;
            case "ATTACK" -> PokemonStatType.ATTACK;
            case "DEFENSE" -> PokemonStatType.DEFENSE;
            case "SPEED" -> PokemonStatType.SPEED;
            case "SP_ATTACK", "SPECIAL_ATTACK" -> PokemonStatType.SPECIAL_ATTACK;
            case "SP_DEFENSE", "SPECIAL_DEFENSE" -> PokemonStatType.SPECIAL_DEFENSE;
            default -> null;
        };
    }
}
