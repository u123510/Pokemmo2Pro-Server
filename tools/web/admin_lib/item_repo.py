"""Item.jsonc 道具字典：显示层字段编辑 + 全库引用扫描。

注意：服务端运行时字典是 Item.bin（ItemManager 加载），本文件只影响显示/参考层；
改 itemIndexId 属于二期的事务式联动，这里先提供引用扫描作为安全网。
"""

from __future__ import annotations

from . import jsonc_io
from .paths import PROJECT_ROOT, RESOURCE_ROOT

ITEM_FILE = RESOURCE_ROOT / "item" / "Item.jsonc"
EDITABLE_FIELDS = ("name", "desc", "regionIndexId", "iconId", "nameLocalStringIndexId", "descLocalStringIndexId")
INT_FIELDS = {"regionIndexId", "iconId", "nameLocalStringIndexId", "descLocalStringIndexId"}

# 引用扫描范围：(文件, 数组键, id 字段名)
REFERENCE_TARGETS = [
    (RESOURCE_ROOT / "item" / "ItemUse.jsonc", "itemEffects", "itemIndexId"),
    (RESOURCE_ROOT / "gift" / "GiftShop.jsonc", "items", "itemIndexId"),
    (RESOURCE_ROOT / "gift" / "Gift.jsonc", "giftPokemons", None),  # item 字段特殊处理
]

_cache_doc: jsonc_io.Value | None = None
_cache_stamp: float = 0.0


def _load() -> jsonc_io.Value:
    global _cache_doc, _cache_stamp
    stamp = ITEM_FILE.stat().st_mtime
    if _cache_doc is None or stamp != _cache_stamp:
        _cache_doc = jsonc_io.loads(ITEM_FILE.read_text(encoding="utf-8"))
        _cache_stamp = stamp
    return _cache_doc


def _save(doc: jsonc_io.Value) -> None:
    from .backups import snapshot
    from .paths import check_writable

    check_writable(ITEM_FILE)
    snapshot(ITEM_FILE)
    ITEM_FILE.write_text(jsonc_io.dumps(doc), encoding="utf-8")
    global _cache_stamp
    _cache_stamp = 0.0


def _items_node(doc: jsonc_io.Value) -> jsonc_io.Value:
    return doc.value["itemInfos"]


def list_items() -> list[dict]:
    doc = _load()
    rows = []
    for entry in _items_node(doc).value:
        data = jsonc_io.to_plain(entry)
        if isinstance(data, dict):
            rows.append({k: data.get(k) for k in ("itemIndexId", "name", "desc", "regionIndexId", "iconId")})
    return rows


def get_item(item_id: int) -> dict:
    doc = _load()
    for entry in _items_node(doc).value:
        data = jsonc_io.to_plain(entry)
        if isinstance(data, dict) and data.get("itemIndexId") == item_id:
            return data
    raise ValueError(f"道具 {item_id} 不存在")


def save_item_fields(item_id: int, changes: dict) -> None:
    doc = _load()
    for entry in _items_node(doc).value:
        data = jsonc_io.to_plain(entry)
        if not isinstance(data, dict) or data.get("itemIndexId") != item_id:
            continue
        for field, value in changes.items():
            if field not in EDITABLE_FIELDS:
                raise ValueError(f"字段 {field} 不可编辑")
            if field in INT_FIELDS:
                if not isinstance(value, int) or isinstance(value, bool) or value < 0:
                    raise ValueError(f"{field} 必须是非负整数")
            elif field in ("name", "desc"):
                value = str(value or "")
                if len(value) > 120:
                    raise ValueError(f"{field} 过长")
            jsonc_io.set_value(entry, field, value)
        _save(doc)
        return
    raise ValueError(f"道具 {item_id} 不存在")


def create_item(item_id: int, fields: dict) -> None:
    doc = _load()
    for entry in _items_node(doc).value:
        data = jsonc_io.to_plain(entry)
        if isinstance(data, dict) and data.get("itemIndexId") == item_id:
            raise ValueError(f"道具 {item_id} 已存在")
    row = jsonc_io.new_object()
    jsonc_io.set_value(row, "itemIndexId", int(item_id))
    for field in EDITABLE_FIELDS:
        if field in fields:
            jsonc_io.set_value(row, field, fields[field])
        elif field in INT_FIELDS:
            jsonc_io.set_value(row, field, 0)
        else:
            jsonc_io.set_value(row, field, "")
    _items_node(doc).value.append(row)
    _save(doc)


def delete_item(item_id: int) -> None:
    refs = scan_references(item_id)
    if refs:
        raise ValueError(f"道具被 {len(refs)} 处引用，先解除引用再删除：见引用扫描")
    doc = _load()
    node = _items_node(doc)
    for index, entry in enumerate(node.value):
        data = jsonc_io.to_plain(entry)
        if isinstance(data, dict) and data.get("itemIndexId") == item_id:
            node.value.pop(index)
            _save(doc)
            return
    raise ValueError(f"道具 {item_id} 不存在")


def scan_references(item_id: int) -> list[dict]:
    """扫描资源文件中对该道具 ID 的全部引用（商店、礼包、ItemUse、服务端常量）。"""
    from . import shop_repo

    refs: list[dict] = []
    for shop in shop_repo.list_shops():
        for index, item in enumerate(shop.get("items") or []):
            if item.get("itemId") == item_id:
                refs.append({"file": f"resource/shop/{shop['relPath']}", "where": f"items[{index}].itemId",
                             "shopId": shop.get("shopId")})
    for file, array_key, id_field in REFERENCE_TARGETS:
        if not file.exists():
            continue
        try:
            doc = jsonc_io.loads(file.read_text(encoding="utf-8"))
        except (OSError, jsonc_io.JsoncError):
            continue
        container = doc.value.get(array_key)
        if container is None or container.kind != "list":
            continue
        rel = file.relative_to(PROJECT_ROOT).as_posix()
        for index, entry in enumerate(container.value):
            data = jsonc_io.to_plain(entry)
            if not isinstance(data, dict):
                continue
            if id_field is not None and data.get(id_field) == item_id:
                refs.append({"file": rel, "where": f"{array_key}[{index}].{id_field}", "rowId": data.get("id")})
            if id_field is None and array_key == "giftPokemons" and data.get("item") == item_id and item_id:
                refs.append({"file": rel, "where": f"{array_key}[{index}].item", "rowId": data.get("giftId")})
    # ItemUse.jsonc 与商店可能重名，已覆盖；服务端硬编码提示
    if item_id == 349:
        refs.append({"file": "server.game .../ItemManager.java", "where": "OAK_PARCEL_ITEM_ID（硬编码，需改代码）"})
    return refs


def next_free_id(region_index_id: int) -> int:
    """推荐该地区段的下一个空闲编号（按现有最大值 +1）。"""
    doc = _load()
    biggest = region_index_id * 1000
    for entry in _items_node(doc).value:
        data = jsonc_io.to_plain(entry)
        if isinstance(data, dict) and data.get("regionIndexId") == region_index_id:
            biggest = max(biggest, int(data.get("itemIndexId") or 0))
    return biggest + 1
