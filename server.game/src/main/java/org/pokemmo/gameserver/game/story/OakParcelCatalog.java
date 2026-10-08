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

@Slf4j
public final class OakParcelCatalog {
    public static final short BALL_ITEM_ID = 5004;
    public static final short BALL_COUNT = 5;
    public record Content(boolean enabled, Map<String, Integer> text) { }
    private final Content content;

    public OakParcelCatalog(Path directory) {
        Path file = directory.resolve("kanto/oak_parcel/chapter.jsonc");
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonReader jsonReader = new JsonReader(reader);
            jsonReader.setLenient(true);
            JsonObject root = JsonParser.parseReader(jsonReader).getAsJsonObject();
            Map<String, Integer> text = readText(root);
            if (text.isEmpty()) throw new IllegalArgumentException("缺少章节文本配置");
            for (String key : List.of("martGreeting", "martRequest", "parcelReceived", "martThanks", "oakGreeting",
                    "parcelDelivered", "oakThanks", "dexRequest", "dexDescription", "dexOffer", "dexReceived",
                    "ballIntroduction", "ballsReceived", "catchAdvice", "oakDream", "depart", "goToCity", "returnAdvice")) {
                Integer offset = text.get(key);
                if (offset == null || offset <= 0) throw new IllegalArgumentException("缺少大木包裹文本: " + key);
            }
            for (short id : new short[]{ItemManager.OAK_PARCEL_ITEM_ID, BALL_ITEM_ID}) {
                int needed = id == ItemManager.OAK_PARCEL_ITEM_ID ? 1 : BALL_COUNT;
                if (ItemManager.getItemData(id) == null || ItemManager.getItemData(id).getItemMaxStackSize() < needed) {
                    throw new IllegalArgumentException("章节所需道具资源无效: " + id);
                }
            }
            content = new Content(StoryProgram.bool(root, "enabled", false), Map.copyOf(text));
            log.info("大木包裹剧情配置加载完成: 文件={}, 启用={}", file, content.enabled());
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("大木包裹剧情配置加载失败: " + file, exception);
        }
    }

    public boolean enabled() { return content.enabled(); }
    public int text(String key) { return content.text().get(key); }

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
