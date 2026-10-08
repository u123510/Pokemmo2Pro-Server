package org.pokemmo.chatserver.protocol;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;

import io.netty.util.AttributeKey;
import org.pokemmo.chatserver.chat.ChatBroadcastListener;
import org.pokemmo.chatserver.chat.ChatManager;
import org.pokemmo.chatserver.protocol.c2s.ClientChatServerHello;
import org.pokemmo.chatserver.protocol.c2s.ConnectChatServerPacket;
import org.pokemmo.chatserver.protocol.c2s.JoinChatServerPacket;
import org.pokemmo.chatserver.protocol.s2c.SendChatServerConnectResultPacket;
import org.pokemmo.chatserver.protocol.s2c.SendServerChatServerHello;
import org.pokemmo.chatserver.protocol.s2c.SendSpeakerChatPacket;
import org.pokemmo.chatserver.services.ChatServerService;
import org.server.DataFlow;
import org.server.Protocol;
import org.server.services.ServerService;

public class ChatProtocol extends Protocol{
    public static final AttributeKey<ChatManager> ATTRIBUTE_CHAT_MANAGER = AttributeKey.valueOf("chat_manager");
    public ChatProtocol(ChatServerService chatServerService, ServerService sessionService, ChatBroadcastListener chatBroadcastListener) {
        super(16, true, true, createInjector(chatServerService,sessionService,chatBroadcastListener));
        registerPacket(DataFlow.CLIENT_TO_SERVER, (byte) 0x00, ClientChatServerHello.class);
        registerPacket(DataFlow.SERVER_TO_CLIENT, (byte) 0x00, SendServerChatServerHello.class);
        registerPacket(DataFlow.CLIENT_TO_SERVER, (byte) 0x01, ConnectChatServerPacket.class);
        registerPacket(DataFlow.SERVER_TO_CLIENT, (byte) 0x01, SendChatServerConnectResultPacket.class);
        registerPacket(DataFlow.CLIENT_TO_SERVER, (byte) 0x02, JoinChatServerPacket.class);
        registerPacket(DataFlow.SERVER_TO_CLIENT, (byte) 0x02, SendSpeakerChatPacket.class);

    }
    private static Injector createInjector(ChatServerService chatServerService,ServerService sessionService,ChatBroadcastListener chatBroadcastListener) {
        Injector injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(ServerService.class).toInstance(sessionService);
                bind(ChatServerService.class).toInstance(chatServerService);
                bind(ChatBroadcastListener.class).toInstance(chatBroadcastListener);
            }
        });
        return injector;
    }
}
