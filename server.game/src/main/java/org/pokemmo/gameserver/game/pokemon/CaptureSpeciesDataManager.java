package org.pokemmo.gameserver.game.pokemon;

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

/** Loads the Generation V species catch-rate and base-friendship table. */
@Slf4j
public final class CaptureSpeciesDataManager extends JsonUtil {
    private static final SpeciesData DEFAULT = new SpeciesData(45, 70);
    private static final Map<Integer, SpeciesData> LOADED = new HashMap<>();

    public CaptureSpeciesDataManager(String configPath) {
        load(configPath);
    }

    public SpeciesData getSpeciesData(int pokemonIndexId) {
        synchronized (LOADED) {
            return LOADED.getOrDefault(pokemonIndexId, DEFAULT);
        }
    }

    public static SpeciesData lookup(int pokemonIndexId) {
        synchronized (LOADED) {
            return LOADED.getOrDefault(pokemonIndexId, DEFAULT);
        }
    }

    private void load(String configPath) {
        if (configPath == null || configPath.isBlank()) {
            log.warn("未配置宝可梦捕获率资源，使用默认捕获率 {}", DEFAULT.catchRate());
            return;
        }
        File file = new File(configPath);
        if (!file.isFile()) {
            log.warn("未找到宝可梦捕获率资源，使用默认捕获率 {}: {}", DEFAULT.catchRate(), file.getPath());
            return;
        }
        try (JsonReader reader = new JsonReader(new FileReader(file))) {
            reader.setLenient(true);
            CaptureSpeciesConfig config = getGson().fromJson(reader, CaptureSpeciesConfig.class);
            if (config == null || config.species == null) return;
            Map<Integer, SpeciesData> loaded = new HashMap<>();
            for (SpeciesRule rule : config.species) {
                if (rule == null || rule.pokemonIndexId <= 0 || rule.pokemonIndexId > 0xFFFF
                        || rule.catchRate < 1 || rule.catchRate > 255
                        || rule.baseHappiness < 0 || rule.baseHappiness > 255) {
                    log.warn("忽略非法宝可梦捕获率配置: {}", rule);
                    continue;
                }
                loaded.put(rule.pokemonIndexId,
                        new SpeciesData(rule.catchRate, rule.baseHappiness));
            }
            synchronized (LOADED) {
                LOADED.clear();
                LOADED.putAll(Collections.unmodifiableMap(loaded));
            }
            log.info("成功加载 {} 条宝可梦捕获率配置", loaded.size());
        } catch (IOException | RuntimeException exception) {
            log.error("加载宝可梦捕获率资源失败: {}", file.getPath(), exception);
        }
    }

    public record SpeciesData(int catchRate, int baseHappiness) {
    }

    private static final class CaptureSpeciesConfig {
        private List<SpeciesRule> species;
    }

    private static final class SpeciesRule {
        private int pokemonIndexId = -1;
        private int catchRate = -1;
        private int baseHappiness = -1;

        @Override
        public String toString() {
            return "SpeciesRule{pokemonIndexId=" + pokemonIndexId
                    + ", catchRate=" + catchRate
                    + ", baseHappiness=" + baseHappiness + '}';
        }
    }
}
