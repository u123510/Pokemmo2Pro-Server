#!/usr/bin/env python3
"""Generate OpenMMO MapDef-compatible JSON resources from pret GBA decomps."""

from __future__ import annotations

import argparse
import base64
import json
import re
import struct
from pathlib import Path
from typing import Any


BEHAVIOR_NAMES = [
    "NORMAL",
    "TALL_GRASS",
    "LONG_GRASS",
    "JUMP_EAST",
    "JUMP_WEST",
    "JUMP_NORTH",
    "JUMP_SOUTH",
    "DOOR",
    "NON_ANIMATED_DOOR",
    "LADDER",
    "STAIR_WARP_EAST",
    "STAIR_WARP_WEST",
    "NORTH_ARROW_WARP",
    "SOUTH_ARROW_WARP",
    "EAST_ARROW_WARP",
    "WEST_ARROW_WARP",
]

BEHAVIOR_CLASSIFICATION = {
    "MB_TALL_GRASS": "TALL_GRASS",
    "MB_LONG_GRASS": "LONG_GRASS",
    "MB_JUMP_EAST": "JUMP_EAST",
    "MB_JUMP_WEST": "JUMP_WEST",
    "MB_JUMP_NORTH": "JUMP_NORTH",
    "MB_JUMP_SOUTH": "JUMP_SOUTH",
    "MB_ANIMATED_DOOR": "DOOR",
    "MB_WARP_DOOR": "DOOR",
    "MB_NON_ANIMATED_DOOR": "NON_ANIMATED_DOOR",
    "MB_WATER_DOOR": "NON_ANIMATED_DOOR",
    "MB_CAVE_DOOR": "NON_ANIMATED_DOOR",
    "MB_LADDER": "LADDER",
    "MB_UP_ESCALATOR": "LADDER",
    "MB_DOWN_ESCALATOR": "LADDER",
    "MB_REGULAR_WARP": "LADDER",
    "MB_FALL_WARP": "LADDER",
    "MB_DEEP_SOUTH_WARP": "LADDER",
    "MB_UNION_ROOM_WARP": "LADDER",
    "MB_BRIDGE_OVER_OCEAN": "LADDER",
    "MB_LAVARIDGE_GYM_B1F_WARP": "LADDER",
    "MB_LAVARIDGE_GYM_1F_WARP": "LADDER",
    "MB_LAVARIDGE_1F_WARP": "LADDER",
    "MB_AQUA_HIDEOUT_WARP": "LADDER",
    "MB_MT_PYRE_HOLE": "LADDER",
    "MB_MOSSDEEP_GYM_WARP": "LADDER",
    "MB_UP_RIGHT_STAIR_WARP": "STAIR_WARP_EAST",
    "MB_DOWN_RIGHT_STAIR_WARP": "STAIR_WARP_EAST",
    "MB_UP_LEFT_STAIR_WARP": "STAIR_WARP_WEST",
    "MB_DOWN_LEFT_STAIR_WARP": "STAIR_WARP_WEST",
    "MB_NORTH_ARROW_WARP": "NORTH_ARROW_WARP",
    "MB_SOUTH_ARROW_WARP": "SOUTH_ARROW_WARP",
    "MB_WATER_SOUTH_ARROW_WARP": "SOUTH_ARROW_WARP",
    "MB_EAST_ARROW_WARP": "EAST_ARROW_WARP",
    "MB_WEST_ARROW_WARP": "WEST_ARROW_WARP",
}

MAP_TYPE_NAMES = {
    "MAP_TYPE_INDOOR": "INSIDE",
    "MAP_TYPE_TOWN": "VILLAGE",
    "MAP_TYPE_CITY": "CITY",
    "MAP_TYPE_ROUTE": "ROUTE",
    "MAP_TYPE_OCEAN_ROUTE": "ROUTE",
    "MAP_TYPE_UNDERGROUND": "UNDERGROUND",
    "MAP_TYPE_UNDERWATER": "UNDERWATER",
    "MAP_TYPE_SECRET_BASE": "SECRET_BASE",
}

WEATHER_NAMES = {
    "WEATHER_NONE": "IN_HOUSE_WEATHER",
    "WEATHER_SUNNY": "REGULAR_WEATHER",
    "WEATHER_RAIN": "RAINY_WEATHER",
    "WEATHER_SNOW": "THREE_SNOW_FLAKES",
    "WEATHER_FOG_HORIZONTAL": "STEADY_MIST",
    "WEATHER_FOG_DIAGONAL": "STEADY_MIST",
    "WEATHER_SHADE": "CLOUDY",
    "WEATHER_UNDERWATER_BUBBLES": "UNDERWATER_MIST",
    "WEATHER_VOLCANIC_ASH": "DENSE_BRIGHT_MIST",
}


def load_json(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def parse_int(value: str) -> int:
    return int(value, 0)


def parse_defines(path: Path, prefix: str) -> dict[str, int]:
    result: dict[str, int] = {}
    if not path.is_file():
        return result
    pattern = re.compile(rf"^\s*#define\s+({re.escape(prefix)}\w+)\s+(\(?0x[0-9A-Fa-f]+\)?|\(?\d+\)?)")
    for line in path.read_text(encoding="utf-8", errors="replace").splitlines():
        match = pattern.match(line)
        if match:
            result[match.group(1)] = parse_int(match.group(2).strip("()"))
    return result


def parse_enum_defines(path: Path, prefix: str) -> dict[str, int]:
    result = parse_defines(path, prefix)
    if not path.is_file():
        return result
    next_value = 0
    in_enum = False
    entry = re.compile(rf"^\s*({re.escape(prefix)}\w+)\s*(?:=\s*(0x[0-9A-Fa-f]+|\d+))?")
    for raw in path.read_text(encoding="utf-8", errors="replace").splitlines():
        line = raw.strip()
        if line.startswith("enum"):
            in_enum = True
            continue
        if not in_enum:
            continue
        if line.startswith("}"):
            in_enum = False
            continue
        match = entry.match(line)
        if not match:
            continue
        value = parse_int(match.group(2)) if match.group(2) else next_value
        result.setdefault(match.group(1), value)
        next_value = value + 1
    return result


def parse_tileset_attributes(root: Path) -> tuple[dict[str, Path], dict[str, str], int, int]:
    metatiles_header = root / "src/data/tilesets/metatiles.h"
    headers = root / "src/data/tilesets/headers.h"
    attr_paths: dict[str, Path] = {}
    attr_symbols: dict[str, str] = {}
    width = 4
    if metatiles_header.is_file():
        pattern = re.compile(
            r"(gMetatileAttributes_\w+)\[\]\s*=\s*INCBIN_U(16|32)\(\"([^\"]+)\"\)"
        )
        for match in pattern.finditer(metatiles_header.read_text(encoding="utf-8")):
            attr_symbols[match.group(1)] = match.group(3)
            width = 4 if match.group(2) == "32" else 2
            attr_paths[match.group(1)] = root / match.group(3)
    tileset_attrs: dict[str, str] = {}
    current: str | None = None
    if headers.is_file():
        tileset_re = re.compile(r"const struct Tileset\s+(gTileset_\w+)")
        attr_re = re.compile(r"\.metatileAttributes\s*=\s*(gMetatileAttributes_\w+)")
        for line in headers.read_text(encoding="utf-8").splitlines():
            tileset_match = tileset_re.search(line)
            if tileset_match:
                current = tileset_match.group(1)
            attr_match = attr_re.search(line)
            if current and attr_match:
                tileset_attrs[current] = attr_match.group(1)
    primary_count = 512
    fieldmap = root / "include/fieldmap.h"
    if fieldmap.is_file():
        match = re.search(
            r"#define\s+NUM_METATILES_IN_PRIMARY\s+(\d+)",
            fieldmap.read_text(encoding="utf-8", errors="replace"),
        )
        if match:
            primary_count = int(match.group(1))
    return attr_paths, tileset_attrs, primary_count, width


def parse_tileset_palette_ids(root: Path, region_id: int) -> dict[str, int]:
    path = root / "src/data/tilesets/headers.h"
    result: dict[str, int] = {}
    if not path.is_file():
        return result
    index = 100 if region_id == 1 else 0
    pattern = re.compile(r"^\s*const struct Tileset\s+(gTileset_\w+)\s*=")
    for line in path.read_text(encoding="utf-8", errors="replace").splitlines():
        match = pattern.match(line)
        if match:
            result[match.group(1)] = index
            index += 1
    return result


def build_behavior_tables(root: Path) -> tuple[dict[str, int], dict[str, int]]:
    behavior_file = root / "include/constants/metatile_behaviors.h"
    ids = parse_enum_defines(behavior_file, "MB_")
    by_name: dict[str, int] = {}
    for name, raw_id in ids.items():
        category = BEHAVIOR_CLASSIFICATION.get(name)
        if category:
            by_name[category] = BEHAVIOR_NAMES.index(category)
    return ids, by_name


def read_attribute_table(path: Path, width: int) -> list[int]:
    if not path.is_file():
        return []
    raw = path.read_bytes()
    return [
        int.from_bytes(raw[index:index + width], "little")
        for index in range(0, len(raw), width)
        if len(raw[index:index + width]) == width
    ]


def behavior_data(
    root: Path,
    layout: dict[str, Any],
    attr_paths: dict[str, Path],
    tileset_attrs: dict[str, str],
    primary_count: int,
    attr_width: int,
    behavior_ids: dict[str, int],
    behavior_ordinals: dict[str, int],
) -> str:
    block_path = root / layout["blockdata_filepath"]
    block_bytes = block_path.read_bytes()
    primary_name = layout.get("primary_tileset", "")
    secondary_name = layout.get("secondary_tileset", "")
    primary_attr = read_attribute_table(
        attr_paths.get(tileset_attrs.get(primary_name, ""), Path()), attr_width
    )
    secondary_attr = read_attribute_table(
        attr_paths.get(tileset_attrs.get(secondary_name, ""), Path()), attr_width
    )
    mask = 0x1FF if attr_width == 4 else 0xFF
    output = bytearray()
    for index in range(0, len(block_bytes), 2):
        block = int.from_bytes(block_bytes[index:index + 2], "little")
        metatile_id = block & 0x3FF
        attr = (
            primary_attr[metatile_id]
            if metatile_id < primary_count and metatile_id < len(primary_attr)
            else secondary_attr[metatile_id - primary_count]
            if metatile_id - primary_count < len(secondary_attr)
            else 0
        )
        raw_behavior = attr & mask
        ordinal = 0
        for name, raw_id in behavior_ids.items():
            if raw_id == raw_behavior and name in BEHAVIOR_CLASSIFICATION:
                ordinal = behavior_ordinals[BEHAVIOR_CLASSIFICATION[name]]
                break
        output.append(ordinal)
    return base64.b64encode(output).decode("ascii")


def parse_gfx_ids(root: Path) -> dict[str, int]:
    return parse_defines(root / "include/constants/event_objects.h", "OBJ_EVENT_GFX_")


def facing_for_movement(movement: str) -> str:
    upper = movement.upper()
    if "UP" in upper and "DOWN" not in upper:
        return "UP"
    if "LEFT" in upper and "RIGHT" not in upper:
        return "LEFT"
    if "RIGHT" in upper and "LEFT" not in upper:
        return "RIGHT"
    return "DOWN"


def parse_script_events(root: Path, map_name: str) -> tuple[str, list[dict[str, Any]]]:
    path = root / "data/maps" / map_name / "scripts.inc"
    if not path.is_file():
        return "", []
    text = path.read_text(encoding="utf-8", errors="replace")
    transition = re.search(
        r"map_script\s+MAP_SCRIPT_ON_TRANSITION\s*,\s*(\w+)", text
    )
    frame_table = re.search(
        r"map_script\s+MAP_SCRIPT_ON_FRAME_TABLE\s*,\s*(\w+)", text
    )
    if not frame_table:
        return transition.group(1) if transition else "", []
    table = re.search(
        rf"(?m)^{frame_table.group(1)}:+[^\n]*\n(.*?)(?=\n\s*\.2byte)",
        text,
        re.DOTALL,
    )
    if not table:
        return transition.group(1) if transition else "", []
    frames = []
    for match in re.finditer(
        r"map_script_2\s+(\w+)\s*,\s*(\d+)\s*,\s*(\w+)",
        table.group(1),
    ):
        frames.append({
            "varKey": match.group(1),
            "value": int(match.group(2)),
            "script": match.group(3),
        })
    return transition.group(1) if transition else "", frames


def parse_coordinate_events(source: dict[str, Any]) -> list[dict[str, Any]]:
    events = []
    for event in source.get("coord_events") or []:
        if event.get("type") != "trigger":
            continue
        value = str(event.get("var_value", ""))
        if not re.fullmatch(r"-?\d+", value):
            continue
        events.append({
            "x": int(event.get("x", 0)),
            "y": int(event.get("y", 0)),
            "elevation": int(event.get("elevation", 0)),
            "varKey": event.get("var", ""),
            "value": int(value),
            "script": event.get("script", "0x0"),
        })
    return events


def parse_background_events(source: dict[str, Any]) -> list[dict[str, Any]]:
    events = []
    for event in source.get("bg_events") or []:
        events.append({
            "x": int(event.get("x", 0)),
            "y": int(event.get("y", 0)),
            "elevation": int(event.get("elevation", 0)),
            "facingDir": str(event.get("player_facing_dir", "BG_EVENT_PLAYER_FACING_ANY"))
                .removeprefix("BG_EVENT_PLAYER_FACING_"),
            "script": event.get("script", "0x0"),
        })
    return events


def normalize_map_name(value: str) -> str:
    return re.sub(r"[^a-z0-9]", "", value.lower())


def snake_name(value: str) -> str:
    value = re.sub(r"([a-z])([A-Z])", r"\1_\2", value)
    return re.sub(r"[^A-Za-z0-9]+", "_", value).strip("_").lower()


def read_regions(root: Path, region: str) -> tuple[dict[str, Any], dict[str, Any], dict[str, int], dict[str, int]]:
    groups = load_json(root / "data/maps/map_groups.json")
    layouts = load_json(root / "data/layouts/layouts.json")
    songs = parse_defines(root / "include/constants/songs.h", "MUS_")
    sections = load_json(root / "src/data/region_map/region_map_sections.json")
    mapsecs = {
        item["id"]: index
        for index, item in enumerate(sections.get("map_sections", []))
    }
    return groups, layouts, songs, mapsecs


def map_index(groups: dict[str, Any], map_name: str) -> tuple[int, int]:
    for group_index, group_name in enumerate(groups["group_order"]):
        maps = groups.get(group_name, [])
        for map_id, candidate in enumerate(maps):
            if candidate == map_name:
                return group_index, map_id
    raise ValueError(f"找不到地图组位置: {map_name}")


def make_map(
    root: Path,
    region_id: int,
    bank_offset: int,
    map_name: str,
    groups: dict[str, Any],
    layouts: dict[str, Any],
    songs: dict[str, int],
    mapsecs: dict[str, int],
    gfx_ids: dict[str, int],
    attr_paths: dict[str, Path],
    tileset_attrs: dict[str, str],
    primary_count: int,
    attr_width: int,
    behavior_ids: dict[str, int],
    behavior_ordinals: dict[str, int],
    all_addresses: dict[str, tuple[int, int, str]],
    palette_ids: dict[str, int],
    encounter_fields: list[dict[str, Any]],
    encounter_entry: dict[str, Any] | None,
) -> dict[str, Any]:
    source = load_json(root / "data/maps" / map_name / "map.json")
    layout = next(item for item in layouts["layouts"] if item.get("id") == source["layout"])
    group_id, map_id = map_index(groups, map_name)
    width = int(layout.get("width", 20))
    height = int(layout.get("height", 15))
    border_width = int(layout.get("border_width", 2))
    border_height = int(layout.get("border_height", 2))
    block_path = layout.get("blockdata_filepath")
    border_path = layout.get("border_filepath")
    block = (root / block_path).read_bytes() if block_path else b""
    border = (root / border_path).read_bytes() if border_path else b""
    border_blocks = [
        int.from_bytes(border[index:index + 2], "little")
        for index in range(0, len(border), 2)
    ]
    border_tiles = [
        {"material": raw & 0x3FF, "collision": (raw >> 10) & 0x3F}
        for raw in border_blocks[: max(1, border_width * border_height)]
    ]
    if len(border_tiles) < border_width * border_height:
        border_tiles.extend(
            [{"material": 8, "collision": 0}]
            * (border_width * border_height - len(border_tiles))
        )
    connections = []
    for connection in source.get("connections") or []:
        target = normalize_map_name(connection["map"].removeprefix("MAP_"))
        if target not in all_addresses:
            continue
        target_group, target_id, _ = all_addresses[target]
        direction = connection["direction"].upper()
        connections.append({
            "direction": direction,
            "unknown": int(connection.get("offset", 0)),
            "targetBank": target_group + bank_offset,
            "targetMap": target_id,
        })
    warps = []
    for warp in source.get("warp_events") or []:
        target_name = normalize_map_name(warp.get("dest_map", "").removeprefix("MAP_"))
        if target_name not in all_addresses:
            continue
        target_group, target_id, target_source_name = all_addresses[target_name]
        target_json = load_json(root / "data/maps" / target_source_name / "map.json")
        target_index = int(warp.get("dest_warp_id", 0))
        target_warps = target_json.get("warp_events") or []
        target = target_warps[target_index] if 0 <= target_index < len(target_warps) else warp
        warps.append({
            "x": int(warp["x"]),
            "y": int(warp["y"]),
            "elevation": max(0, int(warp.get("elevation", 0)) - 1),
            "targetRegionId": region_id,
            "targetBankId": target_group + bank_offset,
            "targetMapId": target_id,
            "targetX": int(target.get("x", warp["x"])),
            "targetY": int(target.get("y", warp["y"])),
            "targetElevation": max(0, int(target.get("elevation", 0)) - 1),
        })
    npcs = []
    for entity_idx, npc in enumerate(source.get("object_events") or []):
        graphics = npc.get("graphics_id", "OBJ_EVENT_GFX_NONE")
        movement = npc.get("movement_type", "MOVEMENT_TYPE_NONE")
        flag = npc.get("flag", "0")
        npcs.append({
            "entityIdx": entity_idx,
            "graphicsId": gfx_ids.get(graphics, 0),
            "x": int(npc.get("x", 0)),
            "y": int(npc.get("y", 0)),
            "elevation": max(0, int(npc.get("elevation", 0)) - 1),
            "movementType": movement.removeprefix("MOVEMENT_TYPE_"),
            "movementRangeX": int(npc.get("movement_range_x", 0)),
            "movementRangeY": int(npc.get("movement_range_y", 0)),
            "trainerType": 0 if npc.get("trainer_type") == "TRAINER_TYPE_NONE" else 1,
            "facing": facing_for_movement(movement),
            "script": npc.get("script", "0x0"),
            "hideFlag": "" if flag in ("0", "", None) else f"{'kanto' if region_id == 0 else 'hoenn'}/{flag}",
            "rawMovementType": parse_defines(
                root / "include/constants/event_object_movement.h",
                "MOVEMENT_TYPE_",
            ).get(movement, -1),
        })
    map_type = MAP_TYPE_NAMES.get(source.get("map_type"), "INSIDE")
    weather = WEATHER_NAMES.get(source.get("weather"), "REGULAR_WEATHER")
    transition_script, frame_scripts = parse_script_events(root, map_name)
    return {
        "regionId": region_id,
        "bankId": group_id + bank_offset,
        "mapId": map_id,
        "width": width,
        "height": height,
        "paletteIdx1": palette_ids.get(layout.get("primary_tileset"), 0),
        "paletteIdx2": palette_ids.get(layout.get("secondary_tileset"), 0),
        "borderWidth": border_width,
        "borderHeight": border_height,
        "unknownShort": songs.get(source.get("music"), 0),
        "unknownByte": mapsecs.get(source.get("region_map_section"), 0),
        "borderTiles": border_tiles,
        "lighting": "REGULAR",
        "weather": weather,
        "mapType": map_type,
        "encounterType": "RANDOM",
        "encounterFields": encounter_fields,
        "wildEncounters": [encounter_entry] if encounter_entry else [],
        "onTransitionScript": transition_script,
        "onFrameScripts": frame_scripts,
        "coordScripts": parse_coordinate_events(source),
        "bgEvents": parse_background_events(source),
        "connections": connections,
        "warps": warps,
        "npcs": npcs,
        "blockData": base64.b64encode(block).decode("ascii"),
        "behaviorData": behavior_data(
            root, layout, attr_paths, tileset_attrs, primary_count, attr_width,
            behavior_ids, behavior_ordinals,
        ),
    }


def generate_region(source_root: Path, output_root: Path, region: str, region_id: int, bank_offset: int) -> int:
    groups, layouts, songs, mapsecs = read_regions(source_root, region)
    encounter_fields: list[dict[str, Any]] = []
    encounter_by_map: dict[str, dict[str, Any]] = {}
    encounter_source = source_root / "src/data/wild_encounters.json"
    if encounter_source.is_file():
        encounter_document = load_json(encounter_source)
        for group in encounter_document.get("wild_encounter_groups", []):
            if group.get("for_maps"):
                encounter_fields = group.get("fields") or []
                for entry in group.get("encounters") or []:
                    label = str(entry.get("base_label", ""))
                    if (region_id == 0 and label.endswith("_FireRed")) or (
                            region_id == 1 and label and not label.endswith("_FireRed")
                    ):
                        encounter_by_map[normalize_map_name(
                            str(entry.get("map", "")).removeprefix("MAP_")
                        )] = entry
                break
    gfx_ids = parse_gfx_ids(source_root)
    attr_paths, tileset_attrs, primary_count, attr_width = parse_tileset_attributes(source_root)
    palette_ids = parse_tileset_palette_ids(source_root, region_id)
    behavior_ids, behavior_ordinals = build_behavior_tables(source_root)
    all_addresses: dict[str, tuple[int, int, str]] = {}
    for group_name in groups["group_order"]:
        for map_id, map_name in enumerate(groups.get(group_name, [])):
            all_addresses[normalize_map_name(map_name)] = (
                groups["group_order"].index(group_name),
                map_id,
                map_name,
            )
    names = [name for group in groups["group_order"] for name in groups.get(group, [])]
    count = 0
    for map_name in names:
        if not (source_root / "data/maps" / map_name / "map.json").is_file():
            continue
        source = load_json(source_root / "data/maps" / map_name / "map.json")
        layout = next(
            (item for item in layouts["layouts"] if item.get("id") == source.get("layout")),
            None,
        )
        block_path = layout.get("blockdata_filepath") if layout else None
        if not block_path or not (source_root / block_path).is_file():
            continue
        value = make_map(
            source_root, region_id, bank_offset, map_name, groups, layouts, songs, mapsecs,
            gfx_ids, attr_paths, tileset_attrs, primary_count, attr_width,
            behavior_ids, behavior_ordinals, all_addresses, palette_ids,
            encounter_fields, encounter_by_map.get(normalize_map_name(map_name)),
        )
        target = output_root / snake_name(map_name)
        target.mkdir(parents=True, exist_ok=True)
        (target / f"{map_name}.json").write_text(
            json.dumps(value, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
        )
        count += 1
    return count


def main() -> int:
    parser = argparse.ArgumentParser(description="按 openmmo 地图逻辑生成 Kanto/Hoenn JSON 地图资源")
    parser.add_argument("--firered-root", type=Path, required=True, help="pret/pokefirered 源码根目录")
    parser.add_argument("--emerald-root", type=Path, required=True, help="pret/pokeemerald 源码根目录")
    parser.add_argument("--output-root", type=Path, required=True, help="输出地图根目录")
    parser.add_argument("--clean", action="store_true", help="生成前清理输出目录中的 Kanto/Hoenn 地图")
    args = parser.parse_args()
    if args.clean:
        for name in ("kanto", "hoenn"):
            target = args.output_root / name
            if target.exists():
                for child in target.iterdir():
                    if child.is_dir():
                        import shutil
                        shutil.rmtree(child)
                    else:
                        child.unlink()
    kanto = generate_region(args.firered_root, args.output_root / "kanto", "kanto", 0, 0)
    hoenn = generate_region(args.emerald_root, args.output_root / "hoenn", "hoenn", 1, 50)
    print(f"kanto={kanto} hoenn={hoenn}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
