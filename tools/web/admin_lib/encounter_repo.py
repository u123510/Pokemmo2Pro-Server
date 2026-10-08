"""遇敌表编辑：地图 JSON 的 encounterFields + wildEncounters。

地图 JSON 为无注释的标准 JSON（2 空格缩进），用 json 模块读写；
原生地图文件只允许改这两个遇敌相关字段，其余内容原样保留。
"""

from __future__ import annotations

import json
import re

from . import map_index
from .backups import snapshot
from .paths import MAP_DIR

GROUP_KEYS = ("land_mons", "water_mons", "rock_smash_mons", "fishing_mons")
_SPECIES_PATTERN = re.compile(r"#define\s+(SPECIES_\w+)\s+(\d+)")


def get_encounters(region: str, map_name: str) -> dict:
    info = map_index.get(region, map_name)
    if info is None:
        raise ValueError(f"地图不存在: {region}/{map_name}")
    data = json.loads((MAP_DIR / info["dir"] / f"{map_name}.json").read_text(encoding="utf-8"))
    return {
        "region": region,
        "name": map_name,
        "encounterFields": data.get("encounterFields", []),
        "wildEncounters": data.get("wildEncounters", []),
    }


def save_encounters(region: str, map_name: str, encounter_fields: list, wild_encounters: list) -> None:
    info = map_index.get(region, map_name)
    if info is None:
        raise ValueError(f"地图不存在: {region}/{map_name}")
    file = MAP_DIR / info["dir"] / f"{map_name}.json"
    if file.resolve().parent != (MAP_DIR / info["dir"]).resolve():
        raise ValueError("路径校验失败")
    data = json.loads(file.read_text(encoding="utf-8"))
    if not isinstance(encounter_fields, list) or not isinstance(wild_encounters, list):
        raise ValueError("数据格式错误")
    for group in wild_encounters:
        if not isinstance(group, dict):
            raise ValueError("wildEncounters 条目必须是对象")
        for key in GROUP_KEYS:
            section = group.get(key)
            if section is None:
                continue
            rate = section.get("encounter_rate")
            if not isinstance(rate, int) or not 0 <= rate <= 100:
                raise ValueError(f"{key}.encounter_rate 必须在 0..100")
            for mon in section.get("mons") or []:
                if not isinstance(mon, dict) or not isinstance(mon.get("species"), str):
                    raise ValueError(f"{key}.mons 条目缺少 species")
                levels = (mon.get("min_level"), mon.get("max_level"))
                if not all(isinstance(v, int) for v in levels) or not 1 <= levels[0] <= levels[1] <= 100:
                    raise ValueError(f"{key}.mons 等级范围非法")
    snapshot(file)
    data["encounterFields"] = encounter_fields
    data["wildEncounters"] = wild_encounters
    file.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def species_options() -> list[dict]:
    """种类下拉：来自反编译工程 include/constants/species.h 的 SPECIES_ 枚举。"""
    from .map_render import _decomp_root

    root = _decomp_root("kanto")
    header = root / "include" / "constants" / "species.h" if root else None
    if header is None or not header.exists():
        return []
    options = []
    for match in _SPECIES_PATTERN.finditer(header.read_text(encoding="utf-8", errors="replace")):
        options.append({"species": match.group(1), "dexId": int(match.group(2))})
    return options
