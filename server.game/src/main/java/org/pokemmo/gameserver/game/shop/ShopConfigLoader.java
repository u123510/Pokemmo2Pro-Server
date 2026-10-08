package org.pokemmo.gameserver.game.shop;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import org.pokemmo.gameserver.game.item.ItemData;
import org.pokemmo.gameserver.game.item.ItemManager;
import org.pokemmo.gameserver.game.item.ItemType;
import org.pokemmo.gameserver.game.map.MapData;
import lombok.extern.slf4j.Slf4j;

/** Parses independent shop files without publishing partially reloaded state. */
@Slf4j
final class ShopConfigLoader {
    private static final long MAX_FILE_BYTES = 1024 * 1024;
    private static final Set<String> SHOP_FIELDS = Set.of("shopId", "buyEnabled", "sellEnabled", "items", "npcs");
    private static final Set<String> ITEM_FIELDS = Set.of("itemId", "buyPrice", "sellPrice");
    private static final Set<String> NPC_FIELDS = Set.of("map", "entityIdx");

    record Loaded(Map<String, ShopDefinition> shops, ShopNpcBindings bindings, List<String> errors) {
    }

    Loaded load(Path directory, List<MapData> maps) {
        Map<String, ShopDefinition> shops = new HashMap<>();
        Map<String, Path> origins = new HashMap<>();
        Set<String> duplicatedIds = new HashSet<>();
        List<String> errors = new ArrayList<>();
        if (!Files.isDirectory(directory)) {
            errors.add("店铺目录不存在: " + directory);
            return new Loaded(Map.of(), ShopNpcBindings.compile(shops, maps, origins, errors),
                    List.copyOf(errors));
        }
        try (Stream<Path> paths = Files.walk(directory)) {
            for (Path file : paths.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(ShopConfigLoader::isShopFile).sorted().toList()) {
                try {
                    ShopDefinition definition = read(file);
                    log.debug("读取店铺配置成功: 文件={}, shopId={}", file, definition.shopId());
                    Path previous = origins.putIfAbsent(definition.shopId(), file);
                    if (previous != null) {
                        duplicatedIds.add(definition.shopId());
                        shops.remove(definition.shopId());
                        errors.add(file + ": shopId 重复，与 " + previous + " 冲突");
                    } else if (!duplicatedIds.contains(definition.shopId())) {
                        shops.put(definition.shopId(), definition);
                    }
                } catch (IOException | RuntimeException exception) {
                    log.error("读取店铺配置失败: 文件={}, 原因={}", file, exception.getMessage());
                    errors.add(file + ": " + exception.getMessage());
                }
            }
        } catch (IOException | RuntimeException exception) {
            errors.add("扫描店铺目录失败: " + directory + ": " + exception.getMessage());
        }
        ShopNpcBindings bindings = ShopNpcBindings.compile(shops, maps, origins, errors);
        return new Loaded(Map.copyOf(shops), bindings, List.copyOf(errors));
    }

    private ShopDefinition read(Path file) throws IOException {
        if (Files.size(file) > MAX_FILE_BYTES) {
            throw new IOException("店铺配置超过 1 MiB");
        }
        try (JsonReader reader = new JsonReader(Files.newBufferedReader(file, StandardCharsets.UTF_8))) {
            reader.setLenient(true);
            JsonElement document = JsonParser.parseReader(reader);
            if (!document.isJsonObject() || reader.peek() != JsonToken.END_DOCUMENT) {
                throw new IllegalArgumentException("店铺文件必须只包含一个 JSON 对象");
            }
            JsonObject root = document.getAsJsonObject();
            checkFields(root, SHOP_FIELDS);
            if (!root.has("shopId") || !root.get("shopId").isJsonPrimitive()
                    || !root.getAsJsonPrimitive("shopId").isString()) {
                throw new IllegalArgumentException("缺少字符串 shopId");
            }
            if (!root.has("items") || !root.get("items").isJsonArray()) {
                throw new IllegalArgumentException("缺少 items 数组");
            }
            if (root.getAsJsonArray("items").size() > ShopDefinition.MAX_ITEMS) {
                throw new IllegalArgumentException("items 数量不能超过 1024");
            }
            List<ShopItem> items = new ArrayList<>();
            for (JsonElement element : root.getAsJsonArray("items")) {
                if (!element.isJsonObject()) {
                    throw new IllegalArgumentException("items 中每个商品必须是 JSON 对象");
                }
                JsonObject entry = element.getAsJsonObject();
                checkFields(entry, ITEM_FIELDS);
                int id = integer(entry, "itemId");
                ShopItem item = new ShopItem(id, price(entry, "buyPrice"), price(entry, "sellPrice"));
                ItemData metadata = ItemManager.getItemData((short) id);
                if (metadata == null || metadata.getItemMaxStackSize() <= 0) {
                    throw new IllegalArgumentException("道具不存在或堆叠上限无效: " + id);
                }
                if (metadata.isBindAccount() || metadata.getItemType() == ItemType.KEY_ITEMS
                        || !ItemManager.isTradeableForExchange((short) id)) {
                    throw new IllegalArgumentException("禁止配置账号绑定、关键或不可交易道具: " + id);
                }
                items.add(item);
            }
            return new ShopDefinition(root.get("shopId").getAsString(),
                    bool(root, "buyEnabled"), bool(root, "sellEnabled"), items, readNpcBindings(root));
        }
    }

    static List<ShopNpcBinding> readNpcBindings(JsonObject root) {
        if (!root.has("npcs")) return null;
        JsonElement value = root.get("npcs");
        if (!value.isJsonArray() || value.getAsJsonArray().size() > ShopDefinition.MAX_NPCS) {
            throw new IllegalArgumentException("npcs 必须是数组，最多 1024 项；不绑定任何 NPC 请填写 []");
        }
        List<ShopNpcBinding> bindings = new ArrayList<>();
        for (JsonElement entry : value.getAsJsonArray()) {
            if (!entry.isJsonObject()) {
                throw new IllegalArgumentException("npcs 中每项必须是包含 map 和 entityIdx 的对象");
            }
            JsonObject npc = entry.getAsJsonObject();
            checkFields(npc, NPC_FIELDS);
            JsonElement map = npc.get("map");
            if (map == null || !map.isJsonPrimitive() || !map.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException("npcs.map 必须是地图文件名字符串");
            }
            bindings.add(new ShopNpcBinding(map.getAsString(), integer(npc, "entityIdx")));
        }
        return bindings;
    }

    private static boolean isShopFile(Path file) {
        String name = file.getFileName().toString();
        return name.endsWith(".json") || name.endsWith(".jsonc");
    }

    private static void checkFields(JsonObject object, Set<String> allowed) {
        for (String key : object.keySet()) {
            if (!allowed.contains(key)) {
                throw new IllegalArgumentException("未知配置字段: " + key);
            }
        }
    }

    private static int integer(JsonObject object, String name) {
        JsonElement value = object.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(name + " 必须是整数");
        }
        try {
            return value.getAsBigDecimal().intValueExact();
        } catch (ArithmeticException | NumberFormatException exception) {
            throw new IllegalArgumentException(name + " 必须是 32 位有符号整数", exception);
        }
    }

    private static Integer price(JsonObject object, String name) {
        return !object.has(name) || object.get(name).isJsonNull() ? null : integer(object, name);
    }

    private static boolean bool(JsonObject object, String name) {
        JsonElement value = object.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException(name + " 必须是 true 或 false");
        }
        return value.getAsBoolean();
    }
}
