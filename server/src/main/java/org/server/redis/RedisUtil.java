package org.server.redis;

import com.google.gson.Gson;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

public class RedisUtil {
    private static final JedisPool jedisPool = RedisConfig.getJedisPool();
    private static final Gson gson = new Gson();
    public static final String CHAT_QUEUE_KEY = "chat:queue";

    public static void pushToQueue(Object message) {
        try (Jedis jedis = jedisPool.getResource()) {
            String jsonMessage = gson.toJson(message);
            jedis.lpush(CHAT_QUEUE_KEY, jsonMessage);
        }
    }

    public static String popFromQueue() {
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.rpop(CHAT_QUEUE_KEY);
        }
    }

    public static <T> T fromJson(String json, Class<T> clazz) {
        return gson.fromJson(json, clazz);
    }
}
