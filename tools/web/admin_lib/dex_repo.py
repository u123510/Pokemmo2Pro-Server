"""图鉴/捕捉配置：CaptureSpecies.jsonc（捕捉率等）+ Pokemon.jsonc（种类基础配置）。

两份文件都按「主键字段 + 可编辑字段」的行模型编辑；嵌套复杂结构
（进化链、招式表等）不在行编辑范围内。
"""

from __future__ import annotations

from . import jsonc_io
from .backups import snapshot
from .paths import RESOURCE_ROOT, check_writable

CAPTURE_FILE = RESOURCE_ROOT / "pokemon" / "CaptureSpecies.jsonc"
POKEMON_FILE = RESOURCE_ROOT / "pokemon" / "Pokemon.jsonc"

_cache: dict[str, tuple[jsonc_io.Value, jsonc_io.Value, float]] = {}


def _load(file, key: str) -> tuple[jsonc_io.Value, jsonc_io.Value]:
    stamp = file.stat().st_mtime
    cached = _cache.get(str(file))
    if cached is None or cached[2] != stamp:
        doc = jsonc_io.loads(file.read_text(encoding="utf-8"))
        node = doc.value.get(key)
        if node is None or node.kind != "list":
            raise ValueError(f"{file.name} 缺少数组 {key}")
        _cache[str(file)] = (doc, node, stamp)
    doc, node, _ = _cache[str(file)]
    return doc, node


def _save(file, doc: jsonc_io.Value) -> None:
    check_writable(file)
    snapshot(file)
    file.write_text(jsonc_io.dumps(doc), encoding="utf-8")
    _cache.pop(str(file), None)


# -- CaptureSpecies ---------------------------------------------------------------

CAPTURE_EDITABLE = ("catchRate", "baseHappiness")


def list_capture(q: str = "", limit: int = 80) -> list[dict]:
    _doc, node = _load(CAPTURE_FILE, "species")
    keyword = (q or "").strip().lower()
    names = _pokemon_names()
    rows = []
    for entry in node.value:
        data = jsonc_io.to_plain(entry)
        if not isinstance(data, dict):
            continue
        dex_id = data.get("pokemonIndexId")
        name = names.get(dex_id, "")
        if keyword and keyword not in str(dex_id) and keyword not in name.lower():
            continue
        rows.append({"pokemonIndexId": dex_id, "name": name,
                     "catchRate": data.get("catchRate"), "baseHappiness": data.get("baseHappiness")})
        if len(rows) >= limit:
            break
    return rows


def save_capture(dex_id: int, changes: dict) -> None:
    doc, node = _load(CAPTURE_FILE, "species")
    for entry in node.value:
        data = jsonc_io.to_plain(entry)
        if isinstance(data, dict) and data.get("pokemonIndexId") == dex_id:
            for field in CAPTURE_EDITABLE:
                if field in changes:
                    value = int(changes[field] or 0)
                    if not 0 <= value <= 255:
                        raise ValueError(f"{field} 必须在 0..255")
                    jsonc_io.set_value(entry, field, value)
            _save(CAPTURE_FILE, doc)
            return
    raise ValueError(f"图鉴编号 {dex_id} 不存在")


# -- Pokemon.jsonc（种类基础配置，字段编辑 + 原始 JSON 查看） --------------------------

POKEMON_FLAT_FIELDS = ("name", "exp_type", "obtainable", "gender_ratio")


def _pokemon_names() -> dict[int, str]:
    try:
        _doc, node = _load(POKEMON_FILE, "pokemonInfos")
    except (OSError, ValueError, jsonc_io.JsoncError):
        return {}
    names = {}
    for entry in node.value:
        data = jsonc_io.to_plain(entry)
        if isinstance(data, dict) and "id" in data:
            names[int(data["id"])] = str(data.get("name", ""))
    return names


def pokemon_names() -> dict[int, str]:
    return _pokemon_names()


def get_pokemon_species(dex_id: int) -> dict:
    _doc, node = _load(POKEMON_FILE, "pokemonInfos")
    for entry in node.value:
        data = jsonc_io.to_plain(entry)
        if isinstance(data, dict) and data.get("id") == dex_id:
            return data
    raise ValueError(f"图鉴编号 {dex_id} 不存在于 Pokemon.jsonc")


def save_pokemon_flat_fields(dex_id: int, changes: dict) -> None:
    doc, node = _load(POKEMON_FILE, "pokemonInfos")
    for entry in node.value:
        data = jsonc_io.to_plain(entry)
        if isinstance(data, dict) and data.get("id") == dex_id:
            for field in POKEMON_FLAT_FIELDS:
                if field not in changes:
                    continue
                if field == "name":
                    value = str(changes[field] or "")
                    if not value or len(value) > 20:
                        raise ValueError("名称非法")
                else:
                    value = changes[field]
                jsonc_io.set_value(entry, field, value)
            _save(POKEMON_FILE, doc)
            return
    raise ValueError(f"图鉴编号 {dex_id} 不存在")
