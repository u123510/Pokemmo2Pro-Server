package org.pokemmo.gameserver.command;

import org.pokemmo.gameserver.game.permission.PermissionType;

import java.util.Set;

public interface Command {
    String getName();

    default Set<String> getAliases() {
        return Set.of();
    }

    default PermissionType getRequiredPermission() {
        return PermissionType.GM;
    }

    String getUsage();

    void execute(CommandContext context, String[] arguments);
}
