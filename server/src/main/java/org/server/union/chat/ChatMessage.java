package org.server.union.chat;

import lombok.Getter;

import org.server.union.language.LanguageType;

@Getter
public class ChatMessage {
    private long senderId;
    private byte senderPermission;
    private String sender;
    private ChatType chatType;
    private LanguageType languageType;
    private String message;


    public static ChatMessage gameNotification(String message) {
        return new ChatMessage(ChatType.SYSTEM_ANNOUNCEMENTS, LanguageType.ENGLISH, message, "");
    }

    public ChatMessage(ChatType chatType, LanguageType languageType, String message, String sender) {
        this.chatType = chatType;
        this.languageType = languageType;
        this.message = message;
        this.sender = sender;
    }
    public ChatMessage(long senderId, byte senderPermission, String sender, ChatType chatType, LanguageType languageType, String message) {
        this.senderId = senderId;
        this.senderPermission = senderPermission;
        this.sender = sender;
        this.chatType = chatType;
        this.languageType = languageType;
        this.message = message;
    }
}
