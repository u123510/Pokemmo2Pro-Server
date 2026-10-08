"""商城礼包（GiftShop.jsonc）与赠送宝可梦（Gift.jsonc）编辑。"""

from __future__ import annotations

from . import jsonc_io
from .backups import snapshot
from .paths import RESOURCE_ROOT, check_writable

GIFT_SHOP_FILE = RESOURCE_ROOT / "gift" / "GiftShop.jsonc"
GIFT_FILE = RESOURCE_ROOT / "gift" / "Gift.jsonc"

SHOP_FIELDS = ("id", "targetType", "category", "itemIndexId", "price", "originalPrice", "quantity", "featuredOrder")
GIFT_FIELDS = ("giftId", "pokemonIndexId", "level", "ability", "moves", "item", "ballType")


def _load_doc(file) -> jsonc_io.Value:
    return jsonc_io.loads(file.read_text(encoding="utf-8"))


def _write_doc(file, doc: jsonc_io.Value) -> None:
    check_writable(file)
    snapshot(file)
    file.write_text(jsonc_io.dumps(doc), encoding="utf-8")


def _array_node(file, key: str) -> tuple[jsonc_io.Value, jsonc_io.Value]:
    doc = _load_doc(file)
    node = doc.value.get(key)
    if node is None or node.kind != "list":
        raise ValueError(f"{file.name} 缺少数组 {key}")
    return doc, node


def list_gift_shop() -> list[dict]:
    _doc, node = _array_node(GIFT_SHOP_FILE, "items")
    return [jsonc_io.to_plain(e) for e in node.value if isinstance(jsonc_io.to_plain(e), dict)]


def save_gift_shop_row(row_id: int, changes: dict) -> None:
    doc, node = _array_node(GIFT_SHOP_FILE, "items")
    for entry in node.value:
        data = jsonc_io.to_plain(entry)
        if isinstance(data, dict) and data.get("id") == row_id:
            for field in SHOP_FIELDS:
                if field in changes:
                    if field == "id":
                        continue
                    value = changes[field]
                    if field != "itemIndexId" and not isinstance(value, int):
                        value = int(value or 0)
                    jsonc_io.set_value(entry, field, value)
            _write_doc(GIFT_SHOP_FILE, doc)
            return
    raise ValueError(f"礼包条目 {row_id} 不存在")


def add_gift_shop_row(data: dict) -> int:
    doc, node = _array_node(GIFT_SHOP_FILE, "items")
    existing = {jsonc_io.to_plain(e).get("id") for e in node.value if isinstance(jsonc_io.to_plain(e), dict)}
    next_id = max([i for i in existing if isinstance(i, int)], default=0) + 1
    row = jsonc_io.new_object()
    jsonc_io.set_value(row, "id", next_id)
    for field in SHOP_FIELDS[1:]:
        jsonc_io.set_value(row, field, int(data.get(field, 0) or 0))
    node.value.append(row)
    _write_doc(GIFT_SHOP_FILE, doc)
    return next_id


def delete_gift_shop_row(row_id: int) -> None:
    doc, node = _array_node(GIFT_SHOP_FILE, "items")
    for index, entry in enumerate(node.value):
        data = jsonc_io.to_plain(entry)
        if isinstance(data, dict) and data.get("id") == row_id:
            node.value.pop(index)
            _write_doc(GIFT_SHOP_FILE, doc)
            return
    raise ValueError(f"礼包条目 {row_id} 不存在")


# -- Gift.jsonc（赠送宝可梦） ----------------------------------------------------


def list_gifts() -> list[dict]:
    _doc, node = _array_node(GIFT_FILE, "giftPokemons")
    return [jsonc_io.to_plain(e) for e in node.value if isinstance(jsonc_io.to_plain(e), dict)]


def save_gift_row(gift_id: int, changes: dict) -> None:
    doc, node = _array_node(GIFT_FILE, "giftPokemons")
    for entry in node.value:
        data = jsonc_io.to_plain(entry)
        if isinstance(data, dict) and data.get("giftId") == gift_id:
            for field in GIFT_FIELDS:
                if field not in changes or field == "giftId":
                    continue
                value = changes[field]
                if field == "moves":
                    moves = value if isinstance(value, list) else []
                    if len(moves) != 4 or not all(isinstance(m, int) and 0 <= m <= 10000 for m in moves):
                        raise ValueError("moves 必须是 4 个招式编号")
                    jsonc_io.set_value(entry, "moves", moves)
                else:
                    jsonc_io.set_value(entry, field, int(value or 0))
            _write_doc(GIFT_FILE, doc)
            return
    raise ValueError(f"赠送条目 {gift_id} 不存在")


def add_gift_row(data: dict) -> int:
    doc, node = _array_node(GIFT_FILE, "giftPokemons")
    existing = [jsonc_io.to_plain(e).get("giftId") for e in node.value if isinstance(jsonc_io.to_plain(e), dict)]
    next_id = max([i for i in existing if isinstance(i, int)], default=0) + 1
    row = jsonc_io.new_object()
    jsonc_io.set_value(row, "giftId", next_id)
    jsonc_io.set_value(row, "pokemonIndexId", int(data.get("pokemonIndexId", 1)))
    jsonc_io.set_value(row, "level", int(data.get("level", 5)))
    jsonc_io.set_value(row, "ability", int(data.get("ability", 0)))
    moves = data.get("moves") if isinstance(data.get("moves"), list) else [0, 0, 0, 0]
    jsonc_io.set_value(row, "moves", [int(m) for m in (moves + [0, 0, 0, 0])[:4]])
    jsonc_io.set_value(row, "item", int(data.get("item", 0)))
    jsonc_io.set_value(row, "ballType", int(data.get("ballType", 3)))
    node.value.append(row)
    _write_doc(GIFT_FILE, doc)
    return next_id


def delete_gift_row(gift_id: int) -> None:
    doc, node = _array_node(GIFT_FILE, "giftPokemons")
    for index, entry in enumerate(node.value):
        data = jsonc_io.to_plain(entry)
        if isinstance(data, dict) and data.get("giftId") == gift_id:
            node.value.pop(index)
            _write_doc(GIFT_FILE, doc)
            return
    raise ValueError(f"赠送条目 {gift_id} 不存在")
