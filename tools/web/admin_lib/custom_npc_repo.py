"""resource/npc/custom 的扫描、校验与写回（离线版 //spawnnpc）。

规则对齐服务端：
- CustomNpcCodec：字段白名单、类型、版本 1/2 字段完整性、拒绝重复键
- CustomNpcDefinition：地图名安全正则、序号 >=100000、外观/移动/坐标范围
- CustomNpcStore：16 KiB、目录=地区/地图、文件名=npc_<序号>.jsonc、原子写
- CustomNpcCatalog：序号不与原生/自定义冲突、出生格越界/重叠、单图 1024、总量 10000

出生格"可站立"只能按 blockData 碰撞位近似判断，服务端加载时仍会做最终校验。
"""

from __future__ import annotations

import os
import re
import tempfile
from pathlib import Path

from . import jsonc_io, map_index
from .backups import snapshot
from .paths import CUSTOM_NPC_DIR, check_writable

MAX_FILE_BYTES = 16384
MAX_FILES = 10000
MAX_MAP_NPCS = 1024
FIRST_ENTITY_IDX = 100000

BASE_FIELDS = ("version", "enabled", "map", "regionId", "entityIdx", "spriteId",
               "spriteRegion", "movementType", "leashX", "leashY", "x", "y", "z", "toward")
APPEARANCE_FIELDS = ("eventId", "sparkles", "spriteScale")
SAFE_MAP_NAME = re.compile(r"[A-Za-z0-9][A-Za-z0-9_-]{0,127}")
SPRITE_REGIONS = {0, 1, 2, 3, 4, 10}
GBA_MOVEMENTS = {0, 1, 2, 3, 6, 8, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24}
NDS_MOVEMENTS = {0, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20}
SPAWN_REGIONS = {0, 1, 3}


class CustomNpcError(ValueError):
    """自定义 NPC 配置非法或写入失败。"""


def _iter_files() -> list[Path]:
    if not CUSTOM_NPC_DIR.is_dir():
        return []
    return sorted(CUSTOM_NPC_DIR.rglob("npc_*.jsonc"))


def list_all() -> list[dict]:
    result = []
    for file in _iter_files():
        entry: dict = {
            "path": file.resolve().as_posix(),
            "relPath": file.relative_to(CUSTOM_NPC_DIR).as_posix(),
        }
        try:
            if file.stat().st_size > MAX_FILE_BYTES:
                raise CustomNpcError("文件超过 16 KiB")
            data = _read_definition(file)
            entry.update(data)
            entry["errors"] = []
        except (OSError, jsonc_io.JsoncError, CustomNpcError) as exc:
            entry["parseError"] = str(exc)
        result.append(entry)
    return result


def _read_definition(file: Path) -> dict:
    text = file.read_text(encoding="utf-8")
    node = jsonc_io.loads(text)
    data = jsonc_io.to_plain(node)
    if not isinstance(data, dict):
        raise CustomNpcError("自定义 NPC 文件必须只包含一个 JSON 对象")
    _validate_structure(data, require_complete=True)
    expected = file.relative_to(CUSTOM_NPC_DIR).as_posix().replace("\\", "/")
    wanted = f"{_region_name(data['regionId'])}/{data['map']}/npc_{data['entityIdx']}.jsonc"
    if expected != wanted:
        raise CustomNpcError(f"文件路径必须与地区、地图、序号一致: {wanted}")
    return data


def _region_name(region_id: int) -> str:
    name = map_index.REGION_NAMES.get(region_id)
    if name is None:
        raise CustomNpcError(f"地区编号 {region_id} 不支持（可用 0=关都、1=丰缘、3=神奥）")
    return name


def _validate_structure(data: dict, *, require_complete: bool) -> None:
    allowed = set(BASE_FIELDS) | set(APPEARANCE_FIELDS)
    unknown = set(data) - allowed
    if unknown:
        raise CustomNpcError(f"自定义 NPC 字段未知或重复: {', '.join(sorted(unknown))}")
    if require_complete and not set(BASE_FIELDS) <= set(data):
        raise CustomNpcError("自定义 NPC 配置字段不完整")
    version = data.get("version")
    if version not in (1, 2):
        raise CustomNpcError("自定义 NPC 配置版本必须为 1 或 2")
    has_appearance = set(APPEARANCE_FIELDS) <= set(data)
    if require_complete:
        if version == 1 and set(APPEARANCE_FIELDS) & set(data):
            raise CustomNpcError("事件分类、闪光或缩放需要自定义 NPC 配置版本 2")
        if version == 2 and not has_appearance:
            raise CustomNpcError("版本 2 必须完整填写 eventId、sparkles、spriteScale")
    for key in BASE_FIELDS:
        if key in ("map", "enabled"):
            continue
        if key in data and (not isinstance(data[key], int) or isinstance(data[key], bool)):
            raise CustomNpcError(f"自定义 NPC 字段必须是 32 位整数: {key}")
    if "enabled" in data and not isinstance(data["enabled"], bool):
        raise CustomNpcError("enabled 必须是 true 或 false")
    if "sparkles" in data and not isinstance(data["sparkles"], bool):
        raise CustomNpcError("sparkles 必须是 true 或 false")
    if "spriteScale" in data:
        scale = data["spriteScale"]
        if not isinstance(scale, (int, float)) or isinstance(scale, bool):
            raise CustomNpcError("NPC 缩放必须是 0.25..4.0 的有限数值")


def validate_full(data: dict, *, self_path: str = "", for_create: bool = False) -> list[str]:
    errors: list[str] = []
    try:
        _validate_structure(data, require_complete=True)
    except CustomNpcError as exc:
        return [str(exc)]
    if not isinstance(data.get("map"), str) or not SAFE_MAP_NAME.fullmatch(data["map"]):
        errors.append("自定义 NPC 地图名必须是安全的地图文件名，不含目录或扩展名")
    entity_idx = data.get("entityIdx")
    if not isinstance(entity_idx, int) or entity_idx < FIRST_ENTITY_IDX:
        errors.append("自定义 NPC 序号必须从 100000 开始，不与原生 NPC 序号混用")
        return errors
    sprite_id = data.get("spriteId", -1)
    if not 0 <= sprite_id <= 10000:
        errors.append("外观编号必须在 0..10000 范围内")
    if data.get("spriteRegion") not in SPRITE_REGIONS:
        errors.append("外观地区必须是 0、1、2、3、4 或 10，不是当前地图编号")
    movement = data.get("movementType", -1)
    if not 0 <= movement <= 127:
        errors.append("移动类型必须在 0..127 范围内")
    for leash in ("leashX", "leashY"):
        value = data.get(leash, -1)
        if not 0 <= value <= 4:
            errors.append(f"{leash} 必须在 0..4 范围内")
    region_id = data.get("regionId")
    if region_id not in SPAWN_REGIONS:
        errors.append("当前地区尚不支持自定义 NPC 生成（可用 0=关都、1=丰缘、3=神奥）")
    elif not 0 <= movement <= 127 or movement not in (GBA_MOVEMENTS if region_id in (0, 1) else NDS_MOVEMENTS):
        errors.append("移动类型不适用于当前地图或属于未接入的特殊事件行为；静止请填 0")
    x, y, z, toward = data.get("x", -1), data.get("y", -1), data.get("z", 0), data.get("toward", -1)
    if not 0 <= x <= 32767 or not 0 <= y <= 32767 or not -128 <= z <= 127 or not 0 <= toward <= 3:
        errors.append("自定义 NPC 的坐标、高度或朝向无效")
    if data.get("version") == 2:
        event_id = data.get("eventId", -1)
        if not -1 <= event_id <= 6:
            errors.append("事件编号必须在 -1..6 范围内，-1 表示普通 NPC")
        scale = data.get("spriteScale", 1.0)
        if not isinstance(scale, (int, float)) or isinstance(scale, bool) or not 0.25 <= scale <= 4.0:
            errors.append("NPC 缩放必须是 0.25..4.0 的有限数值")
    errors.extend(_placement_errors(data, self_path, for_create=for_create))
    return errors


def _placement_errors(data: dict, self_path: str, *, for_create: bool) -> list[str]:
    errors: list[str] = []
    info = map_index.get(_region_name(data["regionId"]), data["map"])
    if info is None:
        errors.append(f"自定义 NPC 地图不存在或未加载: {data['regionId']}/{data['map']}")
        return errors
    x, y, z = data["x"], data["y"], data["z"]
    if x >= info["width"] or y >= info["height"]:
        errors.append(f"自定义 NPC 出生格越界（地图 {info['width']}x{info['height']}）")
        return errors
    grid = map_index.block_grid(info)
    cell = grid[y][x]
    if cell["collision"] != 0:
        errors.append(f"自定义 NPC 出生格不可站立（碰撞={cell['collision']}，服务端仍会最终校验）")
    same_layer_gba = data["regionId"] in (0, 1)

    def overlaps(npc_z: int) -> bool:
        if same_layer_gba:
            return z < 0 or npc_z < 0 or z // 3 == npc_z // 3
        return z == npc_z

    for npc in info["npcs"]:
        if npc["x"] == x and npc["y"] == y and overlaps(npc["elevation"] or 0):
            errors.append(f"出生格与原生 NPC 序号 {npc['entityIdx']} 重叠")
    customs = list_all()
    for other in customs:
        if other["path"] == self_path or other.get("parseError"):
            continue
        if other["map"] == data["map"] and other["regionId"] == data["regionId"]:
            if for_create and other["entityIdx"] == data["entityIdx"]:
                errors.append(f"自定义 NPC 序号 {data['entityIdx']} 已存在: {other['relPath']}")
            if other["enabled"] and other["x"] == x and other["y"] == y and overlaps(other["z"]):
                errors.append(f"出生格与自定义 NPC {other['entityIdx']} 重叠")
    enabled_here = sum(
        1 for other in customs
        if other.get("enabled") and other["map"] == data["map"] and other["regionId"] == data["regionId"]
    )
    total = len(info["npcs"]) + enabled_here
    if for_create and total >= MAX_MAP_NPCS:
        errors.append(f"地图 NPC 总数超过 {MAX_MAP_NPCS}")
    if for_create and len(customs) >= MAX_FILES:
        errors.append(f"自定义 NPC 文件数量已达到 {MAX_FILES} 上限")
    return errors


def next_entity_idx(region_id: int, map_name: str) -> int:
    """对齐 CustomNpcCatalog.nextEntityIdx：该图自定义最大序号 +1，跳过原生占用。"""
    next_idx = FIRST_ENTITY_IDX
    for npc in list_all():
        if npc.get("map") == map_name and npc.get("regionId") == region_id:
            next_idx = max(next_idx, int(npc["entityIdx"]) + 1)
    info = map_index.get(map_index.REGION_NAMES.get(region_id, ""), map_name)
    native = map_index.native_entity_idxs(info) if info else set()
    while next_idx in native:
        next_idx += 1
    return next_idx


def _write_file(file: Path, data: dict) -> None:
    node = jsonc_io.from_plain({key: data[key] for key in _ordered_fields(data)})
    text = jsonc_io.dumps(node)
    check_writable(file)
    handle, temp_name = tempfile.mkstemp(dir=str(file.parent), prefix=".npc-", suffix=".tmp")
    temp = Path(temp_name)
    try:
        with os.fdopen(handle, "w", encoding="utf-8", newline="\n") as output:
            output.write(text)
            output.flush()
            os.fsync(output.fileno())
        os.replace(temp, file)
    finally:
        if temp.exists():
            temp.unlink()


def _ordered_fields(data: dict) -> list[str]:
    order = list(BASE_FIELDS) + [f for f in APPEARANCE_FIELDS if f in data]
    return order


def save_new(data: dict) -> Path:
    errors = validate_full(data, for_create=True)
    if errors:
        raise CustomNpcError("；".join(errors))
    region = _region_name(data["regionId"])
    file = CUSTOM_NPC_DIR / region / data["map"] / f"npc_{data['entityIdx']}.jsonc"
    if file.exists():
        raise CustomNpcError(f"自定义 NPC 文件已存在，禁止覆盖: {file}")
    file.parent.mkdir(parents=True, exist_ok=True)
    check_writable(file)
    _write_file(file, data)
    return file


def update(rel_path: str, data: dict) -> Path:
    file = CUSTOM_NPC_DIR / rel_path
    check_writable(file)
    if not file.is_file():
        raise CustomNpcError(f"自定义 NPC 文件不存在: {rel_path}")
    current = _read_definition(file)
    for key in ("regionId", "map", "entityIdx"):
        if data.get(key) != current[key]:
            raise CustomNpcError(f"{key} 不可在线修改（会破坏文件路径约定），请删除后重建")
    errors = validate_full(data, self_path=file.resolve().as_posix())
    if errors:
        raise CustomNpcError("；".join(errors))
    snapshot(file)
    _write_file(file, data)
    return file


def set_enabled(rel_path: str, enabled: bool) -> Path:
    file = CUSTOM_NPC_DIR / rel_path
    check_writable(file)
    if not file.is_file():
        raise CustomNpcError(f"自定义 NPC 文件不存在: {rel_path}")
    data = _read_definition(file)
    if data["enabled"] == enabled:
        return file
    data["enabled"] = enabled
    snapshot(file)
    _write_file(file, data)
    return file
