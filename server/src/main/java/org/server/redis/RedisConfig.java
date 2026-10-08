package org.server.redis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
public class RedisConfig {
    private static final JedisPool jedisPool;
    static {
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(1000);
        poolConfig.setMaxIdle(10);
        poolConfig.setMinIdle(5);
        poolConfig.setTestOnBorrow(true);
        jedisPool = new JedisPool(poolConfig, "localhost", 6379, 3000);
    }

    public static JedisPool getJedisPool() {
        return jedisPool;
    }

    public static void close() {
        if (jedisPool != null) {
            jedisPool.close();
        }
    }
}
