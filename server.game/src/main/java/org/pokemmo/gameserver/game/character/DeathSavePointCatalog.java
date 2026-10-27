package org.pokemmo.gameserver.game.character;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Immutable Unova checkpoints, including read compatibility for legacy IDs 21..34. */
public final class DeathSavePointCatalog {
    public static final int HOME_ID = 200;
    private final Map<Integer, DeathSavePoint> byId;
    private final Map<String, DeathSavePoint> bySourceMap;

    public DeathSavePointCatalog(Path file) {
        Map<Integer, DeathSavePoint> ids = new HashMap<>();
        Map<String, DeathSavePoint> sources = new HashMap<>();
        Set<String> keys = new HashSet<>();
        try {
            int lineNumber = 0;
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                lineNumber++;
                String text = line.strip();
                if (text.isEmpty() || text.startsWith("#")) continue;
                try {
                    DeathSavePoint point = parse(text);
                    if (ids.putIfAbsent(point.id(), point) != null
                            || sources.putIfAbsent(point.sourceMap(), point) != null
                            || !keys.add(point.key())) {
                        throw new IllegalArgumentException("重复编号、key 或触发地图");
                    }
                } catch (IllegalArgumentException exception) {
                    throw new IllegalArgumentException(file + ":" + lineNumber + ": "
                            + exception.getMessage(), exception);
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("无法加载合众死亡存盘点: " + file, exception);
        }
        if (ids.size() != 14 || !ids.containsKey(HOME_ID)
                || !"UNOVA_HOME".equals(ids.get(HOME_ID).key())) {
            throw new IllegalArgumentException("合众死亡存盘点必须包含 200..213 和 UNOVA_HOME=200");
        }
        byId = Map.copyOf(ids);
        bySourceMap = Map.copyOf(sources);
    }

    private static DeathSavePoint parse(String text) {
        String[] fields = text.split("\\s+");
        if (fields.length != 10 || !"POINT".equals(fields[0])) {
            throw new IllegalArgumentException("应为 POINT 和 9 个字段");
        }
        return new DeathSavePoint(Integer.parseInt(fields[1]), fields[2], fields[3], fields[4],
                Short.parseShort(fields[5]), Short.parseShort(fields[6]), Byte.parseByte(fields[7]),
                Integer.parseInt(fields[8]), Integer.parseInt(fields[9]));
    }

    public DeathSavePoint get(int storedId) {
        int normalized = storedId >= 21 && storedId <= 34 ? storedId + 179 : storedId;
        DeathSavePoint point = byId.get(normalized);
        if (point == null) throw new IllegalArgumentException("未知的合众死亡存盘点: " + storedId);
        return point;
    }

    public DeathSavePoint forSourceMap(String mapKey) {
        return mapKey == null ? null : bySourceMap.get(mapKey);
    }
}
