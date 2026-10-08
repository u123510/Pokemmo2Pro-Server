package org.pokemmo.gameserver.game.map;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Decodes the matrix/chunk terrain used by the openmmo-ds Sinnoh generator.
 */
public final class NdsTerrainPlane {
    private final int columns;
    private final int rows;
    private final int chunkSide;
    private final List<String> chunks;
    private final List<Integer> altitudes;
    private final List<Integer> headers;
    private final List<String> behaviors;
    private final Map<Integer, int[]> decodedChunks = new ConcurrentHashMap<>();

    public NdsTerrainPlane(
            int columns,
            int rows,
            int chunkSide,
            List<String> chunks,
            List<Integer> altitudes,
            List<Integer> headers,
            List<String> behaviors
    ) {
        this.columns = columns;
        this.rows = rows;
        this.chunkSide = chunkSide;
        this.chunks = chunks;
        this.altitudes = altitudes;
        this.headers = headers;
        this.behaviors = behaviors;
    }

    public int getWidth() {
        return columns * chunkSide;
    }

    public int getHeight() {
        return rows * chunkSide;
    }

    public boolean isWalkable(int x, int y) {
        if (x < 0 || y < 0 || x >= getWidth() || y >= getHeight()) {
            return false;
        }
        int raw = rawAt(x, y);
        return raw >= 0 && (raw & 0x8000) == 0;
    }

    public int getHeaderAt(int x, int y) {
        if (headers.isEmpty() || x < 0 || y < 0 || x >= getWidth() || y >= getHeight()) {
            return -1;
        }
        int cell = (y / chunkSide) * columns + (x / chunkSide);
        return cell < headers.size() ? headers.get(cell) : -1;
    }

    public String getBehaviorAt(int x, int y) {
        int raw = rawAt(x, y);
        int behaviorId = raw < 0 ? -1 : raw & 0xFF;
        return behaviorId >= 0 && behaviorId < behaviors.size()
                ? behaviors.get(behaviorId)
                : "NORMAL";
    }

    public boolean isGrass(int x, int y) {
        String behavior = getBehaviorAt(x, y);
        return "TALL_GRASS".equals(behavior) || "LONG_GRASS".equals(behavior);
    }

    public boolean isWater(int x, int y) {
        String behavior = getBehaviorAt(x, y);
        return "SURFABLE_WATER".equals(behavior) || "WATERFALL".equals(behavior);
    }

    public int getAltitudeAt(int x, int y) {
        if (altitudes.isEmpty() || x < 0 || y < 0 || x >= getWidth() || y >= getHeight()) {
            return -1;
        }
        int cell = (y / chunkSide) * columns + (x / chunkSide);
        return cell < altitudes.size() ? altitudes.get(cell) : -1;
    }

    private int rawAt(int x, int y) {
        int cell = (y / chunkSide) * columns + (x / chunkSide);
        if (cell < 0 || cell >= chunks.size() || chunks.get(cell) == null) {
            return -1;
        }
        int[] plane = decodedChunks.computeIfAbsent(cell, ignored ->
                decode(chunks.get(cell)));
        int local = (y % chunkSide) * chunkSide + (x % chunkSide);
        return local < plane.length ? plane[local] : -1;
    }

    private int[] decode(String encoded) {
        byte[] bytes = Base64.getDecoder().decode(encoded);
        int[] result = new int[bytes.length / 2];
        for (int index = 0; index < result.length; index++) {
            result[index] = (bytes[index * 2] & 0xFF)
                    | ((bytes[index * 2 + 1] & 0xFF) << 8);
        }
        return result;
    }
}
