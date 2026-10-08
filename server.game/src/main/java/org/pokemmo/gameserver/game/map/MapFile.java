package org.pokemmo.gameserver.game.map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.pokemmo.gameserver.game.entity.MovementType;
import org.pokemmo.gameserver.game.entity.NpcEntity;
import org.pokemmo.gameserver.game.region.RegionType;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Locale;

/**
 * Loads the MapDef JSON emitted by the openmmo map generator.
 *
 * Legacy JSONC and sidecar BIN resources are intentionally unsupported.
 */
@Getter
@Setter
@Slf4j
public class MapFile {
    private File jsonFile;

    private final Gson gson = new GsonBuilder().create();

    public MapData parseMapData() {
        try (Reader reader = new FileReader(jsonFile)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            MapData map;
            if (root.has("terrain") && root.has("regionId")) {
                map = convert(gson.fromJson(root, SinnohMapConfig.class));
            } else {
                map = convert(gson.fromJson(root, OpenMmoMapConfig.class));
            }
            if (map == null) {
                return null;
            }
            if (map != null) {
                String fileName = jsonFile.getName();
                int extensionIndex = fileName.lastIndexOf('.');
                map.setMapKey(extensionIndex > 0
                        ? fileName.substring(0, extensionIndex)
                        : fileName);
            }
            return map;
        } catch (IOException | RuntimeException exception) {
            log.warn("加载 openmmo 地图失败: {}", jsonFile, exception);
            return null;
        }
    }

    private MapData convert(OpenMmoMapConfig config) throws IOException {
        RegionType region = RegionType.getByType(config.regionId);
        if (region != RegionType.KANTO && region != RegionType.HOENN) {
            log.warn("跳过不支持的地图地区: {}", config.regionId);
            return null;
        }
        return createGbaMap(config);
    }

    private MapData convert(SinnohMapConfig config) throws IOException {
        if (config.regionId != 3 || config.terrain == null) {
            log.warn("跳过无效的 NDS 地图定义: {}", config.name);
            return null;
        }
        NdsTerrainPlane terrain = new NdsTerrainPlane(
                config.terrain.cols,
                config.terrain.rows,
                config.terrain.chunkSide,
                config.terrain.chunks,
                config.terrain.altitudes,
                config.terrain.headers,
                config.terrainBehaviors
        );
        SinnohMapData map = new SinnohMapData(
                config,
                lighting(config.lighting),
                weather(config.weather),
                zone(config.mapType),
                terrain
        );
        for (int index = 0; index < config.warps.size(); index++) {
            OpenMmoMapConfig.WarpConfig warp = config.warps.get(index);
            map.addWarpEvent(new WarpEvent(
                    "warp_" + index,
                    warp.x,
                    warp.y,
                    warp.elevation,
                    directionCode(warp.facingDirection, -1),
                    warp.targetRegionId,
                    warp.targetBankId,
                    warp.targetMapId,
                    warp.targetX,
                    warp.targetY,
                    warp.targetElevation,
                    directionCode(warp.exitFacing, -1)
            ));
        }
        if (MapLoadingOptions.areNpcsEnabled()) {
            for (OpenMmoMapConfig.NpcConfig npc : config.npcs) {
                map.addEntity(toNpc(npc, map));
            }
        }
        if (MapLoadingOptions.areEventsEnabled()) {
            for (OpenMmoMapConfig.CoordScriptConfig event : config.coordScripts) {
                map.addCoordinateEvent(new CoordinateEvent(
                        event.script,
                        event.x,
                        event.y,
                        event.elevation
                ));
            }
            for (int index = 0; index < config.bgEvents.size(); index++) {
                OpenMmoMapConfig.BgEventConfig event = config.bgEvents.get(index);
                map.addBgEvent(new BgEvent(
                        (short) index,
                        (short) event.x,
                        (short) event.y,
                        (byte) event.elevation
                ));
            }
        }
        return map;
    }

    private KantoregionMapData createGbaMap(OpenMmoMapConfig config) throws IOException {
        int borderTileCount = config.borderWidth * config.borderHeight;
        Tile2D[] borderTiles = new Tile2D[borderTileCount];
        if (!config.borderTiles.isEmpty() && config.borderTiles.size() != borderTileCount) {
            throw new IOException("borderTiles 数量与边界尺寸不一致");
        }
        for (int i = 0; i < borderTileCount; i++) {
            if (config.borderTiles.isEmpty()) {
                borderTiles[i] = new Tile2D(8, 0);
            } else {
                OpenMmoMapConfig.TileConfig tile = config.borderTiles.get(i);
                borderTiles[i] = new Tile2D(tile.material, tile.collision);
            }
        }

        KantoregionMapData map = new KantoregionMapData(
                config.regionId,
                config.bankId,
                config.mapId,
                0,
                0,
                2,
                config.width,
                config.height,
                config.paletteIdx1,
                config.paletteIdx2,
                config.borderWidth,
                config.borderHeight,
                config.unknownShort,
                signedByte(config.unknownByte),
                lighting(config.lighting),
                weather(config.weather),
                zone(config.mapType),
                borderTiles,
                isOutdoor(config.mapType),
                true,
                false,
                true,
                0,
                encounter(config.encounterType)
        );

        for (OpenMmoMapConfig.ConnectionConfig connection : config.connections) {
            MapConnectionType type = connectionType(connection.direction);
            map.addMapConnection(
                    type,
                    new MapConnection(type, connection.unknown, connection.targetBank, connection.targetMap)
            );
        }
        for (int index = 0; index < config.warps.size(); index++) {
            OpenMmoMapConfig.WarpConfig warp = config.warps.get(index);
            map.addWarpEvent(new WarpEvent(
                    "warp_" + index,
                    warp.x,
                    warp.y,
                    warp.elevation,
                    directionCode(warp.facingDirection, -1),
                    warp.targetRegionId,
                    warp.targetBankId,
                    warp.targetMapId,
                    warp.targetX,
                    warp.targetY,
                    warp.targetElevation,
                    directionCode(warp.exitFacing, -1)
            ));
        }
        if (MapLoadingOptions.areEventsEnabled()) {
            for (OpenMmoMapConfig.CoordScriptConfig event : config.coordScripts) {
                map.addCoordinateEvent(new CoordinateEvent(
                        event.script,
                        event.x,
                        event.y,
                        event.elevation
                ));
            }
            for (int index = 0; index < config.bgEvents.size(); index++) {
                OpenMmoMapConfig.BgEventConfig event = config.bgEvents.get(index);
                map.addBgEvent(new BgEvent(
                        (short) index,
                        (short) event.x,
                        (short) event.y,
                        (byte) event.elevation
                ));
            }
        }
        if (MapLoadingOptions.areNpcsEnabled()) {
            for (OpenMmoMapConfig.NpcConfig npc : config.npcs) {
                map.addEntity(toNpc(npc, map));
            }
        }
        map.setMapCoordinateData(OpenMmoMapDataReader.read(config));
        return map;
    }

    private NpcEntity toNpc(
            OpenMmoMapConfig.NpcConfig npc,
            MapData map
    ) {
        int rawMovement = npc.rawMovementType >= 0
                ? npc.rawMovementType
                : movementType(npc.movementType);
        NpcEntity entity = new NpcEntity(
                0,
                true,
                "npc_" + npc.entityIdx,
                directionCode(npc.facing, 0),
                rawMovement,
                npc.movementRangeX,
                npc.movementRangeY,
                map.getRegionIndexId(),
                map.getMapHeaderIdOrGBAmapGroupId(),
                map.getGbaMapId(),
                map.getRegionIndexId(),
                npc.graphicsId,
                npc.x,
                npc.y,
                npc.elevation
        );
        entity.setMapResourceEntity(true);
        entity.setLoad(false);
        entity.setInteractionScriptName(npc.script);
        entity.setHideFlag(npc.hideFlag);
        entity.setShopId(npc.shopId);
        if (npc.trainerType != 0 && npc.script != null
                && !npc.script.equals("0x0")
                && !npc.script.startsWith("EventScript_")) {
            entity.setIsTrainer(true, 0, 0, false);
        }
        return entity;
    }

    private int movementType(String value) {
        if (value == null || value.isBlank()) {
            return MovementType.NONE.getMovementType();
        }
        String normalized = value.substring(value.lastIndexOf('.') + 1)
                .toUpperCase(Locale.ROOT);
        try {
            return MovementType.valueOf(normalized).getMovementType();
        } catch (IllegalArgumentException ignored) {
            return MovementType.NONE.getMovementType();
        }
    }

    private MapConnectionType connectionType(String value) {
        if (value == null) {
            return MapConnectionType.DOWN;
        }
        return switch (value.substring(value.lastIndexOf('.') + 1).toUpperCase(Locale.ROOT)) {
            case "UP" -> MapConnectionType.UP;
            case "LEFT" -> MapConnectionType.LEFT;
            case "RIGHT" -> MapConnectionType.RIGHT;
            default -> MapConnectionType.DOWN;
        };
    }

    private int directionCode(String value, int defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return switch (value.substring(value.lastIndexOf('.') + 1).toUpperCase(Locale.ROOT)) {
            case "DOWN", "SOUTH" -> 0;
            case "UP", "NORTH" -> 1;
            case "LEFT", "WEST" -> 2;
            case "RIGHT", "EAST" -> 3;
            default -> defaultValue;
        };
    }

    private MapLightingType lighting(String value) {
        return switch (suffix(value)) {
            case "DARK_FLASH_USABLE" -> MapLightingType.DARK_FLASH_USABLE;
            case "DARK_FLASH_UNUSABLE" -> MapLightingType.DARK_FLASH_UNUSABLE;
            default -> MapLightingType.REGULAR;
        };
    }

    private MapWeatherType weather(String value) {
        return switch (suffix(value)) {
            case "IN_HOUSE_WEATHER", "NONE" -> MapWeatherType.IN_HOUSE_WEATHER;
            case "SUNNY_WEATHER_WITH_CLOUDS_IN_WATER", "SUNNY" ->
                    MapWeatherType.SUNNY_WEATHER_WITH_CLOUDS_IN_WATER;
            default -> {
                try {
                    yield MapWeatherType.valueOf(suffix(value));
                } catch (IllegalArgumentException ignored) {
                    yield MapWeatherType.REGULAR_WEATHER;
                }
            }
        };
    }

    private MapZoneType zone(String value) {
        return switch (suffix(value)) {
            case "UNKNOWN_0X00" -> MapZoneType.UNKNOWN_0x00;
            case "CITY" -> MapZoneType.CITY;
            case "ROUTE" -> MapZoneType.ROUTE;
            case "UNDERGROUND" -> MapZoneType.UNDERGROUND;
            case "UNDERWATER" -> MapZoneType.UNDERWATER;
            case "INSIDE", "INDOOR" -> MapZoneType.INSIDE;
            case "SECRET_BASE" -> MapZoneType.SECRET_BASE;
            default -> MapZoneType.VILLAGE;
        };
    }

    private MapEncounterType encounter(String value) {
        try {
            return MapEncounterType.valueOf(suffix(value));
        } catch (IllegalArgumentException exception) {
            return MapEncounterType.RANDOM;
        }
    }

    private boolean isOutdoor(String value) {
        String normalized = suffix(value);
        return normalized.equals("CITY")
                || normalized.equals("VILLAGE")
                || normalized.equals("ROUTE");
    }

    private String suffix(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.substring(value.lastIndexOf('.') + 1).toUpperCase(Locale.ROOT);
    }

    private byte signedByte(int value) {
        return (byte) value;
    }
}
