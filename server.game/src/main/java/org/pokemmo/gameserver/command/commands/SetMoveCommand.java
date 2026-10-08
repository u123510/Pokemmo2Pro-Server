package org.pokemmo.gameserver.command.commands;

import com.google.inject.Inject;
import org.pokemmo.gameserver.command.Command;
import org.pokemmo.gameserver.command.CommandContext;
import org.pokemmo.gameserver.game.move.MoveManager;
import org.pokemmo.gameserver.game.move.PokemonMoveData;
import org.pokemmo.gameserver.game.permission.PermissionType;
import org.pokemmo.gameserver.game.pokemon.PokemonData;
import org.pokemmo.gameserver.game.pokemon.UpdatePokemonData;
import org.pokemmo.gameserver.protocol.packets.s2c.SendUpdatePokemonDataPacket;

import java.util.Set;

/**
 * 宝可梦技能更换指令：
 * 支持单槽位更换: //setmove <party slot 0-5> <move slot 0-3> <move id>
 * 支持全槽位设置: //setmove <party slot 0-5> <m1> <m2> <m3> <m4>
 */
public class SetMoveCommand implements Command {

    @Inject
    public SetMoveCommand() {
    }

    @Override
    public String getName() {
        return "setmove";
    }

    @Override
    public Set<String> getAliases() {
        return Set.of("setmoves", "setskill", "setskills");
    }

    @Override
    public PermissionType getRequiredPermission() {
        return PermissionType.NORMAL;
    }

    @Override
    public String getUsage() {
        return "//setmove <party slot 0-5> <move slot 0-3> <move id> OR //setmove <party slot 0-5> <m1> <m2> <m3> <m4>";
    }

    @Override
    public void execute(CommandContext context, String[] arguments) {
        if (arguments.length != 3 && arguments.length != 5) {
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

        PokemonData targetPokemon = context.getCharacterManager().getPartyPokemons()[partyPosition];
        if (targetPokemon == null) {
            context.reply("There is no Pokemon in that party slot.");
            return;
        }

        short[] targetMoves;
        short[] targetMovesPp;
        byte targetPpUpTimes;

        if (arguments.length == 3) {
            int moveSlot;
            try {
                moveSlot = Integer.parseInt(arguments[1]);
            } catch (NumberFormatException exception) {
                context.reply("Move slot must be an integer from 0 to 3.");
                return;
            }

            if (moveSlot < 0 || moveSlot > 3) {
                context.reply("Move slot must be an integer from 0 to 3.");
                return;
            }

            int moveId;
            try {
                moveId = Integer.parseInt(arguments[2]);
            } catch (NumberFormatException exception) {
                context.reply("Move ID must be a valid integer.");
                return;
            }

            if (moveId < 0 || moveId > 10000) {
                context.reply("Move ID must be between 0 and 10000.");
                return;
            }

            if (moveId > 0) {
                PokemonMoveData moveData = MoveManager.getPokemonMove((short) moveId);
                if (moveData == null) {
                    context.reply("Move ID " + moveId + " does not exist.");
                    return;
                }
            } else {
                int remainingMoves = 0;
                for (int slot = 0; slot < 4; slot++) {
                    if (slot != moveSlot && targetPokemon.getMoves()[slot] > 0) {
                        remainingMoves++;
                    }
                }
                if (remainingMoves == 0) {
                    context.reply("Pokemon must retain at least one move.");
                    return;
                }
            }

            targetMoves = targetPokemon.getMoves().clone();
            targetMovesPp = targetPokemon.getMovesPp().clone();
            targetPpUpTimes = targetPokemon.getPpUpTimes();

            targetMoves[moveSlot] = (short) moveId;
            targetPpUpTimes = (byte) (targetPpUpTimes & ~(3 << (moveSlot * 2)));

            if (moveId == 0) {
                targetMovesPp[moveSlot] = 0;
            } else {
                PokemonMoveData moveData = MoveManager.getPokemonMove((short) moveId);
                targetMovesPp[moveSlot] = moveData.getMoveBasePp();
            }
        } else {
            int[] newMoveIds = new int[4];
            int validCount = 0;
            for (int i = 0; i < 4; i++) {
                try {
                    newMoveIds[i] = Integer.parseInt(arguments[i + 1]);
                } catch (NumberFormatException exception) {
                    context.reply("Each move ID must be a valid integer.");
                    return;
                }
                if (newMoveIds[i] < 0 || newMoveIds[i] > 10000) {
                    context.reply("Move ID must be between 0 and 10000.");
                    return;
                }
                if (newMoveIds[i] > 0) {
                    PokemonMoveData moveData = MoveManager.getPokemonMove((short) newMoveIds[i]);
                    if (moveData == null) {
                        context.reply("Move ID " + newMoveIds[i] + " does not exist.");
                        return;
                    }
                    validCount++;
                }
            }

            if (validCount == 0) {
                context.reply("Pokemon must have at least one valid move.");
                return;
            }

            targetMoves = new short[4];
            targetMovesPp = new short[4];
            targetPpUpTimes = 0;

            for (int i = 0; i < 4; i++) {
                targetMoves[i] = (short) newMoveIds[i];
                if (newMoveIds[i] > 0) {
                    PokemonMoveData moveData = MoveManager.getPokemonMove((short) newMoveIds[i]);
                    targetMovesPp[i] = moveData.getMoveBasePp();
                } else {
                    targetMovesPp[i] = 0;
                }
            }
        }

        long characterId = context.getCharacterManager().getCharacterData().getPlayerEntity().getEntityGameId();
        if (!context.getGameServerService().updatePokemonMoves(
                characterId,
                targetPokemon.getPokemonId(),
                targetMoves,
                targetMovesPp,
                targetPpUpTimes
        )) {
            context.reply("The Pokemon moves could not be saved.");
            return;
        }

        targetPokemon.setMoves(targetMoves);
        targetPokemon.setMovesPp(targetMovesPp);
        targetPokemon.setPpUpTimes(targetPpUpTimes);

        context.getSession().send(new SendUpdatePokemonDataPacket(new UpdatePokemonData.Builder()
                .setUpdatePokemon(targetPokemon)
                .setIsReloadPokemonMove(true)
                .build()));

        context.reply("Party slot " + partyPosition + " moves updated successfully.");
    }
}
