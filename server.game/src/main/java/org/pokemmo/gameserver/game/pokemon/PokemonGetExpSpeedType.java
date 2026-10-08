package org.pokemmo.gameserver.game.pokemon;

public enum PokemonGetExpSpeedType {
    FASTEST(0),
    FASTER(1),
    FAST(2),
    SLOWER(3),
    SLOW(4),
    SLOWEST(5);
    private byte type;
    public static final PokemonGetExpSpeedType[] allTypeArray = values();
    PokemonGetExpSpeedType(int type) {
        this.type = (byte)type;
    }
    public int getExpByLevel(int level) {
        switch (type) {
            case 0:
                if (level <= 50) {
                    return (int) ((java.lang.Math.pow(level, 3.0d) * (100 - level)) / 50.0d);
                }
                if (level <= 68) {
                    return (int) ((java.lang.Math.pow(level, 3.0d) * (150 - level)) / 100.0d);
                }
                if (level > 98) {
                    return (int) ((java.lang.Math.pow(level, 3.0d) * (160 - level)) / 100.0d);
                }
                return (int) ((((1911 - (level * 10)) / 3.0d) * java.lang.Math.pow(level, 3.0d)) / 500.0d);
            case 1:
                return (int) java.lang.Math.pow(level, 3.0d);
            case 2:
                return (int) ((java.lang.Math.pow(level, 3.0d) * 4.0d) / 5.0d);
            case 3:
                if (level <= 15) {
                    return (int) (((((level + 1) / 3.0d) + 24.0d) / 50.0d) * java.lang.Math.pow(level, 3.0d));
                }
                if (level <= 36) {
                    return (int) (((level + 14) / 50.0d) * java.lang.Math.pow(level, 3.0d));
                }
                double d = level;
                return (int) ((((d / 2.0d) + 32.0d) / 50.0d) * java.lang.Math.pow(d, 3.0d));
            case 4:
                return (int) ((java.lang.Math.pow(level, 3.0d) * 5.0d) / 4.0d);
            case 5:
                return (int) ((((java.lang.Math.pow(level, 3.0d) * 1.2d) - ((level * 15) * level)) + (level * 100)) - 140.0d);
            default:
                return 0;
        }
    }
    public int getLevelByExp(int currentExp) {
        // 经验值为0时返回等级1
        if (currentExp <= 0) {
            return 1;
        }
        // 遍历等级1到100级
        for (int level = 1; level <= 100; level++) {
            // 计算当前等级所需的经验值
            int levelExp = getExpByLevel(level);
            // 累加总经验值
            // 如果当前经验值小于总经验值，则当前等级为level
            if (currentExp < levelExp) {
                return level;
            }
        }
        // 超过100级的经验值仍返回100级
        return 100;
    }
    public byte getType() {
        return type;
    }
    public static PokemonGetExpSpeedType getByType(int type) {
        return allTypeArray[type];
    }
}
