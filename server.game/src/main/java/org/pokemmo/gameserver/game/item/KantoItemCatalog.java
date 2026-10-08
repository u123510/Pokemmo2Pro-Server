package org.pokemmo.gameserver.game.item;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Generated Kanto map pickup bindings translated from the original scripts. */
public final class KantoItemCatalog {
    public record Pickup(String map, int entityIdx, String script, int itemId,
                         int amount, String hideFlag) {
    }

    private final Map<String, Pickup> pickups = new HashMap<>();

    public KantoItemCatalog(Path file) {
        if (file == null || !Files.isRegularFile(file)) {
            return;
        }
        try (Reader source = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonReader reader = new JsonReader(source);
            reader.setLenient(true);
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            JsonElement values = root.get("pickups");
            if (values == null || !values.isJsonArray()) return;
            for (JsonElement value : values.getAsJsonArray()) {
                if (!value.isJsonObject()) continue;
                JsonObject item = value.getAsJsonObject();
                String map = string(item, "map");
                String script = string(item, "script");
                int itemId = integer(item, "itemId", 0);
                if (map == null || script == null || itemId <= 0) continue;
                pickups.put(key(map, script), new Pickup(
                        map,
                        integer(item, "entityIdx", -1),
                        script,
                        itemId,
                        Math.max(1, integer(item, "amount", 1)),
                        string(item, "hideFlag")));
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Kanto 道具拾取目录加载失败: " + file, exception);
        }
    }

    public Pickup find(String map, String script) {
        return pickups.get(key(map, script));
    }

    private static String key(String map, String script) {
        return map + "\u0000" + script;
    }

    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
    }

    private static int integer(JsonObject object, String key, int fallback) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()
                ? value.getAsInt() : fallback;
    }
}
