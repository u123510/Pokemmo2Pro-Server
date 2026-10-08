package org.pokemmo.gameserver.game.story;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import lombok.extern.slf4j.Slf4j;

/** Loads all generic story chapters into one immutable startup snapshot. */
@Slf4j
public final class StoryCatalog {
    private static final List<String> REQUIRED_KEYS = List.of(
            "id", "version", "enabled", "triggers", "actors", "startNode", "nodes", "text"
    );

    private final Map<String, StoryProgram> programs;

    public StoryCatalog(Path directory) {
        Map<String, StoryProgram> loaded = new LinkedHashMap<>();
        if (directory == null || !Files.isDirectory(directory)) {
            throw new IllegalStateException("剧情目录不存在: " + directory);
        }
        try {
            List<Path> files = Files.walk(directory)
                    .filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().endsWith(".jsonc"))
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
            for (Path file : files) {
                StoryProgram program = read(file);
                if (loaded.putIfAbsent(program.id(), program) != null) {
                    throw new IllegalArgumentException("重复剧情 ID: " + program.id());
                }
            }
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("通用剧情目录加载失败: " + directory, exception);
        }
        if (loaded.isEmpty()) {
            throw new IllegalStateException("通用剧情目录没有 JSONC: " + directory);
        }
        programs = Map.copyOf(loaded);
        log.info("通用剧情目录加载完成: 章节数={}, 目录={}", programs.size(), directory);
        programs.values().stream()
                .sorted(Comparator.comparing(StoryProgram::id))
                .forEach(program -> log.info("通用剧情章节: id={}, version={}, enabled={}, source={}",
                        program.id(), program.version(), program.enabled(), program.source()));
    }

    public StoryCatalog() {
        programs = Map.of();
    }

    public Map<String, StoryProgram> programs() {
        return programs;
    }

    public StoryProgram get(String id) {
        return programs.get(id);
    }

    public boolean hasEnabledProgram() {
        return programs.values().stream().anyMatch(StoryProgram::enabled);
    }

    private static StoryProgram read(Path file) throws IOException {
        try (Reader source = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonReader reader = new JsonReader(source);
            reader.setLenient(true);
            JsonElement element = JsonParser.parseReader(reader);
            if (!element.isJsonObject()) {
                throw new IllegalArgumentException("剧情根节点必须是对象: " + file);
            }
            JsonObject root = element.getAsJsonObject();
            validate(root, file);
            return new StoryProgram(root, file.toString());
        }
    }

    private static void validate(JsonObject root, Path file) {
        List<String> missing = new ArrayList<>();
        for (String key : REQUIRED_KEYS) {
            if (!root.has(key)) {
                missing.add(key);
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("剧情配置缺少字段 " + missing + ": " + file);
        }
        String id = StoryProgram.string(root, "id");
        if (id == null || id.isBlank() || id.length() > 120) {
            throw new IllegalArgumentException("剧情 ID 无效: " + file);
        }
        if (StoryProgram.integer(root, "version", 0) < 1) {
            throw new IllegalArgumentException("剧情版本必须大于 0: " + file);
        }
        if (!root.get("triggers").isJsonArray() || !root.get("actors").isJsonObject()
                || !root.get("nodes").isJsonObject() || !root.get("text").isJsonObject()) {
            throw new IllegalArgumentException("剧情配置结构无效: " + file);
        }
        JsonObject nodes = root.getAsJsonObject("nodes");
        String startNode = StoryProgram.string(root, "startNode");
        if (startNode == null || !nodes.has(startNode)) {
            throw new IllegalArgumentException("剧情 startNode 不存在: " + file);
        }
        for (Map.Entry<String, JsonElement> entry : nodes.entrySet()) {
            if (!entry.getValue().isJsonObject()) {
                throw new IllegalArgumentException("剧情节点必须是对象: " + file + "#" + entry.getKey());
            }
            JsonObject node = entry.getValue().getAsJsonObject();
            JsonElement actions = node.get("actions");
            if (actions != null && !actions.isJsonArray()) {
                throw new IllegalArgumentException("剧情 actions 必须是数组: " + file + "#" + entry.getKey());
            }
        }
    }
}
