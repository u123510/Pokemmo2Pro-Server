package org.pokemmo.gameserver.command;

import lombok.Getter;
import org.pokemmo.gameserver.game.character.CharacterManager;
import org.pokemmo.gameserver.services.GameServerService;
import org.server.Session;

@Getter
public class CommandContext {
    private final Session session;
    private final CharacterManager characterManager;
    private final GameServerService gameServerService;
    private final CommandFeedbackService feedbackService;

    public CommandContext(Session session,
                          CharacterManager characterManager,
                          GameServerService gameServerService,
                          CommandFeedbackService feedbackService) {
        this.session = session;
        this.characterManager = characterManager;
        this.gameServerService = gameServerService;
        this.feedbackService = feedbackService;
    }

    public void reply(String message) {
        feedbackService.reply(session, message);
    }
}
