"""resource/shop 的扫描、校验与写回。

校验逐条对齐 ShopConfigLoader（字段白名单、1 MiB、items<=1024、
价格可空、buyEnabled/sellEnabled 必填布尔）和 ShopNpcBindings
（地图名唯一且已加载、序号存在、NPC 不可被两家店重复绑定）。
"""

from __future__ import annotations

import os
import tempfile
from pathlib import Path

from . import custom_npc_repo, item_index, jsonc_io, map_index
from .backups import record_for_deletion, snapshot
from .paths import SHOP_DIR, check_writable

MAX_FILE_BYTES = 1024 * 1024
MAX_ITEMS = 1024
MAX_NPCS = 1024
SHOP_FIELDS = {"shopId", "buyEnabled", "sellEnabled", "items", "npcs"}
ITEM_FIELDS = {"itemId", "buyPrice", "sellPrice"}
NPC_FIELDS = {"map", "entityIdx"}


class ShopError(ValueError):
    """商店配置非法或写入失败。"""


def _iter_shop_files() -> list[Path]:
    if not SHOP_DIR.is_dir():
        return []
    return sorted(
        p for p in SHOP_DIR.rglob("*")
        if p.is_file() and not p.is_symlink() and p.suffix in (".json", ".jsonc")
    )


def list_shops() -> list[dict]:
    result = []
    for file in _iter_shop_files():
        entry: dict = {"path": file.resolve().as_posix(), "relPath": file.relative_to(SHOP_DIR).as_posix()}
        try:
            if file.stat().st_size > MAX_FILE_BYTES:
                raise ShopError("店铺配置超过 1 MiB")
            node = jsonc_io.loads(file.read_text(encoding="utf-8"))
            data = jsonc_io.to_plain(node)
            if not isinstance(data, dict):
                raise ShopError("店铺文件必须只包含一个 JSON 对象")
            entry.update({
                "shopId": data.get("shopId"),
                "buyEnabled": data.get("buyEnabled"),
                "sellEnabled": data.get("sellEnabled"),
                "itemCount": len(data.get("items", []) or []),
                "npcs": data.get("npcs"),
                "hasNpcsField": "npcs" in data,
                "errors": [],
            })
        except (OSError, jsonc_io.JsoncError, ShopError) as exc:
            entry["parseError"] = str(exc)
        result.append(entry)
    return result


def load_doc(rel_path: str) -> tuple[jsonc_io.Value, dict, Path]:
    file = SHOP_DIR / rel_path
    check_writable(file)
    if not file.is_file():
        raise ShopError(f"店铺文件不存在: {rel_path}")
    node = jsonc_io.loads(file.read_text(encoding="utf-8"))
    data = jsonc_io.to_plain(node)
    if not isinstance(data, dict):
        raise ShopError("店铺文件必须只包含一个 JSON 对象")
    return node, data, file


def _validate_structure(data: dict) -> None:
    unknown = set(data) - SHOP_FIELDS
    if unknown:
        raise ShopError(f"未知配置字段: {', '.join(sorted(unknown))}")
    if not isinstance(data.get("shopId"), str) or not data["shopId"]:
        raise ShopError("缺少字符串 shopId")
    for flag in ("buyEnabled", "sellEnabled"):
        if not isinstance(data.get(flag), bool):
            raise ShopError(f"{flag} 必须是 true 或 false")
    items = data.get("items")
    if not isinstance(items, list):
        raise ShopError("缺少 items 数组")
    if len(items) > MAX_ITEMS:
        raise ShopError("items 数量不能超过 1024")
    for index, item in enumerate(items):
        if not isinstance(item, dict):
            raise ShopError(f"items[{index}] 必须是 JSON 对象")
        unknown = set(item) - ITEM_FIELDS
        if unknown:
            raise ShopError(f"items[{index}] 存在未知字段: {', '.join(sorted(unknown))}")
        if not isinstance(item.get("itemId"), int) or isinstance(item.get("itemId"), bool):
            raise ShopError(f"items[{index}].itemId 必须是整数")
        for price in ("buyPrice", "sellPrice"):
            value = item.get(price)
            if value is not None and (not isinstance(value, int) or isinstance(value, bool)):
                raise ShopError(f"items[{index}].{price} 必须是整数或 null")
        if item.get("buyPrice") is None and item.get("sellPrice") is None:
            raise ShopError(f"items[{index}] 的买入价和回收价不能同时为 null（会从商店消失）")
    npcs = data.get("npcs")
    if npcs is not None:
        if not isinstance(npcs, list):
            raise ShopError("npcs 必须是数组；不绑定任何 NPC 请填写 []")
        if len(npcs) > MAX_NPCS:
            raise ShopError("npcs 数量不能超过 1024")
        for index, npc in enumerate(npcs):
            if not isinstance(npc, dict):
                raise ShopError(f"npcs[{index}] 必须是包含 map 和 entityIdx 的对象")
            unknown = set(npc) - NPC_FIELDS
            if unknown:
                raise ShopError(f"npcs[{index}] 存在未知字段: {', '.join(sorted(unknown))}")
            if not isinstance(npc.get("map"), str) or not npc["map"]:
                raise ShopError(f"npcs[{index}].map 必须是地图文件名字符串")
            if not isinstance(npc.get("entityIdx"), int) or isinstance(npc.get("entityIdx"), bool):
                raise ShopError(f"npcs[{index}].entityIdx 必须是整数")


def _binding_conflicts(data: dict, self_path: str) -> list[str]:
    """对齐 ShopNpcBindings：地图唯一且存在、序号存在、NPC 不重复绑定。"""
    errors: list[str] = []
    npcs = data.get("npcs")
    if npcs is None:
        return errors
    # 自定义 NPC 索引: (map name) -> set(entityIdx)
    custom: dict[str, set[int]] = {}
    for npc in custom_npc_repo.list_all():
        custom.setdefault(npc["map"], set()).add(npc["entityIdx"])
    # 其他店铺已占用的 NPC
    owners: dict[tuple[str, int], str] = {}
    for shop in list_shops():
        if shop["path"] == self_path or shop.get("parseError"):
            continue
        for npc in shop.get("npcs") or []:
            owners[(npc["map"], npc["entityIdx"])] = shop["shopId"]
    for npc in npcs:
        map_name, entity_idx = npc["map"], npc["entityIdx"]
        matches = map_index.find_by_name(map_name)
        if len(matches) != 1:
            errors.append(f"NPC 绑定地图未加载或名称不唯一: {map_name}")
            continue
        native = entity_idx in map_index.native_entity_idxs(matches[0])
        if not native and entity_idx not in custom.get(map_name, set()):
            errors.append(f"地图 {map_name} 中不存在 NPC 序号 {entity_idx}，请核对 entityIdx")
            continue
        owner = owners.get((map_name, entity_idx))
        if owner:
            errors.append(f"NPC 绑定冲突: 地图={map_name}, 序号={entity_idx}, 已被店铺 {owner} 占用")
    return errors


def validate(data: dict, self_path: str = "") -> list[str]:
    errors: list[str] = []
    try:
        _validate_structure(data)
    except ShopError as exc:
        errors.append(str(exc))
        return errors
    others = [
        s for s in list_shops()
        if s["path"] != self_path and s.get("shopId") == data["shopId"]
    ]
    if others:
        errors.append(f"shopId 重复，与 {others[0]['relPath']} 冲突")
    missing = [
        item["itemId"] for item in data.get("items", [])
        if isinstance(item, dict) and not item_index.exists(item["itemId"])
    ]
    if missing:
        errors.append(f"道具编号不存在: {', '.join(map(str, sorted(set(missing))))}")
    errors.extend(_binding_conflicts(data, self_path))
    return errors


def _merge_into_doc(existing: jsonc_io.Value, data: dict) -> jsonc_io.Value:
    """把编辑后的数据合并回原文档节点，尽量保留注释。

    商品按 itemId 匹配：老商品保留其节点与注释并更新价格，新商品追加。
    """
    merged = jsonc_io.Value("dict", {})
    for key in ("shopId", "buyEnabled", "sellEnabled"):
        jsonc_io.set_value(merged, key, data[key])
    old_items_node = existing.value.get("items")
    old_by_id: dict[int, jsonc_io.Value] = {}
    if old_items_node is not None and old_items_node.kind == "list":
        for child in old_items_node.value:
            if child.kind == "dict" and isinstance(child.value.get("itemId"), jsonc_io.Value):
                raw = child.value["itemId"].value
                if isinstance(raw, int):
                    old_by_id[raw] = child
    items_node = jsonc_io.new_array()
    items_node.leading = old_items_node.leading if old_items_node is not None else []
    for item in data["items"]:
        old_child = old_by_id.get(item["itemId"])
        if old_child is not None:
            jsonc_io.set_value(old_child, "itemId", item["itemId"])
            jsonc_io.set_value(old_child, "buyPrice", item["buyPrice"])
            jsonc_io.set_value(old_child, "sellPrice", item["sellPrice"])
            items_node.value.append(old_child)
        else:
            items_node.value.append(jsonc_io.from_plain(item))
    merged.value["items"] = items_node
    if "npcs" in data:
        old_npcs_node = existing.value.get("npcs")
        npcs_node = jsonc_io.from_plain(data["npcs"])
        if old_npcs_node is not None:
            npcs_node.leading = old_npcs_node.leading
        merged.value["npcs"] = npcs_node
    return merged


def save(rel_path: str, data: dict) -> Path:
    node, _old_data, file = load_doc(rel_path)
    errors = validate(data, self_path=file.resolve().as_posix())
    if errors:
        raise ShopError("；".join(errors))
    merged = _merge_into_doc(node, data)
    text = jsonc_io.dumps(merged)
    snapshot(file)
    _atomic_write(file, text)
    return file


def create(rel_path: str, data: dict) -> Path:
    file = (SHOP_DIR / rel_path)
    check_writable(file)
    if file.suffix not in (".json", ".jsonc"):
        raise ShopError("店铺文件扩展名必须是 .json 或 .jsonc")
    if file.exists():
        raise ShopError(f"店铺文件已存在，禁止覆盖: {rel_path}")
    errors = validate(data)
    if errors:
        raise ShopError("；".join(errors))
    file.parent.mkdir(parents=True, exist_ok=True)
    check_writable(file)
    _atomic_write(file, jsonc_io.dumps(jsonc_io.from_plain(data)))
    return file


def delete(rel_path: str) -> Path:
    _, _, file = load_doc(rel_path)
    return record_for_deletion(file)


def _atomic_write(file: Path, text: str) -> None:
    check_writable(file)
    handle, temp_name = tempfile.mkstemp(dir=str(file.parent), prefix=".shop-", suffix=".tmp")
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
