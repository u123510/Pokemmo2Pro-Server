package org.pokemmo.gameserver.command;

import com.google.inject.Singleton;
import org.pokemmo.gameserver.protocol.packets.s2c.SendChatMessagePacket;
import org.server.Session;
import org.server.union.chat.ChatMessage;

@Singleton
public class CommandFeedbackService {
    public void reply(Session session, String message) {
        session.send(new SendChatMessagePacket(ChatMessage.gameNotification(message)));
    }
}
