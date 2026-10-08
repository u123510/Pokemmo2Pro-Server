package org.pokemmo.gameserver.game.item;

import com.google.gson.stream.JsonReader;
import lombok.extern.slf4j.Slf4j;
import org.pokemmo.gameserver.util.JsonUtil;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Loads small server-side item overrides without duplicating Item.bin metadata. */
@Slf4j
public final class ItemUseManager extends JsonUtil {
    private final Map<Short, ItemUseRule> rules;

    public ItemUseManager(String configPath) {
        this.rules = loadRules(configPath);
    }

    public ItemUseRule getRule(short itemIndexId) {
        return rules.get(itemIndexId);
    }

    public Map<Short, ItemUseRule> getRules() {
        return rules;
    }

    private Map<Short, ItemUseRule> loadRules(String configPath) {
        if (configPath == null || configPath.isBlank()) {
            return Map.of();
        }
        File file = new File(configPath);
        if (!file.isFile()) {
            log.info("未找到道具使用覆盖配置，使用 Item.bin 自动识别: {}", file.getPath());
            return Map.of();
        }

        try (JsonReader reader = new JsonReader(new FileReader(file))) {
            reader.setLenient(true);
            ItemUseConfig config = getGson().fromJson(reader, ItemUseConfig.class);
            if (config == null || config.getItemEffects() == null) {
                return Map.of();
            }
            Map<Short, ItemUseRule> loaded = new HashMap<>();
            for (ItemUseRule rule : config.getItemEffects()) {
                if (rule == null || rule.getItemIndexId() <= 0 || rule.getItemIndexId() > 0xFFFF
                        || rule.getHandler() == null) {
                    log.warn("忽略非法道具使用规则: {}", rule);
                    continue;
                }
                if (rule.getConsumeAmount() < 0 || rule.getAmount() < 0) {
                    log.warn("忽略负数道具使用规则: itemIndexId={}, rule={}",
                            rule.getItemIndexId(), rule);
                    continue;
                }
                loaded.put((short) rule.getItemIndexId(), rule);
            }
            log.info("成功加载 {} 条道具使用覆盖规则", loaded.size());
            return Collections.unmodifiableMap(loaded);
        } catch (IOException | RuntimeException exception) {
            log.error("加载道具使用覆盖配置失败: {}", file.getPath(), exception);
            return Map.of();
        }
    }
}
