package org.pokemmo.gameserver.game.npc;

import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

/** Flat, typed JSONC schema; rejects duplicates instead of silently taking the last value. */
final class CustomNpcCodec {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Set<String> BASE_FIELDS = Set.of("version", "enabled", "map", "regionId", "entityIdx",
            "spriteId", "spriteRegion", "movementType", "leashX", "leashY", "x", "y", "z", "toward");
    private static final Set<String> APPEARANCE_FIELDS = Set.of("eventId", "sparkles", "spriteScale");

    private CustomNpcCodec() {
    }

    static CustomNpcDefinition read(Reader source) throws IOException {
        JsonReader reader = new JsonReader(source);
        reader.setLenient(true);
        JsonObject value = new JsonObject();
        reader.beginObject();
        while (reader.hasNext()) {
            String field = reader.nextName();
            if ((!BASE_FIELDS.contains(field) && !APPEARANCE_FIELDS.contains(field)) || value.has(field)) {
                throw new IllegalArgumentException("自定义 NPC 字段未知或重复: " + field);
            }
            if (field.equals("map")) {
                require(reader, JsonToken.STRING, field);
                value.addProperty(field, reader.nextString());
            } else if (field.equals("enabled") || field.equals("sparkles")) {
                require(reader, JsonToken.BOOLEAN, field);
                value.addProperty(field, reader.nextBoolean());
            } else if (field.equals("spriteScale")) {
                require(reader, JsonToken.NUMBER, field);
                float scale = Float.parseFloat(reader.nextString());
                if (!Float.isFinite(scale)) throw new IllegalArgumentException("自定义 NPC 缩放必须是有限数值");
                value.addProperty(field, scale);
            } else {
                require(reader, JsonToken.NUMBER, field);
                try {
                    value.addProperty(field, new BigDecimal(reader.nextString()).intValueExact());
                } catch (ArithmeticException | NumberFormatException exception) {
                    throw new IllegalArgumentException("自定义 NPC 字段必须是 32 位整数: " + field, exception);
                }
            }
        }
        reader.endObject();
        if (reader.peek() != JsonToken.END_DOCUMENT || !value.keySet().containsAll(BASE_FIELDS)) {
            throw new IllegalArgumentException("自定义 NPC 配置字段不完整或存在多余内容");
        }
        int version = number(value, "version");
        if ((version == 1 && value.size() != BASE_FIELDS.size())
                || (version == 2 && !value.keySet().containsAll(APPEARANCE_FIELDS))) {
            throw new IllegalArgumentException("自定义 NPC 字段与配置版本不符：版本 1 无外观扩展，版本 2 必须完整填写外观扩展");
        }
        return new CustomNpcDefinition(version, value.get("enabled").getAsBoolean(),
                value.get("map").getAsString(), number(value, "regionId"), number(value, "entityIdx"),
                number(value, "spriteId"), number(value, "spriteRegion"), number(value, "movementType"),
                number(value, "leashX"), number(value, "leashY"), number(value, "x"), number(value, "y"),
                number(value, "z"), number(value, "toward"),
                version == 2 ? number(value, "eventId") : -1,
                version == 2 && value.get("sparkles").getAsBoolean(),
                version == 2 ? value.get("spriteScale").getAsFloat() : 1.0f);
    }

    static String write(CustomNpcDefinition definition) {
        JsonObject value = GSON.toJsonTree(definition).getAsJsonObject();
        if (definition.version() == 1) APPEARANCE_FIELDS.forEach(value::remove);
        return GSON.toJson(value) + System.lineSeparator();
    }

    private static int number(JsonObject value, String field) {
        return value.get(field).getAsInt();
    }

    private static void require(JsonReader reader, JsonToken token, String field) throws IOException {
        if (reader.peek() != token) throw new IllegalArgumentException("自定义 NPC 字段类型错误: " + field);
    }
}
