package org.pokemmo.gameserver.command.commands;

import com.google.inject.Inject;
import org.pokemmo.gameserver.command.Command;
import org.pokemmo.gameserver.command.CommandContext;

public final class MoveToCommand implements Command {
    @Inject
    public MoveToCommand() {
    }

    @Override
    public String getName() {
        return "moveto";
    }

    @Override
    public String getUsage() {
        return "//moveto <region> <mapHeader/group> <gbaMap> <x> <y>";
    }

    @Override
    public void execute(CommandContext context, String[] arguments) {
        if (arguments.length != 5) {
            context.reply("Usage: " + getUsage());
            return;
        }
        int[] values = new int[5];
        try {
            for (int i = 0; i < values.length; i++) {
                values[i] = Integer.parseInt(arguments[i]);
            }
        } catch (NumberFormatException exception) {
            context.reply("moveto arguments must be integers.");
            return;
        }
        String failure = context.getCharacterManager().moveTo(
                values[0], values[1], values[2], values[3], values[4]);
        if (failure != null) {
            context.reply(failure);
            return;
        }
        context.reply("Moved to the requested map position.");
    }
}
