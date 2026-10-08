package org.pokemmo.gameserver.command.commands;

import com.google.inject.Inject;
import org.pokemmo.gameserver.command.Command;
import org.pokemmo.gameserver.command.CommandContext;

public final class MoveCloseCommand implements Command {
    @Inject
    public MoveCloseCommand() {
    }

    @Override
    public String getName() { return "moveclose"; }

    @Override
    public String getUsage() { return "//moveclose <dx -1..1> <dy -1..1>"; }

    @Override
    public void execute(CommandContext context, String[] arguments) {
        if (arguments.length != 2) {
            context.reply("Usage: " + getUsage());
            return;
        }
        final int dx;
        final int dy;
        try {
            dx = Integer.parseInt(arguments[0]);
            dy = Integer.parseInt(arguments[1]);
        } catch (NumberFormatException exception) {
            context.reply("moveclose offsets must be integers from -1 to 1.");
            return;
        }
        if (dx < -1 || dx > 1 || dy < -1 || dy > 1 || (dx == 0 && dy == 0)) {
            context.reply("moveclose offsets must be from -1 to 1 and cannot both be zero.");
            return;
        }
        String failure = context.getCharacterManager().moveClose(dx, dy);
        if (failure != null) {
            context.reply(failure);
            return;
        }
        context.reply("Moved to the adjacent position.");
    }
}
