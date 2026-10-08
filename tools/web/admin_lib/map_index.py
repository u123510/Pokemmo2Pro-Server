"""resource/map 只读索引：地图、NPC、碰撞格，供商店绑定与 NPC 生成校验使用。

原生地图文件绝不修改。regionId 映射与 RegionType 一致：
kanto=0、hoenn=1、sinnoh=3。
"""

from __future__ import annotations

import base64
import json
import threading
from pathlib import Path

from .paths import MAP_DIR

REGION_NAMES = {0: "kanto", 1: "hoenn", 3: "sinnoh"}
NAME_TO_REGION = {name: rid for rid, name in REGION_NAMES.items()}

_LOCK = threading.Lock()
_CACHE: dict[str, dict] = {}
_SCANNED = False


def _scan() -> dict[str, dict]:
    global _SCANNED
    with _LOCK:
        if _SCANNED:
            return _CACHE
        _CACHE.clear()
        if MAP_DIR.is_dir():
            for region_dir in sorted(MAP_DIR.iterdir()):
                if not region_dir.is_dir():
                    continue
                region_name = region_dir.name
                for map_dir in sorted(region_dir.iterdir()):
                    if not map_dir.is_dir():
                        continue
                    for file in sorted(map_dir.glob("*.json")):
                        try:
                            data = json.loads(file.read_text(encoding="utf-8"))
                        except (OSError, ValueError):
                            continue
                        info = {
                            "name": file.stem,
                            "region": region_name,
                            "regionId": NAME_TO_REGION.get(region_name),
                            "dir": region_name + "/" + map_dir.name,
                            "width": int(data.get("width", 0)),
                            "height": int(data.get("height", 0)),
                            "npcs": [
                                {
                                    "entityIdx": int(npc.get("entityIdx", -1)),
                                    "graphicsId": npc.get("graphicsId"),
                                    "x": npc.get("x"),
                                    "y": npc.get("y"),
                                    "elevation": npc.get("elevation"),
                                    "facing": npc.get("facing"),
                                    "movementType": npc.get("movementType"),
                                    "script": npc.get("script", ""),
                                    "shopId": npc.get("shopId", ""),
                                }
                                for npc in data.get("npcs", [])
                            ],
                            "warps": [
                                {"x": w["x"], "y": w["y"], "targetBankId": w.get("targetBankId"),
                                 "targetMapId": w.get("targetMapId")}
                                for w in data.get("warps", [])
                            ],
                            "counters": [{"x": c["x"], "y": c["y"]} for c in data.get("interactionCounters", [])],
                            "file": str(file),
                            "mtime": file.stat().st_mtime,
                            "_blockData": data.get("blockData", ""),
                        }
                        _CACHE[region_name + "/" + info["name"]] = info
        _SCANNED = True
        return _CACHE


def all_maps() -> list[dict]:
    maps = list(_scan().values())
    maps.sort(key=lambda m: (m["regionId"] if m["regionId"] is not None else 99, m["name"]))
    return [{k: v for k, v in m.items() if not k.startswith("_")} for m in maps]


def find_by_name(name: str) -> list[dict]:
    """按地图名查找（商店绑定要求名称唯一，重复时全部返回供上层报错）。"""
    return [m for m in _scan().values() if m["name"] == name]


def get(region: str, name: str) -> dict | None:
    return _scan().get(region + "/" + name)


def block_grid(info: dict) -> list[list[dict]]:
    """解码 blockData 为 [y][x] 的 {tileId, collision, elevation} 网格。"""
    raw = base64.b64decode(info["_blockData"]) if info["_blockData"] else b""
    width, height = info["width"], info["height"]
    grid: list[list[dict]] = []
    for y in range(height):
        row = []
        for x in range(width):
            index = (y * width + x) * 2
            if index + 1 < len(raw):
                value = raw[index] | raw[index + 1] << 8
                row.append({
                    "tileId": value & 0x3FF,
                    "collision": (value >> 10) & 0x3,
                    "elevation": (value >> 12) & 0xF,
                })
            else:
                row.append({"tileId": 0, "collision": 3, "elevation": 0})
        grid.append(row)
    return grid


def native_entity_idxs(info: dict) -> set[int]:
    return {npc["entityIdx"] for npc in info["npcs"]}


def occupied_cells(info: dict) -> set[tuple[int, int]]:
    """原生 NPC 占用格（z 未参与，重叠提示用）。"""
    return {(npc["x"], npc["y"]) for npc in info["npcs"] if npc["x"] is not None and npc["y"] is not None}


def map_file(region: str, name: str) -> Path | None:
    info = get(region, name)
    return Path(info["file"]) if info else None
