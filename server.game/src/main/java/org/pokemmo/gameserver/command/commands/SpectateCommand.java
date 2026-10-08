package org.pokemmo.gameserver.command.commands;

import com.google.inject.Inject;
import org.pokemmo.gameserver.command.Command;
import org.pokemmo.gameserver.command.CommandContext;
import org.pokemmo.gameserver.game.battle.BattleSpectatingService;
import org.pokemmo.gameserver.game.permission.PermissionType;

/** Player command used by the client context menu to watch a nearby battle. */
public final class SpectateCommand implements Command {
    @Inject
    public SpectateCommand() {
    }

    @Override
    public String getName() {
        return "spectate";
    }

    @Override
    public PermissionType getRequiredPermission() {
        return PermissionType.NORMAL;
    }

    @Override
    public String getUsage() {
        return "//spectate player_name";
    }

    @Override
    public void execute(CommandContext context, String[] arguments) {
        if (arguments.length != 1) {
            context.reply("Usage: " + getUsage());
            return;
        }
        BattleSpectatingService.Result result = BattleSpectatingService.request(
                context.getCharacterManager(), arguments[0]);
        context.reply(result.message());
    }
}
