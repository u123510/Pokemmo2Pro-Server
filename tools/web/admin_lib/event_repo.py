"""事件/节日 NPC 管理：按 eventId 分组统计与批量启用/停用。

数据源是 resource/npc/custom 的版本 2 配置（eventId -1 普通、0..6 节日分类）。
"""

from __future__ import annotations

from . import custom_npc_repo

EVENT_NAMES = {
    -1: "普通 NPC",
    0: "万圣节",
    1: "圣诞节",
    2: "春节",
    3: "情人节",
    4: "复活节",
    5: "夏日祭",
    6: "周年庆",
}


def overview() -> list[dict]:
    groups: dict[int, dict] = {}
    for npc in custom_npc_repo.list_all():
        if npc.get("parseError"):
            continue
        event_id = int(npc.get("eventId", -1))
        group = groups.setdefault(event_id, {
            "eventId": event_id,
            "name": EVENT_NAMES.get(event_id, f"未知分类 {event_id}"),
            "total": 0,
            "enabled": 0,
            "npcs": [],
        })
        group["total"] += 1
        if npc.get("enabled"):
            group["enabled"] += 1
        group["npcs"].append({
            "relPath": npc["relPath"], "map": npc["map"], "entityIdx": npc["entityIdx"],
            "enabled": npc["enabled"],
        })
    return sorted(groups.values(), key=lambda g: g["eventId"])


def set_event_enabled(event_id: int, enabled: bool) -> int:
    """批量启用/停用一个事件分类下全部自定义 NPC，返回处理条数。"""
    changed = 0
    for npc in custom_npc_repo.list_all():
        if npc.get("parseError") or int(npc.get("eventId", -1)) != event_id:
            continue
        if npc.get("enabled") != enabled:
            custom_npc_repo.set_enabled(npc["relPath"], enabled)
            changed += 1
    return changed
