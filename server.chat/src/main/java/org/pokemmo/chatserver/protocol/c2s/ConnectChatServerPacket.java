package org.pokemmo.chatserver.protocol.c2s;

import com.google.inject.Inject;
import org.pokemmo.chatserver.chat.ChatBroadcastListener;
import org.pokemmo.chatserver.chat.ChatManager;
import org.pokemmo.chatserver.protocol.ChatProtocol;
import org.pokemmo.chatserver.protocol.s2c.SendChatServerConnectResultPacket;
import org.pokemmo.chatserver.services.ChatServerService;
import org.pokemmo.db.jooq.tables.records.AccountRecord;
import org.server.IncomingPacket;
import org.server.Session;
import org.server.bytes.ByteBufEx;
import org.server.services.ServerService;

public class ConnectChatServerPacket extends IncomingPacket {
    @Inject
    private ServerService sessionService;
    @Inject
    private ChatServerService chatServerService;
    @Inject
    private ChatBroadcastListener chatBroadcastListener;
    private long characterId;
    private byte[] chatSessionKey;
    @Override
    public void decode(ByteBufEx buffer) {
        characterId = buffer.readLongLE();
        byte keySize = buffer.readByte();
        chatSessionKey = buffer.readByteArray(keySize);
    }
    @Override
    public void handle(Session session){
        AccountRecord accountRecord = chatServerService.getAccountByCharacterId(characterId);
        if(accountRecord == null){
            session.send(new SendChatServerConnectResultPacket(false));
            session.close();
            return;
        }
        ChatManager chatManager = new ChatManager.Builder()
                .setAccountRecord(accountRecord)
                .build();
        session.attr(ChatProtocol.ATTRIBUTE_CHAT_MANAGER).set(chatManager);
        if (!sessionService.validateSessionKey(accountRecord.getAccountId(), "chat","login",session.getRemoteAddress(), chatSessionKey)) {
            session.send(new SendChatServerConnectResultPacket(false));
            session.close();
            return;
        }
        session.send(new SendChatServerConnectResultPacket(true));
        chatBroadcastListener.addSpeakerSessionToPool(characterId,session);
    }
}
