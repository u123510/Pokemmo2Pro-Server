"""招式编辑器：resource/move/gen{3,6,7,8,9}/技能<编号>.json。"""

from __future__ import annotations

import json
import re

from .backups import snapshot
from .paths import RESOURCE_ROOT

MOVE_ROOT = RESOURCE_ROOT / "move"
GENS = ("gen3", "gen6", "gen7", "gen8", "gen9")
FILE_PATTERN = re.compile(r"^技能(\d+)\.json$")
# 允许编辑的标量字段（数组/复杂效果字段走原始 JSON 编辑）
SCALAR_FIELDS = {
    "moveBasePower": (0, 500), "moveBasePp": (0, 99), "moveBaseAccuracy": (0, 100),
    "movePriority": (-7, 7),
}


def gens() -> list[dict]:
    out = []
    for gen in GENS:
        directory = MOVE_ROOT / gen
        count = len([f for f in directory.glob("技能*.json")]) if directory.is_dir() else 0
        out.append({"gen": gen, "count": count, "available": directory.is_dir()})
    return out


def _file(gen: str, move_id: int) -> "object":
    directory = MOVE_ROOT / gen
    if not directory.is_dir():
        raise ValueError(f"未知世代 {gen}")
    return directory / f"技能{move_id}.json"


def list_moves(gen: str) -> list[dict]:
    directory = MOVE_ROOT / gen
    if not directory.is_dir():
        raise ValueError(f"未知世代 {gen}")
    rows = []
    for file in sorted(directory.glob("技能*.json")):
        match = FILE_PATTERN.match(file.name)
        if not match:
            continue
        try:
            data = json.loads(file.read_text(encoding="utf-8"))
        except ValueError:
            continue
        rows.append({
            "moveIndexId": data.get("moveIndexId", int(match.group(1))),
            "type": data.get("movePokemonType"),
            "damageType": data.get("moveDamageType"),
            "power": data.get("moveBasePower"),
            "pp": data.get("moveBasePp"),
            "accuracy": data.get("moveBaseAccuracy"),
            "priority": data.get("movePriority"),
        })
    return rows


def get_move(gen: str, move_id: int) -> dict:
    file = _file(gen, move_id)
    if not file.exists():
        raise ValueError(f"招式 {move_id} 在 {gen} 不存在")
    return json.loads(file.read_text(encoding="utf-8"))


def save_move(gen: str, move_id: int, data: dict) -> None:
    file = _file(gen, move_id)
    if not file.exists():
        raise ValueError(f"招式 {move_id} 在 {gen} 不存在（不支持新建）")
    if not isinstance(data, dict) or data.get("moveIndexId") != move_id:
        raise ValueError("moveIndexId 与文件不符，禁止修改")
    for field, (low, high) in SCALAR_FIELDS.items():
        if field in data:
            value = data[field]
            if not isinstance(value, int) or not low <= value <= high:
                raise ValueError(f"{field} 超出范围 {low}..{high}")
    snapshot(file)
    file.write_text(json.dumps(data, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
