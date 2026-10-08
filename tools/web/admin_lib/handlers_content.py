"""内容域路由：道具字典、商城礼包、招式、图鉴、遇敌表、事件 NPC、审计。"""

from __future__ import annotations

from http import HTTPStatus

from .http import (
    ApiError,
    SESSION_COOKIE,
    _audit,
    _body,
    _int,
    _one,
    _q,
    _require_csrf,
    _require_session,
    _session_token,
)

from .paths import PROJECT_ROOT
from . import (
    audit,
    custom_npc_repo,
    dex_repo,
    encounter_repo,
    event_repo,
    gift_repo,
    item_index,
    item_repo,
    map_index,
    map_render,
    move_repo,
    shop_repo,
)


def overview(params: dict, _handler=None) -> dict:
    return {
        "shops": len(shop_repo.list_shops()),
        "customNpcs": len(custom_npc_repo.list_all()),
        "maps": len(map_index.all_maps()),
        "dictItems": len(item_repo.list_items()),
        "projectRoot": str(PROJECT_ROOT),
        "decomp": {
            "kanto": map_render._decomp_root("kanto") is not None,
            "hoenn": map_render._decomp_root("hoenn") is not None,
        },
        "hints": [
            "商店保存后需在游戏内执行 //reloadshops 生效",
            "自定义 NPC 保存后需重启游戏服；游戏服运行中请使用 //spawnnpc 命令",
            "在线角色的数据库修改会被游戏服覆盖，请尽量离线操作",
        ],
    }


def dict_items(params: dict, _handler=None) -> dict:
    q = _one(params, "q")
    rows = item_repo.list_items()
    if q:
        lowered = q.lower()
        rows = [r for r in rows if lowered in str(r.get("name", "")).lower()
                or lowered in str(r.get("itemIndexId", ""))]
    return {"items": rows[:200]}


def dict_item_get(params: dict, _handler=None) -> dict:
    return {"item": item_repo.get_item(_int(params, "itemId")),
            "refs": item_repo.scan_references(_int(params, "itemId"))}


def dict_item_save(body: dict) -> dict:
    item_id = int(body.get("itemId", 0))
    item_repo.save_item_fields(item_id, body.get("changes") or {})
    _audit("dict_item_save", {"itemId": item_id, "fields": sorted((body.get("changes") or {}).keys())})
    return {"ok": True, "message": "已保存（显示层字段；服务端行为由 Item.bin 决定）"}


def dict_item_create(body: dict) -> dict:
    item_id = int(body.get("itemId", 0))
    item_repo.create_item(item_id, body.get("fields") or {})
    _audit("dict_item_create", {"itemId": item_id})
    return {"ok": True, "message": f"已创建 {item_id}；服务端生效还需 Item.bin 有该道具"}


def dict_item_delete(body: dict) -> dict:
    item_id = int(body.get("itemId", 0))
    item_repo.delete_item(item_id)
    _audit("dict_item_delete", {"itemId": item_id})
    return {"ok": True, "message": "已删除"}


def dict_refs(params: dict, _handler=None) -> dict:
    return {"refs": item_repo.scan_references(_int(params, "itemId"))}


def gift_shop_list(params: dict, _handler=None) -> dict:
    return {"rows": gift_repo.list_gift_shop()}


def gift_shop_save(body: dict) -> dict:
    gift_repo.save_gift_shop_row(int(body.get("id", 0)), body.get("changes") or {})
    _audit("gift_shop_save", {"id": body.get("id")})
    return {"ok": True, "message": "礼包已保存（需重启游戏服或相应热重载生效）"}


def gift_shop_add(body: dict) -> dict:
    row_id = gift_repo.add_gift_shop_row(body.get("row") or {})
    _audit("gift_shop_add", {"id": row_id})
    return {"ok": True, "id": row_id, "message": f"已新增礼包条目 {row_id}"}


def gift_shop_delete(body: dict) -> dict:
    gift_repo.delete_gift_shop_row(int(body.get("id", 0)))
    _audit("gift_shop_delete", {"id": body.get("id")})
    return {"ok": True, "message": "已删除"}


def gift_list(params: dict, _handler=None) -> dict:
    return {"rows": gift_repo.list_gifts()}


def gift_save(body: dict) -> dict:
    gift_repo.save_gift_row(int(body.get("giftId", 0)), body.get("changes") or {})
    _audit("gift_save", {"giftId": body.get("giftId")})
    return {"ok": True, "message": "赠送宝可梦已保存"}


def gift_add(body: dict) -> dict:
    gift_id = gift_repo.add_gift_row(body.get("row") or {})
    _audit("gift_add", {"giftId": gift_id})
    return {"ok": True, "id": gift_id, "message": f"已新增赠送条目 {gift_id}"}


def gift_delete(body: dict) -> dict:
    gift_repo.delete_gift_row(int(body.get("giftId", 0)))
    _audit("gift_delete", {"giftId": body.get("giftId")})
    return {"ok": True, "message": "已删除"}


def moves_gens(params: dict, _handler=None) -> dict:
    return {"gens": move_repo.gens()}


def moves_list(params: dict, _handler=None) -> dict:
    return {"moves": move_repo.list_moves(_one(params, "gen", "gen3"))}


def move_get(params: dict, _handler=None) -> dict:
    return {"move": move_repo.get_move(_one(params, "gen", "gen3"), _int(params, "moveId"))}


def move_save(body: dict) -> dict:
    move_id = int(body.get("moveId", 0))
    move_repo.save_move(str(body.get("gen", "gen3")), move_id, body.get("move") or {})
    _audit("move_save", {"gen": body.get("gen"), "moveId": move_id})
    return {"ok": True, "message": "招式已保存（需重启游戏服生效）"}


def dex_capture_list(params: dict, _handler=None) -> dict:
    return {"rows": dex_repo.list_capture(_one(params, "q"))}


def dex_capture_save(body: dict) -> dict:
    dex_id = int(body.get("pokemonIndexId", 0))
    dex_repo.save_capture(dex_id, body.get("changes") or {})
    _audit("dex_capture_save", {"pokemonIndexId": dex_id})
    return {"ok": True, "message": "捕捉配置已保存（需重启游戏服生效）"}


def dex_species_get(params: dict, _handler=None) -> dict:
    dex_id = _int(params, "pokemonIndexId")
    species = dex_repo.get_pokemon_species(dex_id)
    return {"species": species, "flatFields": list(dex_repo.POKEMON_FLAT_FIELDS)}


def dex_species_save(body: dict) -> dict:
    dex_id = int(body.get("pokemonIndexId", 0))
    dex_repo.save_pokemon_flat_fields(dex_id, body.get("changes") or {})
    _audit("dex_species_save", {"pokemonIndexId": dex_id, "fields": sorted((body.get("changes") or {}).keys())})
    return {"ok": True, "message": "种类基础配置已保存"}


def encounter_get(params: dict, _handler=None) -> dict:
    return encounter_repo.get_encounters(_one(params, "region"), _one(params, "name"))


def encounter_save(body: dict) -> dict:
    encounter_repo.save_encounters(str(body.get("region", "")), str(body.get("name", "")),
                                   body.get("encounterFields") or [], body.get("wildEncounters") or [])
    _audit("encounter_save", {"map": f"{body.get('region')}/{body.get('name')}"})
    return {"ok": True, "message": "遇敌表已保存（需重启游戏服生效）"}


def species_options(params: dict, _handler=None) -> dict:
    names = dex_repo.pokemon_names()
    options = []
    for option in encounter_repo.species_options():
        option["name"] = names.get(option["dexId"], "")
        options.append(option)
    return {"options": options}


def event_overview(params: dict, _handler=None) -> dict:
    return {"groups": event_repo.overview()}


def event_set_enabled(body: dict) -> dict:
    event_id = int(body.get("eventId", -2))
    if event_id not in event_repo.EVENT_NAMES:
        raise ApiError("eventId 无效")
    changed = event_repo.set_event_enabled(event_id, bool(body.get("enabled")))
    _audit("event_set_enabled", {"eventId": event_id, "enabled": bool(body.get("enabled")), "changed": changed})
    return {"ok": True, "message": f"已{'启用' if body.get('enabled') else '停用'} {changed} 只 NPC（需重启游戏服）"}


def audit_recent(params: dict, _handler=None) -> dict:
    return {"entries": audit.recent(_int(params, "limit", 100))}



GET_ROUTES = {
    "/api/overview": overview,
    "/api/dict/items": dict_items,
    "/api/dict/item": dict_item_get,
    "/api/dict/refs": dict_refs,
    "/api/gifts/shop": gift_shop_list,
    "/api/gifts/list": gift_list,
    "/api/moves/gens": moves_gens,
    "/api/moves/list": moves_list,
    "/api/moves/move": move_get,
    "/api/dex/capture": dex_capture_list,
    "/api/dex/species": dex_species_get,
    "/api/encounters": encounter_get,
    "/api/species-options": species_options,
    "/api/events": event_overview,
    "/api/audit": audit_recent,
}

POST_ROUTES = {
    "/api/dict/item/save": dict_item_save,
    "/api/dict/item/create": dict_item_create,
    "/api/dict/item/delete": dict_item_delete,
    "/api/gifts/shop/save": gift_shop_save,
    "/api/gifts/shop/add": gift_shop_add,
    "/api/gifts/shop/delete": gift_shop_delete,
    "/api/gifts/save": gift_save,
    "/api/gifts/add": gift_add,
    "/api/gifts/delete": gift_delete,
    "/api/moves/save": move_save,
    "/api/dex/capture/save": dex_capture_save,
    "/api/dex/species/save": dex_species_save,
    "/api/encounters/save": encounter_save,
    "/api/events/set-enabled": event_set_enabled,
}
