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

class SpawnNpcCommandTest {
    @Test
    void exposesClientCommandNameAndGmPermission() {
        SpawnNpcCommand command = new SpawnNpcCommand(new NpcSpawnService());
        assertEquals("spawnnpc", command.getName());
        assertEquals(PermissionType.GM, command.getRequiredPermission());
        assertSame(command, new CommandRegistry(Set.of(command)).find("spawnnpc").orElseThrow());
        assertTrue(command.getUsage().contains("脚本偏移 0"));
    }

    @Test
    void invalidRequestsProduceChineseFeedbackBeforeAnySpawn() {
        SpawnNpcCommand command = new SpawnNpcCommand(new NpcSpawnService());
        List<String> replies = new ArrayList<>();
        CommandFeedbackService feedback = new CommandFeedbackService() {
            @Override
            public void reply(Session session, String message) {
                replies.add(message);
            }
        };
        CommandContext context = new CommandContext(null, null, null, feedback);
        command.execute(context, new String[0]);
        command.execute(context, new String[]{"a", "0", "0", "0", "0", "0"});
        command.execute(context, new String[]{"68", "1", "0", "0", "0", "0"});
        command.execute(context, new String[]{"0", "0", "0", "0", "0", "0"});
        assertEquals(4, replies.size());
        assertTrue(replies.get(0).startsWith("用法"));
        assertTrue(replies.get(1).contains("十进制整数"));
        assertTrue(replies.get(2).contains("脚本偏移目前只支持 0"));
        assertTrue(replies.get(3).contains("角色尚未就绪"));
    }
}
