package org.pokemmo.gameserver.command.commands;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.pokemmo.gameserver.command.CommandContext;
import org.pokemmo.gameserver.command.CommandFeedbackService;
import org.pokemmo.gameserver.command.CommandRegistry;
import org.pokemmo.gameserver.game.entity.NpcSpawnService;
import org.pokemmo.gameserver.game.permission.PermissionType;
import org.server.Session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventSpawnNpcCommandTest {
    @Test
    void registersIndependentCommandWithGmPermission() {
        EventSpawnNpcCommand command = new EventSpawnNpcCommand(new NpcSpawnService());
        CommandRegistry registry = new CommandRegistry(Set.of(command));
        assertSame(command, registry.find("eventspawnnpc").orElseThrow());
        assertTrue(registry.find("spawnnpc").isEmpty());
        assertEquals(PermissionType.GM, command.getRequiredPermission());
        assertTrue(command.getUsage().contains("条件数 0"));
    }

    @Test
    void capturedRequestReachesSpawnWhileUnsupportedRequestStopsAtValidation() {
        List<String> messages = new ArrayList<>();
        CommandFeedbackService feedback = new CommandFeedbackService() {
            @Override
            public void reply(Session session, String message) {
                messages.add(message);
            }
        };
        CommandContext context = new CommandContext(null, null, null, feedback);
        EventSpawnNpcCommand command = new EventSpawnNpcCommand(new NpcSpawnService());
        command.execute(context, new String[0]);
        command.execute(context, "0 248 10 0 0 0 0 -1 -1 false false false 1.0 0".split(" "));
        command.execute(context, "0 248 10 0 0 0 0 -1 -1 true false false 1.0 0".split(" "));
        assertTrue(messages.get(0).contains("14 个基础参数"));
        assertTrue(messages.get(1).contains("角色尚未就绪"));
        assertTrue(messages.get(2).contains("更新已有 NPC"));
        assertEquals(3, messages.size());
    }
}
