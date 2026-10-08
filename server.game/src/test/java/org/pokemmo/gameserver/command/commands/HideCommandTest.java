package org.pokemmo.gameserver.command.commands;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.pokemmo.gameserver.command.CommandContext;
import org.pokemmo.gameserver.command.CommandFeedbackService;
import org.pokemmo.gameserver.command.CommandRegistry;
import org.pokemmo.gameserver.game.permission.PermissionType;
import org.server.Session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HideCommandTest {
    @Test
    void registersNoArgumentToggleWithGmPermission() {
        HideCommand command = new HideCommand();
        assertEquals("//hide", command.getUsage());
        assertEquals(PermissionType.GM, command.getRequiredPermission());
        assertSame(command, new CommandRegistry(Set.of(command)).find("hide").orElseThrow());
        List<String> messages = new ArrayList<>();
        CommandFeedbackService feedback = new CommandFeedbackService() {
            @Override
            public void reply(Session session, String message) {
                messages.add(message);
            }
        };
        CommandContext context = new CommandContext(null, null, null, feedback);
        command.execute(context, new String[]{"true"});
        command.execute(context, new String[0]);
        assertTrue(messages.get(0).startsWith("用法"));
        assertTrue(messages.get(1).contains("角色尚未就绪"));
    }
}
