#!/usr/bin/env python3
"""Convert OpenMMO-DS generated Sinnoh maps into Java server JSON resources."""

from __future__ import annotations

import argparse
import base64
import json
import re
from pathlib import Path
from typing import Any


def balanced(text: str, start: int) -> str:
    depth = 0
    quote = False
    escaped = False
    for index in range(start, len(text)):
        char = text[index]
        if quote:
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == '"':
                quote = False
            continue
        if char == '"':
            quote = True
        elif char == "(":
            depth += 1
        elif char == ")":
            depth -= 1
            if depth == 0:
                return text[start + 1:index]
    raise ValueError("未闭合的 Kotlin 表达式")


def field(text: str, name: str, default: str = "") -> str:
    match = re.search(rf"\b{name}\s*=\s*([^,\n]+)", text)
    return match.group(1).strip() if match else default


def integer(value: str, default: int = 0) -> int:
    match = re.search(r"-?\d+", value or "")
    return int(match.group(0)) if match else default


def quoted(value: str) -> str:
    match = re.search(r'"([^"]*)"', value or "")
    return match.group(1) if match else ""


def list_body(text: str, name: str) -> str:
    match = re.search(rf"\b{name}\s*=\s*listOf\(", text)
    if not match:
        return ""
    return balanced(text, match.end() - 1)


def split_expression_items(body: str) -> list[str]:
    items = []
    current = []
    quote = False
    escaped = False
    depth = 0
    for char in body:
        if quote:
            current.append(char)
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == '"':
                quote = False
            continue
        if char == '"':
            quote = True
            current.append(char)
        elif char in "([{":
            depth += 1
            current.append(char)
        elif char in ")]}":
            depth -= 1
            current.append(char)
        elif char == "," and depth == 0:
            item = "".join(current).strip()
            if item:
                items.append(item)
            current = []
        else:
            current.append(char)
    item = "".join(current).strip()
    if item:
        items.append(item)
    return items


def expression_items(body: str) -> list[str]:
    return [item for item in split_expression_items(body) if item != "null"]


def expression_items_with_null(body: str) -> list[str | None]:
    return [
        None if item == "null" else item
        for item in split_expression_items(body)
    ]


def parse_terrain(generated_root: Path) -> dict[str, dict[str, Any]]:
    chunks: dict[str, str] = {}
    matrices: dict[str, dict[str, Any]] = {}
    terrain_files = generated_root.rglob("sinnoh/terrain/*.kt")
    for path in terrain_files:
        text = path.read_text(encoding="utf-8")
        for name, value in re.findall(r'internal val (MAP_\w+) = "([^"]+)"', text):
            chunks[name] = value
        for match in re.finditer(
                r"internal val (MAP_MATRIX_\d+):\s*TerrainPlane\s*=",
                text):
            name = match.group(1)
            start = text.find("TerrainPlane(", match.end())
            body = balanced(text, start + len("TerrainPlane") - 1)
            chunk_names = expression_items_with_null(list_body(body, "chunks"))
            matrices[name] = {
                "cols": integer(field(body, "cols"), 1),
                "rows": integer(field(body, "rows"), 1),
                "chunkSide": integer(field(body, "chunkSide"), 32),
                "chunks": [chunks.get(item) if item is not None else None for item in chunk_names],
                "altitudes": [
                    integer(item)
                    for item in expression_items(list_body(body, "altitudes"))
                ],
                "headers": [
                    integer(item)
                    for item in expression_items(list_body(body, "headers"))
                ],
            }
    return matrices


def parse_warps(body: str) -> list[dict[str, Any]]:
    result = []
    for match in re.finditer(r"WarpTile\(", body):
        item = balanced(body, match.start() + len("WarpTile") - 1)
        result.append({
            "x": integer(field(item, "x")),
            "y": integer(field(item, "y")),
            "elevation": integer(field(item, "elevation")),
            "targetRegionId": integer(field(item, "targetRegionId")),
            "targetBankId": integer(field(item, "targetBankId")),
            "targetMapId": integer(field(item, "targetMapId")),
            "targetX": integer(field(item, "targetX")),
            "targetY": integer(field(item, "targetY")),
            "targetElevation": integer(field(item, "targetElevation")),
            "dynamic": "true" in field(item, "dynamic", "false").lower(),
        })
    return result


def parse_npcs(body: str) -> list[dict[str, Any]]:
    result = []
    for match in re.finditer(r"NpcDef\(", body):
        item = balanced(body, match.start() + len("NpcDef") - 1)
        result.append({
            "entityIdx": integer(field(item, "entityIdx")),
            "graphicsId": integer(field(item, "graphicsId")),
            "x": integer(field(item, "x")),
            "y": integer(field(item, "y")),
            "elevation": integer(field(item, "elevation")),
            "movementType": field(item, "movementType").split(".")[-1],
            "movementRangeX": integer(field(item, "movementRangeX")),
            "movementRangeY": integer(field(item, "movementRangeY")),
            "trainerType": integer(field(item, "trainerType")),
            "facing": field(item, "facing").split(".")[-1],
            "script": quoted(field(item, "script")),
            "hideFlag": quoted(field(item, "hideFlag")),
            "rawMovementType": integer(field(item, "rawMovementId"), -1),
        })
    return result


def parse_coord_scripts(body: str) -> list[dict[str, Any]]:
    result = []
    for match in re.finditer(r"MapCoordScript\(", body):
        item = balanced(body, match.start() + len("MapCoordScript") - 1)
        result.append({
            "x": integer(field(item, "x")),
            "y": integer(field(item, "y")),
            "elevation": integer(field(item, "elevation")),
            "varKey": quoted(field(item, "varKey")),
            "value": integer(field(item, "value")),
            "script": quoted(field(item, "script")),
        })
    return result


def parse_bg_events(body: str) -> list[dict[str, Any]]:
    result = []
    for match in re.finditer(r"BgEventDef\(", body):
        item = balanced(body, match.start() + len("BgEventDef") - 1)
        result.append({
            "x": integer(field(item, "x")),
            "y": integer(field(item, "y")),
            "elevation": integer(field(item, "elevation")),
            "facingDir": quoted(field(item, "facingDir")),
            "script": quoted(field(item, "script")),
        })
    return result


def parse_frame_scripts(body: str) -> list[dict[str, Any]]:
    result = []
    for match in re.finditer(r"MapFrameScript\(", body):
        item = balanced(body, match.start() + len("MapFrameScript") - 1)
        result.append({
            "varKey": quoted(field(item, "varKey")),
            "value": integer(field(item, "value")),
            "script": quoted(field(item, "script")),
        })
    return result


def parse_tile_behaviors(generated_root: Path) -> list[str]:
    path = generated_root / "sinnoh" / "terrain" / "TileBehaviors.kt"
    if not path.is_file():
        return []
    return re.findall(r"TileBehavior\.([A-Z_]+)", path.read_text(encoding="utf-8"))


def parse_encounter_tables(body: str) -> tuple[list[dict[str, Any]], list[dict[str, Any]]]:
    fields = []
    encounter = {
        "map": "",
        "base_label": "sinnoh",
    }
    for match in re.finditer(r"WildEncounterTable\(", body):
        item = balanced(body, match.start() + len("WildEncounterTable") - 1)
        method = field(item, "method").split(".")[-1]
        table = {
            "encounter_rate": integer(field(item, "encounterRate")),
            "mons": [],
        }
        for slot_match in re.finditer(r"WildEncounterSlot\(", list_body(item, "slots")):
            slot = balanced(
                list_body(item, "slots"),
                slot_match.start() + len("WildEncounterSlot") - 1,
            )
            table["mons"].append({
                "min_level": integer(field(slot, "minLevel")),
                "max_level": integer(field(slot, "maxLevel")),
                "species": str(integer(field(slot, "speciesId"))),
            })
        if not table["mons"]:
            continue
        json_name = {
            "LAND": "land_mons",
            "WATER": "water_mons",
        }.get(method)
        field_name = {
            "LAND": "land_mons",
            "WATER": "water_mons",
        }.get(method)
        if json_name is None:
            continue
        encounter[json_name] = table
        fields.append({
            "type": json_name,
            "encounter_rates": [
                integer(field(slot, "weight"))
                for slot_match in re.finditer(
                    r"WildEncounterSlot\(",
                    list_body(item, "slots"),
                )
                for slot in [
                    balanced(
                        list_body(item, "slots"),
                        slot_match.start() + len("WildEncounterSlot") - 1,
                    )
                ]
                if integer(field(slot, "weight")) > 0
            ],
        })
    return fields, [encounter] if len(encounter) > 2 else []


def parse_map(
        path: Path,
        matrices: dict[str, dict[str, Any]],
        behaviors: list[str],
) -> dict[str, Any]:
    text = path.read_text(encoding="utf-8")
    start = text.find("MapDef(")
    body = balanced(text, start + len("MapDef") - 1)
    terrain_ref = re.search(r"terrain\s*=\s*[\w.]+(MAP_MATRIX_\d+)", body)
    matrix = matrices.get(terrain_ref.group(1)) if terrain_ref else None
    map_type = field(body, "mapType", "MapType.INSIDE").split(".")[-1]
    encounter_fields, wild_encounters = parse_encounter_tables(body)
    for entry in wild_encounters:
        entry["map"] = quoted(field(body, "name"))
    weather = field(body, "weather", "").strip()
    weather_value = "REGULAR_WEATHER"
    weather_match = re.search(r"Weather\.entries\[\s*18\s*\+\s*(\d+)\s*]", weather)
    if weather_match:
        weather_value = {
            0: "GEN4_NONE",
            1: "GEN4_UNK0",
            2: "GEN4_RAIN",
            3: "GEN4_HEAVY_RAIN",
            4: "GEN4_HEAVY_RAIN_WITH_THUNDER",
            5: "GEN4_SNOW",
            6: "GEN4_HEAVY_SNOW",
            7: "GEN4_HAIL",
            8: "GEN4_CLEAR",
            9: "GEN4_ASHDUST",
            10: "GEN4_SANDSTORM",
            11: "GEN4_SPECIAL_ICY",
            12: "GEN4_SPECIAL_ROCKS",
            13: "GEN4_UNK",
            14: "GEN4_HEAVY_FOG",
            15: "GEN4_HEAVY_FOG_WITH_DARKNESS",
            16: "GEN4_CAVE_FLASH",
            23: "GEN4_FOREST_TREE_SHADOWS",
            26: "GEN4_DARKNESS",
            27: "GEN4_GREEN_HAZE",
            28: "GEN4_RED_HAZE",
            29: "GEN4_BLUE_HAZE",
            30: "GEN4_BLACK_HAZE",
            32: "GEN4_RAIN2",
            33: "GEN4_UNK2",
            34: "GEN4_HEAVY_SNOW2",
            35: "GEN4_HEAVY_SNOW3",
            36: "GEN4_SNOW2",
        }.get(integer(weather_match.group(1)), "GEN4_NONE")
    elif "." in weather:
        weather_value = weather.split(".")[-1]
    return {
        "regionId": 3,
        "bankId": integer(field(body, "bankId")),
        "mapId": integer(field(body, "mapId")),
        "name": quoted(field(body, "name")),
        "width": integer(field(body, "width"), 32),
        "height": integer(field(body, "height"), 32),
        "lighting": "REGULAR",
        "weather": weather_value,
        "mapType": map_type,
        "encounterType": "RANDOM",
        "terrainBehaviors": behaviors,
        "encounterFields": encounter_fields,
        "wildEncounters": wild_encounters,
        "unknownShort": integer(field(body, "unknownShort")),
        "unknownByte": integer(field(body, "unknownByte")),
        "terrain": matrix,
        "warps": parse_warps(list_body(body, "warps")),
        "npcs": parse_npcs(list_body(body, "npcs")),
        "onTransitionScript": quoted(field(body, "onTransitionScript")),
        "onFrameScripts": parse_frame_scripts(list_body(body, "onFrameScripts")),
        "coordScripts": parse_coord_scripts(list_body(body, "coordScripts")),
        "bgEvents": parse_bg_events(list_body(body, "bgEvents")),
    }


def main() -> int:
    parser = argparse.ArgumentParser(description="生成 OpenMMO-DS 风格的 Sinnoh NDS 地图 JSON")
    parser.add_argument("--generated-root", type=Path, required=True)
    parser.add_argument("--output-root", type=Path, required=True)
    parser.add_argument("--clean", action="store_true")
    args = parser.parse_args()
    sinnoh_root = args.output_root / "sinnoh"
    if args.clean and sinnoh_root.exists():
        import shutil
        shutil.rmtree(sinnoh_root)
    terrain = parse_terrain(args.generated_root)
    behaviors = parse_tile_behaviors(args.generated_root)
    count = 0
    for path in args.generated_root.rglob("sinnoh/bank*/*.kt"):
        config = parse_map(path, terrain, behaviors)
        target = sinnoh_root / f"bank{config['bankId']}"
        target.mkdir(parents=True, exist_ok=True)
        (target / f"{config['name']}.json").write_text(
            json.dumps(config, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
        )
        count += 1
    print(f"sinnoh={count} terrainMatrices={len(terrain)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
