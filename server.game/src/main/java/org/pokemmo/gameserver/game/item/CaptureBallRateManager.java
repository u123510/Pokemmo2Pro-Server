package org.pokemmo.gameserver.game.item;

import com.google.gson.stream.JsonReader;
import lombok.extern.slf4j.Slf4j;
import org.pokemmo.gameserver.util.JsonUtil;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Loads optional Generation V ball-bonus overrides keyed by item or ball type. */
@Slf4j
public final class CaptureBallRateManager extends JsonUtil {
    private static final double DEFAULT_MULTIPLIER = 1.0d;
    private static final Map<Integer, Double> DEFAULT_BALL_MULTIPLIERS = Map.of(
            1, 2.0d,
            2, 1.5d,
            4, 1.5d,
            23, 1.5d);

    private final Map<Integer, Double> itemMultipliers;
    private final Map<Integer, Double> ballTypeMultipliers;

    public CaptureBallRateManager(String configPath) {
        LoadedRates rates = loadRates(configPath);
        this.itemMultipliers = rates.itemMultipliers();
        this.ballTypeMultipliers = rates.ballTypeMultipliers();
    }

    public double getCatchRateMultiplier(short itemIndexId, byte ballType) {
        int itemId = Short.toUnsignedInt(itemIndexId);
        int type = Byte.toUnsignedInt(ballType);
        return itemMultipliers.getOrDefault(itemId,
                ballTypeMultipliers.getOrDefault(type,
                        DEFAULT_BALL_MULTIPLIERS.getOrDefault(type, DEFAULT_MULTIPLIER)));
    }

    public Map<Integer, Double> getBallTypeMultipliers() {
        return ballTypeMultipliers;
    }

    private LoadedRates loadRates(String configPath) {
        if (configPath == null || configPath.isBlank()) {
            return emptyRates();
        }
        File file = new File(configPath);
        if (!file.isFile()) {
            log.warn("未找到精灵球倍率配置，使用第五世代内置倍率: {}", file.getPath());
            return emptyRates();
        }
        try (JsonReader reader = new JsonReader(new FileReader(file))) {
            reader.setLenient(true);
            CaptureBallConfig config = getGson().fromJson(reader, CaptureBallConfig.class);
            if (config == null || config.captureBalls == null) {
                return emptyRates();
            }
            Map<Integer, Double> byItem = new HashMap<>();
            Map<Integer, Double> byBallType = new HashMap<>();
            for (CaptureBallRule rule : config.captureBalls) {
                if (rule == null) continue;
                boolean validItem = rule.itemIndexId > 0 && rule.itemIndexId <= 0xFFFF;
                boolean validBallType = rule.ballType >= 0 && rule.ballType <= 24;
                double multiplier = rule.catchRateMultiplier;
                if ((!validItem && !validBallType) || !Double.isFinite(multiplier)
                        || multiplier < 0d || multiplier > 20d) {
                    log.warn("忽略非法精灵球倍率配置: {}", rule);
                    continue;
                }
                if (validItem) byItem.put(rule.itemIndexId, multiplier);
                else byBallType.put(rule.ballType, multiplier);
            }
            log.info("成功加载 {} 条精灵球倍率配置（道具 {}, 球种 {}）",
                    byItem.size() + byBallType.size(), byItem.size(), byBallType.size());
            return new LoadedRates(Collections.unmodifiableMap(byItem),
                    Collections.unmodifiableMap(byBallType));
        } catch (IOException | RuntimeException exception) {
            log.error("加载精灵球倍率配置失败: {}", file.getPath(), exception);
            return emptyRates();
        }
    }

    private static LoadedRates emptyRates() {
        return new LoadedRates(Map.of(), Map.of());
    }

    private record LoadedRates(Map<Integer, Double> itemMultipliers,
                               Map<Integer, Double> ballTypeMultipliers) {}

    private static final class CaptureBallConfig {
        private List<CaptureBallRule> captureBalls;
    }

    private static final class CaptureBallRule {
        private int itemIndexId = -1;
        private int ballType = -1;
        private double catchRateMultiplier = -1d;

        @Override
        public String toString() {
            return "CaptureBallRule{itemIndexId=" + itemIndexId
                    + ", ballType=" + ballType
                    + ", catchRateMultiplier=" + catchRateMultiplier + '}';
        }
    }
}
