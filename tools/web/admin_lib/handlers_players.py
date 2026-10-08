"""玩家域路由：账号、角色、背包、宝可梦（数据库）。"""

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

from . import db, dex_repo, item_index


def accounts_list(params: dict, _handler=None) -> dict:
    return {"accounts": db.accounts_overview(_one(params, "q")), "db": db.label()}


def account_ban(body: dict) -> dict:
    account_id = int(body.get("accountId", 0))
    banned = bool(body.get("banned"))
    db.set_ban(account_id, banned, str(body.get("reason", "")))
    _audit("account_ban", {"accountId": account_id, "banned": banned, "reason": body.get("reason", "")})
    return {"ok": True, "message": "已封禁（该账号无法再登录；在线会话不受影响）" if banned else "已解封"}


def account_password(body: dict) -> dict:
    account_id = int(body.get("accountId", 0))
    db.set_account_password(account_id, str(body.get("password", "")))
    _audit("account_password", {"accountId": account_id})
    return {"ok": True, "message": "密码已重置"}


def account_delete(body: dict) -> dict:
    account_id = int(body.get("accountId", 0))
    if not body.get("confirm") == account_id:
        raise ApiError("请传 confirm=账号ID 以确认删除（不可恢复）")
    db.delete_account_full(account_id)
    _audit("account_delete", {"accountId": account_id})
    return {"ok": True, "message": f"账号 {account_id} 及其角色/道具/宝可梦已删除"}


def character_rename(body: dict) -> dict:
    character_id = int(body.get("characterId", 0))
    name = db.rename_character(character_id, str(body.get("name", "")), bool(body.get("force")))
    _audit("character_rename", {"characterId": character_id, "name": name})
    return {"ok": True, "message": f"已改名 {name}（在线时改名会被游戏服覆盖，建议离线操作）"}


def character_currency(body: dict) -> dict:
    character_id = int(body.get("characterId", 0))
    field = str(body.get("field", "money"))
    db.set_character_currency(character_id, field, int(body.get("value", 0)), bool(body.get("force")))
    _audit("character_currency", {"characterId": character_id, "field": field, "value": body.get("value")})
    return {"ok": True, "message": f"{field} 已更新（在线修改会被覆盖，建议离线操作）"}


def character_teleport(body: dict) -> dict:
    character_id = int(body.get("characterId", 0))
    db.teleport_character(
        character_id, int(body.get("regionId", 0)), int(body.get("mapGroup", 0)),
        int(body.get("mapId", 0)), int(body.get("x", 0)), int(body.get("y", 0)),
        int(body.get("z", 0)), bool(body.get("force")),
    )
    _audit("character_teleport", {"characterId": character_id,
                                  "pos": [body.get("regionId"), body.get("mapGroup"), body.get("mapId"),
                                          body.get("x"), body.get("y"), body.get("z")]})
    return {"ok": True, "message": "坐标已更新（登录后生效）"}


def player_items(params: dict, body: dict | None = None) -> dict:
    character_id = _int(params, "characterId")
    rows = db.list_items(character_id)
    for row in rows:
        row["item_name"] = item_index.item_name(row["item_index_id"])
    return {"character": db.character_detail(character_id), "items": rows}


def player_item_add(body: dict) -> dict:
    character_id = int(body.get("characterId", 0))
    item_id = db.add_item(character_id, int(body.get("itemIndexId", 0)), int(body.get("amount", 1)),
                          int(body.get("inventoryId", 1)), bool(body.get("force")))
    _audit("player_item_add", {"characterId": character_id, "itemIndexId": body.get("itemIndexId"),
                               "amount": body.get("amount"), "inventoryId": body.get("inventoryId")})
    return {"ok": True, "message": f"已发放道具（记录 {item_id}）"}


def player_item_update(body: dict) -> dict:
    character_id = int(body.get("characterId", 0))
    db.update_item(character_id, int(body.get("itemId", 0)), int(body.get("amount", 1)), bool(body.get("force")))
    _audit("player_item_update", {"characterId": character_id, "itemId": body.get("itemId"),
                                  "amount": body.get("amount")})
    return {"ok": True, "message": "数量已更新"}


def player_item_delete(body: dict) -> dict:
    character_id = int(body.get("characterId", 0))
    db.delete_item(character_id, int(body.get("itemId", 0)), bool(body.get("force")))
    _audit("player_item_delete", {"characterId": character_id, "itemId": body.get("itemId")})
    return {"ok": True, "message": "道具已删除"}


def player_pokemon(params: dict, _handler=None) -> dict:
    character_id = _int(params, "characterId")
    rows = db.list_pokemon(character_id)
    names = dex_repo.pokemon_names()
    for row in rows:
        row["dex_name"] = names.get(row["dex_id"], "")
        row["held_item_name"] = item_index.item_name(row["held_item"] or 0)
    return {"character": db.character_detail(character_id), "pokemon": rows}


def player_pokemon_update(body: dict) -> dict:
    character_id = int(body.get("characterId", 0))
    db.update_pokemon(character_id, int(body.get("pokemonId", 0)), body.get("changes") or {},
                      bool(body.get("force")))
    _audit("player_pokemon_update", {"characterId": character_id, "pokemonId": body.get("pokemonId"),
                                     "fields": sorted((body.get("changes") or {}).keys())})
    return {"ok": True, "message": "宝可梦已更新"}


def player_pokemon_delete(body: dict) -> dict:
    character_id = int(body.get("characterId", 0))
    db.delete_pokemon(character_id, int(body.get("pokemonId", 0)), bool(body.get("force")))
    _audit("player_pokemon_delete", {"characterId": character_id, "pokemonId": body.get("pokemonId")})
    return {"ok": True, "message": "宝可梦已删除"}



GET_ROUTES = {
    "/api/accounts": accounts_list,
    "/api/player/items": player_items,
    "/api/player/pokemon": player_pokemon,
}

POST_ROUTES = {
    "/api/account/ban": account_ban,
    "/api/account/password": account_password,
    "/api/account/delete": account_delete,
    "/api/character/rename": character_rename,
    "/api/character/currency": character_currency,
    "/api/character/teleport": character_teleport,
    "/api/player/item/add": player_item_add,
    "/api/player/item/update": player_item_update,
    "/api/player/item/delete": player_item_delete,
    "/api/player/pokemon/update": player_pokemon_update,
    "/api/player/pokemon/delete": player_pokemon_delete,
}
