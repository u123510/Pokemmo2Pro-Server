package org.pokemmo.gameserver.command.commands;

import com.google.inject.Inject;
import org.pokemmo.gameserver.command.Command;
import org.pokemmo.gameserver.command.CommandContext;
import org.pokemmo.gameserver.game.battle.BattleManager;

public class WinBattleCommand implements Command {
    @Inject
    public WinBattleCommand() {
    }

    @Override
    public String getName() {
        return "winbattle";
    }

    @Override
    public String getUsage() {
        return "//winbattle";
    }

    @Override
    public void execute(CommandContext context, String[] arguments) {
        if (arguments.length != 0) {
            context.reply("Usage: " + getUsage());
            return;
        }

        BattleManager battleManager = context.getCharacterManager().getBattleManager();
        if (battleManager == null) {
            context.reply("You are not currently in a battle.");
            return;
        }
        if (!battleManager.forceVictory(context.getSession())) {
            context.reply("The current battle has already finished or cannot be resolved.");
            return;
        }

        context.reply("Battle victory triggered.");
    }
}
