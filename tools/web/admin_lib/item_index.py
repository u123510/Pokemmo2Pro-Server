"""Item.jsonc 只读索引：给商店编辑提供道具编号到名称的映射。"""

from __future__ import annotations

import threading

from . import jsonc_io
from .paths import ITEM_FILE

_LOCK = threading.Lock()
_CACHE: dict[int, str] | None = None
_CACHE_STAMP: float = 0.0


def _load() -> dict[int, str]:
    global _CACHE, _CACHE_STAMP
    with _LOCK:
        stamp = ITEM_FILE.stat().st_mtime if ITEM_FILE.exists() else 0.0
        if _CACHE is None or stamp != _CACHE_STAMP:
            items: dict[int, str] = {}
            if ITEM_FILE.exists():
                node = jsonc_io.loads(ITEM_FILE.read_text(encoding="utf-8"))
                for entry in node.value.get("itemInfos").value:
                    plain = jsonc_io.to_plain(entry)
                    items[int(plain["itemIndexId"])] = str(plain.get("name", ""))
            _CACHE = items
            _CACHE_STAMP = stamp
        return _CACHE


def item_name(item_id: int) -> str | None:
    items = _load()
    return items.get(item_id)


def exists(item_id: int) -> bool:
    return item_id in _load()


def search(query: str, limit: int = 30) -> list[dict]:
    """按编号（精确/前缀）或名称包含匹配道具。"""
    items = _load()
    query = query.strip()
    results: list[dict] = []
    if query.isdigit():
        item_id = int(query)
        if item_id in items:
            results.append({"itemId": item_id, "name": items[item_id]})
        for candidate in sorted(items):
            if len(results) >= limit:
                return results
            if candidate != item_id and str(candidate).startswith(query):
                results.append({"itemId": candidate, "name": items[candidate]})
    lowered = query.lower()
    if lowered:
        matches = [
            (item_id, name) for item_id, name in items.items()
            if lowered in name.lower() and not any(e["itemId"] == item_id for e in results)
        ]
        matches.sort(key=lambda entry: (len(entry[1]), entry[0]))
        for item_id, name in matches[: max(0, limit - len(results))]:
            results.append({"itemId": item_id, "name": name})
    return results
