package org.pokemmo.chatserver.chat;

import lombok.extern.slf4j.Slf4j;
import org.pokemmo.chatserver.protocol.s2c.SendSpeakerChatPacket;
import org.server.Session;
import org.server.redis.RedisUtil;
import org.server.union.chat.ChatMessage;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
@Slf4j
public class ChatBroadcastListener {
    private static final ConcurrentHashMap<Long, Session> speakerSessionPool = new ConcurrentHashMap<>();
    private static final ExecutorService executorService = Executors.newSingleThreadExecutor();
    public ChatBroadcastListener() {
        // 启动消息处理线程
        executorService.submit(this::processChatMessages);
    }
    private void broadcastMessage(ChatMessage chatMessage) {
        // 创建广播包
        SendSpeakerChatPacket chatPacket = new SendSpeakerChatPacket(
                chatMessage.getChatType(),
                chatMessage.getSenderId(),
                chatMessage.getSender(),
                chatMessage.getLanguageType(),
                chatMessage.getSenderPermission(),
                chatMessage.getMessage()
        );
        // 广播给所有在线玩家
        for (Session session : speakerSessionPool.values()) {
            try {
                session.send(chatPacket);
            } catch (Exception e) {
                log.error("广播玩家聊天消息失败", e);
            }
        }
    }
    private void processChatMessages() {
        while (true) {
            try {
                String messageJson = RedisUtil.popFromQueue();
                if (messageJson != null) {
                    ChatMessage chatMessage = RedisUtil.fromJson(messageJson, ChatMessage.class);
                    broadcastMessage(chatMessage);
                } else {
                    // 避免忙等，短暂休眠
                    Thread.sleep(100);
                }
            } catch (Exception e) {
                log.error("发送消息错误", e);
            }
        }
    }
    public Session getSpeakerSessionInPool(long playerId) {
        return speakerSessionPool.get(playerId);
    }
    public void addSpeakerSessionToPool(long playerId,Session session) {
        speakerSessionPool.put(playerId,session);
    }
}
