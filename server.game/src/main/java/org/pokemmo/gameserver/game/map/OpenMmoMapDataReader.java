package org.pokemmo.gameserver.game.map;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

/**
 * Decodes the generated map representation used by openmmo老外.
 *
 * blockData contains one little-endian 16-bit GBA map-grid block per tile.
 * behaviorData contains one Kanto/Hoenn behavior enum ordinal per tile.
 */
public final class OpenMmoMapDataReader {
    private OpenMmoMapDataReader() {
    }

    public static MapCoordinateData[][] read(OpenMmoMapConfig config) throws IOException {
        byte[] blockData = decode(config.blockData, "blockData");
        byte[] behaviorData = decode(config.behaviorData, "behaviorData");
        int expectedTiles = Math.multiplyExact(config.width, config.height);
        if (blockData.length != expectedTiles * 2) {
            throw new IOException("blockData 长度与地图尺寸不一致");
        }
        if (!config.behaviorData.isBlank() && behaviorData.length != expectedTiles) {
            throw new IOException("behaviorData 长度与地图尺寸不一致");
        }

        Set<Integer> counters = readInteractionCounters(config);
        MapCoordinateData[][] coordinates = new MapCoordinateData[config.width][config.height];
        for (int x = 0; x < config.width; x++) {
            for (int y = 0; y < config.height; y++) {
                int index = y * config.width + x;
                int raw = (blockData[index * 2] & 0xFF)
                        | ((blockData[index * 2 + 1] & 0xFF) << 8);
                short tileId = (short) (raw & 0x03FF);
                byte tileAttribute = (byte) ((raw >>> 10) & 0x3F);
                byte behavior = behaviorData.length == 0
                        ? KantoMetaTileBehaviorType.MB_NORMAL.getType()
                        : behaviorOrdinalToType(behaviorData[index] & 0xFF);
                if (counters.contains(index)) {
                    if (behavior != KantoMetaTileBehaviorType.MB_NORMAL.getType()) {
                        throw new IOException("柜台标记不能覆盖已有地形行为: " + x + "," + y);
                    }
                    behavior = KantoMetaTileBehaviorType.MB_COUNTER.getType();
                }
                coordinates[x][y] = new MapCoordinateData(
                        (short) x,
                        (short) y,
                        tileId,
                        tileAttribute,
                        behavior
                );
            }
        }
        return coordinates;
    }

    private static Set<Integer> readInteractionCounters(OpenMmoMapConfig config) throws IOException {
        if (config.interactionCounters == null) {
            throw new IOException("interactionCounters 不能为 null");
        }
        Set<Integer> counters = new HashSet<>();
        for (OpenMmoMapConfig.InteractionCounterConfig counter : config.interactionCounters) {
            if (counter == null || counter.x < 0 || counter.y < 0
                    || counter.x >= config.width || counter.y >= config.height) {
                throw new IOException("柜台坐标缺失或超出地图边界");
            }
            if (!counters.add(counter.y * config.width + counter.x)) {
                throw new IOException("柜台坐标重复: " + counter.x + "," + counter.y);
            }
        }
        return counters;
    }

    private static byte[] decode(String value, String name) throws IOException {
        if (value == null || value.isBlank()) {
            return new byte[0];
        }
        try {
            return Base64.getDecoder().decode(value.getBytes(StandardCharsets.US_ASCII));
        } catch (IllegalArgumentException exception) {
            throw new IOException(name + " 不是合法 Base64", exception);
        }
    }

    private static byte behaviorOrdinalToType(int ordinal) throws IOException {
        byte[] behaviorTypes = {
                KantoMetaTileBehaviorType.MB_NORMAL.getType(),
                KantoMetaTileBehaviorType.MB_TALL_GRASS.getType(),
                KantoMetaTileBehaviorType.MB_TALL_GRASS.getType(),
                KantoMetaTileBehaviorType.MB_JUMP_EAST.getType(),
                KantoMetaTileBehaviorType.MB_JUMP_WEST.getType(),
                KantoMetaTileBehaviorType.MB_JUMP_NORTH.getType(),
                KantoMetaTileBehaviorType.MB_JUMP_SOUTH.getType(),
                KantoMetaTileBehaviorType.MB_WARP_DOOR.getType(),
                KantoMetaTileBehaviorType.MB_CAVE_DOOR.getType(),
                KantoMetaTileBehaviorType.MB_LADDER.getType(),
                KantoMetaTileBehaviorType.MB_UP_RIGHT_STAIR_WARP.getType(),
                KantoMetaTileBehaviorType.MB_UP_LEFT_STAIR_WARP.getType(),
                KantoMetaTileBehaviorType.MB_NORTH_ARROW_WARP.getType(),
                KantoMetaTileBehaviorType.MB_SOUTH_ARROW_WARP.getType(),
                KantoMetaTileBehaviorType.MB_EAST_ARROW_WARP.getType(),
                KantoMetaTileBehaviorType.MB_WEST_ARROW_WARP.getType()
        };
        if (ordinal < 0 || ordinal >= behaviorTypes.length) {
            throw new IOException("未知 metatile behavior ordinal: " + ordinal);
        }
        return behaviorTypes[ordinal];
    }
}
