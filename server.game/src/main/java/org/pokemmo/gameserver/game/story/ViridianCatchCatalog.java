package org.pokemmo.gameserver.game.story;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import lombok.extern.slf4j.Slf4j;
import org.pokemmo.gameserver.game.item.ItemManager;
import org.pokemmo.gameserver.game.pokemon.PokemonManager;

/** Configuration for the Viridian City capture tutorial. */
@Slf4j
public final class ViridianCatchCatalog {
    public static final int TUTORIAL_SPECIES = 13;
    public static final short TUTORIAL_LEVEL = 5;
    public static final short BALL_ITEM_ID = 5004;

    public record Content(boolean enabled, int wildSpecies, short wildLevel, Map<String, Integer> text) {
    }

    private final Content content;

    public ViridianCatchCatalog(Path directory) {
        Path file = directory.resolve("kanto/viridian_city/catch_tutorial/chapter.jsonc");
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonReader jsonReader = new JsonReader(reader);
            jsonReader.setLenient(true);
            JsonObject root = JsonParser.parseReader(jsonReader).getAsJsonObject();
            Map<String, Integer> text = readText(root);
            JsonObject resources = StoryProgram.object(root, "resources");
            int wildSpecies = StoryProgram.integer(resources, "wildSpecies", 0);
            int wildLevel = StoryProgram.integer(resources, "wildLevel", 0);
            if (text.isEmpty() || wildSpecies <= 0 || wildLevel <= 0 || wildLevel > 100) {
                throw new IllegalArgumentException("常磐市捕获教学配置结构不完整");
            }
            for (String key : List.of("introduction", "completed")) {
                Integer offset = text.get(key);
                if (offset == null || offset <= 0) {
                    throw new IllegalArgumentException("缺少常磐市捕获教学文本: " + key);
                }
            }
            if (PokemonManager.getPokemonoexData(wildSpecies) == null) {
                throw new IllegalArgumentException("捕获教学宝可梦未加载: " + wildSpecies);
            }
            if (ItemManager.getItemData(BALL_ITEM_ID) == null
                    || !ItemManager.getItemData(BALL_ITEM_ID).isCaptureBall()) {
                throw new IllegalArgumentException("捕获教学所需精灵球资源无效: " + BALL_ITEM_ID);
            }
            content = new Content(StoryProgram.bool(root, "enabled", false), wildSpecies, (short) wildLevel,
                    Map.copyOf(text));
            log.info("常磐市捕获教学配置加载完成: 文件={}, 启用={}, 宝可梦={}, 等级={}",
                    file, content.enabled(), content.wildSpecies(), content.wildLevel());
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("常磐市捕获教学配置加载失败: " + file, exception);
        }
    }

    public boolean enabled() {
        return content.enabled();
    }

    public int wildSpecies() {
        return content.wildSpecies();
    }

    public short wildLevel() {
        return content.wildLevel();
    }

    public int text(String key) {
        return content.text().get(key);
    }

    private static Map<String, Integer> readText(JsonObject root) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : StoryProgram.object(root, "text").entrySet()) {
            if (entry.getValue().isJsonPrimitive() && entry.getValue().getAsJsonPrimitive().isNumber()) {
                result.put(entry.getKey(), entry.getValue().getAsInt());
            }
        }
        return result;
    }
}
