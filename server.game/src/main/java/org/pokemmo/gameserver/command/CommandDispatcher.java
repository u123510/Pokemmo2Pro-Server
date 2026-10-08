package org.pokemmo.gameserver.command;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.pokemmo.gameserver.game.character.CharacterManager;
import org.pokemmo.gameserver.protocol.GameProtocol;
import org.pokemmo.gameserver.services.GameServerService;
import org.server.Session;

@Singleton
@Slf4j
public class CommandDispatcher {
    private final CommandRegistry commandRegistry;
    private final GameServerService gameServerService;
    private final CommandFeedbackService feedbackService;

    @Inject
    public CommandDispatcher(CommandRegistry commandRegistry,
                             GameServerService gameServerService,
                             CommandFeedbackService feedbackService) {
        this.commandRegistry = commandRegistry;
        this.gameServerService = gameServerService;
        this.feedbackService = feedbackService;
    }

    public boolean dispatch(Session session, String message) {
        var parsedCommand = CommandParser.parse(message);
        if (parsedCommand.isEmpty()) {
            return false;
        }

        CommandParser.ParsedCommand invocation = parsedCommand.get();
        if (invocation.name().isEmpty()) {
            feedbackService.reply(session, "命令名称不能为空。");
            return true;
        }

        Command command = commandRegistry.find(invocation.name()).orElse(null);
        if (command == null) {
            feedbackService.reply(session, "未知命令: //" + invocation.name());
            return true;
        }

        CharacterManager characterManager = session.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get();
        if (characterManager == null || characterManager.getCharacterData() == null
                || characterManager.getCharacterData().getPlayerEntity() == null) {
            feedbackService.reply(session, "角色尚未就绪，暂时不能执行命令。");
            return true;
        }
        if (characterManager.getCharacterData().getPlayerEntity().getPermission() == null
                || characterManager.getCharacterData().getPlayerEntity().getPermission().getType()
                < command.getRequiredPermission().getType()) {
            feedbackService.reply(session, "权限不足，无法使用 //" + invocation.name() + "。");
            return true;
        }

        CommandContext context = new CommandContext(session, characterManager, gameServerService, feedbackService);
        try {
            command.execute(context, invocation.arguments());
        } catch (RuntimeException exception) {
            log.error("命令执行失败: //{}", invocation.name(), exception);
            feedbackService.reply(session, "命令执行失败: //" + invocation.name());
        }
        return true;
    }
}
