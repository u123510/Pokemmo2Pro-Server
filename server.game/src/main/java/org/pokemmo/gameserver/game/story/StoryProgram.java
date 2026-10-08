package org.pokemmo.gameserver.game.story;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** Immutable view of one data-driven story chapter. */
public final class StoryProgram {
    private final JsonObject root;
    private final String source;

    StoryProgram(JsonObject root, String source) {
        this.root = root;
        this.source = source;
    }

    public String id() {
        return string(root, "id");
    }

    public int version() {
        return integer(root, "version", 0);
    }

    public boolean enabled() {
        return bool(root, "enabled", false);
    }

    public String source() {
        return source;
    }

    public JsonObject resources() {
        return object(root, "resources");
    }

    public JsonObject actors() {
        return object(root, "actors");
    }

    public JsonObject text() {
        return object(root, "text");
    }

    public int text(String key) {
        return integer(text(), key, 0);
    }

    public List<JsonObject> triggers() {
        return objects(array(root, "triggers"));
    }

    public JsonObject node(String nodeId) {
        JsonObject nodes = object(root, "nodes");
        JsonElement value = nodes.get(nodeId);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
    }

    public Set<String> nodeIds() {
        return Collections.unmodifiableSet(object(root, "nodes").keySet());
    }

    public static String string(JsonObject object, String key) {
        JsonElement value = object == null ? null : object.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                ? value.getAsString() : null;
    }

    public static int integer(JsonObject object, String key, int fallback) {
        JsonElement value = object == null ? null : object.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            return fallback;
        }
        return value.getAsInt();
    }

    public static boolean bool(JsonObject object, String key, boolean fallback) {
        JsonElement value = object == null ? null : object.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
            return fallback;
        }
        return value.getAsBoolean();
    }

    public static JsonObject object(JsonObject object, String key) {
        JsonElement value = object == null ? null : object.get(key);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : new JsonObject();
    }

    public static JsonArray array(JsonObject object, String key) {
        JsonElement value = object == null ? null : object.get(key);
        return value != null && value.isJsonArray() ? value.getAsJsonArray() : new JsonArray();
    }

    private static List<JsonObject> objects(JsonArray array) {
        List<JsonObject> result = new ArrayList<>();
        for (JsonElement element : array) {
            if (element.isJsonObject()) {
                result.add(element.getAsJsonObject());
            }
        }
        return List.copyOf(result);
    }
}
