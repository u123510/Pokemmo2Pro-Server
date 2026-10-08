"""世界路由：商店、自定义 NPC、地图、道具搜索。"""

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

from . import custom_npc_repo, item_index, map_index, map_render, shop_repo


def shop_detail(params: dict, _handler=None) -> dict:
    rel = _one(params, "relPath")
    _node, data, file = shop_repo.load_doc(rel)
    names = {item["itemId"]: item_index.item_name(item["itemId"])
             for item in data.get("items", []) if isinstance(item, dict)}
    return {"relPath": rel, "data": data, "itemNames": names,
            "errors": shop_repo.validate(data, self_path=file.resolve().as_posix())}


def shop_validate(body: dict) -> dict:
    data = body.get("data")
    if not isinstance(data, dict):
        raise ApiError("缺少 data 对象")
    rel = str(body.get("relPath", ""))
    self_path = ""
    if rel:
        file = shop_repo.SHOP_DIR / rel
        if file.is_file():
            self_path = file.resolve().as_posix()
    return {"errors": shop_repo.validate(data, self_path=self_path)}


def shop_save(body: dict) -> dict:
    rel = str(body.get("relPath", ""))
    data = body.get("data")
    if not isinstance(data, dict):
        raise ApiError("缺少 data 对象")
    shop_repo.save(rel, data)
    _audit("shop_save", {"relPath": rel, "shopId": data.get("shopId")})
    return {"ok": True, "relPath": rel, "message": "已保存。游戏内输入 //reloadshops 生效。"}


def shop_create(body: dict) -> dict:
    rel = str(body.get("relPath", "")).strip()
    data = body.get("data")
    if not isinstance(data, dict):
        raise ApiError("缺少 data 对象")
    file = shop_repo.create(rel, data)
    _audit("shop_create", {"relPath": rel})
    return {"ok": True, "relPath": file.relative_to(shop_repo.SHOP_DIR).as_posix(),
            "message": "已创建。游戏内输入 //reloadshops 生效。"}


def shop_delete(body: dict) -> dict:
    rel = str(body.get("relPath", ""))
    shop_repo.delete(rel)
    _audit("shop_delete", {"relPath": rel})
    return {"ok": True, "message": "已删除（原文件移入备份目录）"}


def npc_validate(body: dict) -> dict:
    data = body.get("data")
    if not isinstance(data, dict):
        raise ApiError("缺少 data 对象")
    create = bool(body.get("create"))
    self_path = ""
    if not create:
        rel = str(body.get("relPath", ""))
        file = custom_npc_repo.CUSTOM_NPC_DIR / rel
        if file.is_file():
            self_path = file.resolve().as_posix()
    return {"errors": custom_npc_repo.validate_full(data, self_path=self_path, for_create=create)}


def npc_save(body: dict) -> dict:
    data = body.get("data")
    if not isinstance(data, dict):
        raise ApiError("缺少 data 对象")
    if body.get("create"):
        file = custom_npc_repo.save_new(data)
        rel = file.relative_to(custom_npc_repo.CUSTOM_NPC_DIR).as_posix()
        _audit("custom_npc_create", {"relPath": rel})
        return {"ok": True, "relPath": rel, "message": "已创建。需重启游戏服生效。"}
    rel = str(body.get("relPath", ""))
    custom_npc_repo.update(rel, data)
    _audit("custom_npc_save", {"relPath": rel})
    return {"ok": True, "relPath": rel, "message": "已保存。需重启游戏服生效。"}


def npc_toggle(body: dict) -> dict:
    rel = str(body.get("relPath", ""))
    enabled = bool(body.get("enabled"))
    custom_npc_repo.set_enabled(rel, enabled)
    _audit("custom_npc_toggle", {"relPath": rel, "enabled": enabled})
    return {"ok": True, "message": ("已启用" if enabled else "已停用") + "。需重启游戏服生效。"}


def map_detail(params: dict, _handler=None) -> dict:
    import base64 as b64

    region, name = _one(params, "region"), _one(params, "name")
    info = map_index.get(region, name)
    if info is None:
        raise ApiError(f"地图不存在: {region}/{name}", HTTPStatus.NOT_FOUND)
    detail = {k: v for k, v in info.items() if not k.startswith("_")}
    region_id = info.get("regionId")
    detail["customs"] = [npc for npc in custom_npc_repo.list_all()
                         if npc.get("map") == name and npc.get("regionId") == region_id]
    if region_id is not None:
        detail["nextEntityIdx"] = custom_npc_repo.next_entity_idx(region_id, name)
    grid = map_index.block_grid(info)
    detail["collisionB64"] = b64.b64encode(
        bytes(1 if cell["collision"] else 0 for row in grid for cell in row)).decode("ascii")
    return detail



def map_image(params):
    """渲染地图 PNG，返回 (路径, 模式)；由 server 直接输出二进制。"""
    region, name = _one(params, "region"), _one(params, "name")
    scale_raw = _one(params, "scale", "2")
    try:
        scale = max(1, min(int(scale_raw), 4))
    except ValueError:
        scale = 2
    path, mode = map_render.render_map(region, name, scale)
    return path, mode


def world_maps(params, _handler=None):
    return {"maps": map_index.all_maps(), "regions": map_index.REGION_NAMES}


def world_shops(params, _handler=None):
    return {"shops": shop_repo.list_shops()}


def world_items(params, _handler=None):
    from . import item_index
    return {"items": item_index.search(_one(params, "q"))}


def world_npcs(params, _handler=None):
    return {"npcs": custom_npc_repo.list_all()}


def next_entity_idx(params, _handler=None):
    return {"entityIdx": custom_npc_repo.next_entity_idx(_int(params, "regionId"), _one(params, "map"))}


GET_ROUTES = {
    "/api/maps": world_maps,
    "/api/map": map_detail,
    "/api/shops": world_shops,
    "/api/shop": shop_detail,
    "/api/items": world_items,
    "/api/custom-npcs": world_npcs,
    "/api/next-entity-idx": next_entity_idx,
}

POST_ROUTES = {
    "/api/shop/save": shop_save,
    "/api/shop/create": shop_create,
    "/api/shop/delete": shop_delete,
    "/api/shop/validate": shop_validate,
    "/api/custom-npc/save": npc_save,
    "/api/custom-npc/validate": npc_validate,
    "/api/custom-npc/toggle": npc_toggle,
}
