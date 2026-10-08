package org.pokemmo.gameserver.util;

public class SnowflakeIdGenerator {
    // 起始时间戳 (2023-01-01 00:00:00)
    private static final long START_TIMESTAMP = System.currentTimeMillis();

    // 数据中心ID位数
    private static final long DATA_CENTER_ID_BITS = 5L;
    // 机器ID位数
    private static final long MACHINE_ID_BITS = 5L;
    // 序列号位数
    private static final long SEQUENCE_BITS = 12L;

    // 数据中心ID最大值
    private static final long MAX_DATA_CENTER_ID = ~(-1L << DATA_CENTER_ID_BITS);
    // 机器ID最大值
    private static final long MAX_MACHINE_ID = ~(-1L << MACHINE_ID_BITS);
    // 序列号最大值
    private static final long MAX_SEQUENCE = ~(-1L << SEQUENCE_BITS);
    // 机器ID左移位数
    private static final long MACHINE_ID_SHIFT = SEQUENCE_BITS;
    // 数据中心ID左移位数
    private static final long DATA_CENTER_ID_SHIFT = SEQUENCE_BITS + MACHINE_ID_BITS;
    // 时间戳左移位数
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + MACHINE_ID_BITS + DATA_CENTER_ID_BITS;

    private final long dataCenterId;
    private final long machineId;
    private long sequence = 0L;
    private static long lastTimestamp = -1L;

    /**
     * 构造函数
     * @param dataCenterId 数据中心ID (0-31)
     * @param machineId 机器ID (0-31)
     */
    public SnowflakeIdGenerator(long dataCenterId, long machineId) {
        if (dataCenterId > MAX_DATA_CENTER_ID || dataCenterId < 0) {
            throw new IllegalArgumentException("Data center ID can't be greater than " + MAX_DATA_CENTER_ID + " or less than 0");
        }
        if (machineId > MAX_MACHINE_ID || machineId < 0) {
            throw new IllegalArgumentException("Machine ID can't be greater than " + MAX_MACHINE_ID + " or less than 0");
        }
        this.dataCenterId = dataCenterId;
        this.machineId = machineId;
    }

    /**
     * 生成下一个ID
     * @return 雪花算法生成的ID
     */
    public synchronized long nextId() {
        long timestamp = System.currentTimeMillis();

        // 处理时钟回拨
        if (timestamp < lastTimestamp) {
            long offset = lastTimestamp - timestamp;
            if (offset > 5) {
                throw new RuntimeException("Clock moved backwards. Refusing to generate id for " + offset + " milliseconds");
            }
            try {
                wait(offset + 1);
                timestamp = System.currentTimeMillis();
                if (timestamp < lastTimestamp) {
                    throw new RuntimeException("Clock moved backwards. Refusing to generate id");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }

        // 同一毫秒内序列号自增
        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & MAX_SEQUENCE;
            // 序列号溢出
            if (sequence == 0) {
                timestamp = nextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }

        lastTimestamp = timestamp;

        // 组合ID
        return ((timestamp - START_TIMESTAMP) << TIMESTAMP_SHIFT) |
                (dataCenterId << DATA_CENTER_ID_SHIFT) |
                (machineId << MACHINE_ID_SHIFT) |
                sequence;
    }

    /**
     * 获取下一个毫秒数
     * @param lastTimestamp 上次时间戳
     * @return 下一个毫秒数
     */
    private long nextMillis(long lastTimestamp) {
        long timestamp = System.currentTimeMillis();
        while (timestamp <= lastTimestamp) {
            timestamp = System.currentTimeMillis();
        }
        return timestamp;
    }
}
