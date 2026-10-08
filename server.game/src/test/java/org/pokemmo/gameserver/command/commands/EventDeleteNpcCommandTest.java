package org.pokemmo.gameserver.command.commands;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.pokemmo.gameserver.command.CommandContext;
import org.pokemmo.gameserver.command.CommandFeedbackService;
import org.pokemmo.gameserver.command.CommandRegistry;
import org.pokemmo.gameserver.game.entity.NpcDeleteService;
import org.pokemmo.gameserver.game.permission.PermissionType;
import org.server.Session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventDeleteNpcCommandTest {
    @Test
    void registersExactClientNameWithGmPermission() {
        EventDeleteNpcCommand command = new EventDeleteNpcCommand(new NpcDeleteService());
        assertSame(command, new CommandRegistry(Set.of(command)).find("eventdeletenpc").orElseThrow());
        assertEquals(PermissionType.GM, command.getRequiredPermission());
        assertTrue(command.getUsage().contains("Object ID"));
    }

    @Test
    void rejectsBadArgumentsAndKeepsLargeObjectIdAsLong() {
        List<String> messages = new ArrayList<>();
        CommandFeedbackService feedback = new CommandFeedbackService() {
            @Override
            public void reply(Session session, String message) {
                messages.add(message);
            }
        };
        CommandContext context = new CommandContext(null, null, null, feedback);
        EventDeleteNpcCommand command = new EventDeleteNpcCommand(new NpcDeleteService());
        command.execute(context, new String[0]);
        command.execute(context, new String[]{"1", "2"});
        command.execute(context, new String[]{"bad"});
        command.execute(context, new String[]{"9223372036854775808"});
        command.execute(context, new String[]{"0"});
        command.execute(context, new String[]{"553459519488"});
        assertEquals(6, messages.size());
        assertTrue(messages.get(0).startsWith("用法"));
        assertTrue(messages.get(1).startsWith("用法"));
        assertTrue(messages.get(2).contains("64 位"));
        assertTrue(messages.get(3).contains("64 位"));
        assertTrue(messages.get(4).contains("必须是正"));
        assertTrue(messages.get(5).contains("角色尚未就绪"));
    }
}
