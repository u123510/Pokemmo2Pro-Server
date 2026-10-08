"""数据库访问层：连接设置复用 tools/account_admin_db.py，玩家域操作集中在此。

所有更新都带双重条件（如 id + owner_id / trainer_id），并检查在线状态：
在线角色的数据由游戏服内存持有并回写，直接改库会被覆盖或产生竞态，
默认拒绝，需显式 force=True 并自担风险。
"""

from __future__ import annotations

import sys
import unicodedata
from pathlib import Path

TOOLS_DIR = Path(__file__).resolve().parents[2]
_DB = None


class DbError(ValueError):
    """玩家数据操作失败。"""


def _module():
    global _DB
    if _DB is None:
        sys.path.insert(0, str(TOOLS_DIR))
        try:
            _DB = __import__("account_admin_db")
        except ImportError as exc:
            raise DbError("数据库驱动未安装：请在 tools 目录执行 pip install -r requirements.txt") from exc
    return _DB


def label() -> str:
    return _module().database_label()


def _connect():
    try:
        return _module().database_connect()
    except DbError:
        raise
    except Exception as exc:  # noqa: BLE001
        raise DbError(f"数据库连接失败: {exc}") from exc


def _norm(value, field: str, max_len: int) -> str:
    try:
        return _module().normalize_name(str(value), field, max_len)
    except ValueError as exc:
        raise DbError(str(exc)) from exc


def is_online(character_id: int) -> bool:
    with _connect() as conn:
        row = conn.execute(
            "SELECT 1 FROM public.account_context WHERE character_id = %s AND is_log_out = FALSE",
            (character_id,),
        ).fetchone()
    return row is not None


def _require_offline(cursor, character_id: int, force: bool) -> None:
    row = cursor.execute(
        "SELECT 1 FROM public.account_context WHERE character_id = %s AND is_log_out = FALSE",
        (character_id,),
    ).fetchone()
    if row is not None and not force:
        raise DbError("该角色当前在线，游戏服内存会覆盖数据库修改；确要修改请带 force=true 自担风险")


# -- 账号 ---------------------------------------------------------------------


def accounts_overview(q: str = "") -> list[dict]:
    keyword = (q or "").strip().lower()
    with _connect() as conn:
        rows = conn.execute(
            """
            SELECT a.account_id, a.account_name, a.login_permission, a.ban_reason, a.created_at,
                   c.id, c.name, c.sex, c.money, c.coins,
                   c.region_id, c.map_header_id_or_gba_map_group_id, c.gba_map_id, c.x, c.y, c.z,
                   ctx.character_id
            FROM public."account" a
            LEFT JOIN public.character c ON c.account_id = a.account_id
            LEFT JOIN public.account_context ctx
              ON ctx.account_id = a.account_id AND ctx.character_id = c.id AND ctx.is_log_out = FALSE
            ORDER BY a.account_id, c.id
            """
        ).fetchall()
    accounts: dict[int, dict] = {}
    for row in rows:
        acc = accounts.setdefault(row[0], {
            "account_id": row[0], "account_name": row[1], "login_permission": row[2],
            "ban_reason": row[3], "created_at": str(row[4]) if row[4] else "",
            "banned": row[2] is not None and row[2] < 3,
            "characters": [],
        })
        if row[5] is not None:
            matches = (not keyword or keyword in str(row[1]).lower()
                       or keyword in str(row[6]).lower() or str(row[5]) == keyword)
            if matches:
                acc["characters"].append({
                    "id": row[5], "name": row[6], "sex": row[7], "money": row[8], "coins": row[9],
                    "region_id": row[10], "map_group": row[11], "map_id": row[12],
                    "x": row[13], "y": row[14], "z": row[15], "online": row[16] is not None,
                })
    result = [a for a in accounts.values()
              if not keyword or a["characters"] or keyword in a["account_name"].lower()
              or str(a["account_id"]) == keyword]
    return result


def set_ban(account_id: int, banned: bool, reason: str) -> None:
    with _connect() as conn:
        with conn.cursor() as cur:
            row = cur.execute(
                'SELECT 1 FROM public."account" WHERE account_id = %s FOR UPDATE', (account_id,)
            ).fetchone()
            if row is None:
                raise DbError(f"账号 {account_id} 不存在")
            if banned:
                cur.execute(
                    'UPDATE public."account" SET login_permission = 0, ban_reason = %s WHERE account_id = %s',
                    (reason or "违规", account_id),
                )
            else:
                cur.execute(
                    'UPDATE public."account" SET login_permission = 3, ban_reason = NULL WHERE account_id = %s',
                    (account_id,),
                )
        conn.commit()


def set_account_password(account_id: int, password: str) -> None:
    import hashlib

    if len(password) < 4:
        raise DbError("密码至少 4 位")
    digest = hashlib.sha1(password.encode("utf-8")).digest()
    with _connect() as conn:
        with conn.cursor() as cur:
            row = cur.execute(
                "SELECT 1 FROM public.character c JOIN public.\"account\" a ON a.account_id = c.account_id "
                "WHERE a.account_id = %s AND c.id IN (SELECT character_id FROM public.account_context WHERE is_log_out = FALSE)",
                (account_id,),
            ).fetchone()
            if row is not None:
                raise DbError("该账号有角色在线，请先等待下线再改密")
            row = cur.execute(
                'SELECT 1 FROM public."account" WHERE account_id = %s', (account_id,)
            ).fetchone()
            if row is None:
                raise DbError(f"账号 {account_id} 不存在")
            cur.execute(
                'UPDATE public."account" SET password = %s WHERE account_id = %s',
                (digest, account_id),
            )
        conn.commit()


def delete_account_full(account_id: int) -> None:
    try:
        _module().delete_account(account_id)
    except ValueError as exc:
        raise DbError(str(exc)) from exc


# -- 角色 ---------------------------------------------------------------------


def find_character(cursor, character_id: int) -> dict:
    row = cursor.execute(
        """
        SELECT c.id, c.account_id, c.name, c.sex, c.money, c.coins,
               c.region_id, c.map_header_id_or_gba_map_group_id, c.gba_map_id,
               c.x, c.y, c.z, ctx.character_id
        FROM public.character c
        LEFT JOIN public.account_context ctx
          ON ctx.character_id = c.id AND ctx.is_log_out = FALSE
        WHERE c.id = %s
        """,
        (character_id,),
    ).fetchone()
    if row is None:
        raise DbError(f"角色 {character_id} 不存在")
    return {
        "id": row[0], "account_id": row[1], "name": row[2], "sex": row[3],
        "money": row[4], "coins": row[5], "region_id": row[6], "map_group": row[7],
        "map_id": row[8], "x": row[9], "y": row[10], "z": row[11], "online": row[12] is not None,
    }


def character_detail(character_id: int) -> dict:
    with _connect() as conn:
        return find_character(conn.cursor(), character_id)


def rename_character(character_id: int, new_name: str, force: bool) -> str:
    name = _norm(new_name, "角色名", 12)
    with _connect() as conn:
        with conn.cursor() as cur:
            _require_offline(cur, character_id, force)
            row = cur.execute(
                "SELECT 1 FROM public.character WHERE lower(name) = lower(%s) AND id <> %s",
                (name, character_id),
            ).fetchone()
            if row is not None:
                raise DbError(f"角色名 {name} 已被占用")
            cur.execute("UPDATE public.character SET name = %s WHERE id = %s", (name, character_id))
            if cur.rowcount != 1:
                raise DbError(f"角色 {character_id} 不存在")
        conn.commit()
    return name


def set_character_currency(character_id: int, field: str, value: int, force: bool) -> None:
    if field not in ("money", "coins", "battle_points"):
        raise DbError("货币字段无效")
    try:
        value = _module().parse_money(str(value))
    except ValueError as exc:
        raise DbError(str(exc)) from exc
    with _connect() as conn:
        with conn.cursor() as cur:
            _require_offline(cur, character_id, force)
            cur.execute(f"UPDATE public.character SET {field} = %s WHERE id = %s", (value, character_id))
            if cur.rowcount != 1:
                raise DbError(f"角色 {character_id} 不存在")
        conn.commit()


def teleport_character(character_id: int, region_id: int, map_group: int, map_id: int,
                       x: int, y: int, z: int, force: bool) -> None:
    for label_, value, limit in (("region_id", region_id, 255), ("map_group", map_group, 255),
                                 ("map_id", map_id, 255), ("x", x, 32767), ("y", y, 32767),
                                 ("z", z, 127)):
        if not 0 <= value <= limit and label_ != "z":
            raise DbError(f"{label_} 超出范围")
        if label_ == "z" and not -128 <= value <= 127:
            raise DbError("z 超出范围")
    with _connect() as conn:
        with conn.cursor() as cur:
            _require_offline(cur, character_id, force)
            cur.execute(
                "UPDATE public.character SET region_id = %s, map_header_id_or_gba_map_group_id = %s, "
                "gba_map_id = %s, x = %s, y = %s, z = %s WHERE id = %s",
                (region_id, map_group, map_id, x, y, z, character_id),
            )
            if cur.rowcount != 1:
                raise DbError(f"角色 {character_id} 不存在")
        conn.commit()


# -- 背包 / 仓库 ---------------------------------------------------------------


def list_items(character_id: int) -> list[dict]:
    with _connect() as conn:
        rows = conn.execute(
            """
            SELECT o.item_id, o.owner_id, o.item_index_id, o.item_amount, o.inventory_id,
                   o.color_id, o.item_region_index_id
            FROM public.owned_item o
            WHERE o.owner_id = %s
            ORDER BY o.inventory_id, o.item_index_id
            """,
            (character_id,),
        ).fetchall()
    return [
        {"item_id": r[0], "owner_id": r[1], "item_index_id": r[2], "amount": r[3],
         "inventory_id": r[4], "color_id": r[5], "region_index_id": r[6]}
        for r in rows
    ]


def add_item(character_id: int, item_index_id: int, amount: int, inventory_id: int, force: bool) -> int:
    if not 1 <= item_index_id <= 30000:
        raise DbError("道具编号无效")
    if not 1 <= amount <= 999:
        raise DbError("数量必须在 1..999")
    if inventory_id not in (1, 2):
        raise DbError("容器只支持 1=背包 / 2=仓库")
    from . import item_index as dict_index

    if not dict_index.exists(item_index_id):
        raise DbError(f"道具编号 {item_index_id} 在 Item.jsonc 中不存在")
    with _connect() as conn:
        with conn.cursor() as cur:
            _require_offline(cur, character_id, force)
            row = cur.execute(
                "SELECT COALESCE(MAX(item_id), 0) FROM public.owned_item"
            ).fetchone()
            next_id = int(row[0]) + 1
            cur.execute(
                """
                INSERT INTO public.owned_item
                  (item_id, owner_id, item_index_id, item_amount, inventory_id, color_id, item_region_index_id)
                VALUES (%s, %s, %s, %s, %s, -1, -1)
                """,
                (next_id, character_id, item_index_id, amount, inventory_id),
            )
        conn.commit()
    return next_id


def update_item(character_id: int, item_id: int, amount: int, force: bool) -> None:
    if not 1 <= amount <= 999:
        raise DbError("数量必须在 1..999")
    with _connect() as conn:
        with conn.cursor() as cur:
            _require_offline(cur, character_id, force)
            cur.execute(
                "UPDATE public.owned_item SET item_amount = %s WHERE item_id = %s AND owner_id = %s",
                (amount, item_id, character_id),
            )
            if cur.rowcount != 1:
                raise DbError("道具记录不存在或不属于该角色")
        conn.commit()


def delete_item(character_id: int, item_id: int, force: bool) -> None:
    with _connect() as conn:
        with conn.cursor() as cur:
            _require_offline(cur, character_id, force)
            cur.execute(
                "DELETE FROM public.owned_item WHERE item_id = %s AND owner_id = %s",
                (item_id, character_id),
            )
            if cur.rowcount != 1:
                raise DbError("道具记录不存在或不属于该角色")
        conn.commit()


# -- 宝可梦 ---------------------------------------------------------------------

POKEMON_EDITABLE_SCALARS = {
    "level_value": (1, 100), "current_hp": (0, 9999), "exp": (0, 2_000_000_000),
    "friend_value": (0, 255), "pp_up_times": (0, 100), "ability": (0, 300),
    "item": (-1, 30000), "ball_type": (0, 30), "form_type": (0, 100),
}
POKEMON_EDITABLE_BOOLS = {"has_hidden_ability", "is_shiny", "is_alpha"}


def list_pokemon(character_id: int) -> list[dict]:
    with _connect() as conn:
        rows = conn.execute(
            """
            SELECT p.id, p.container_id, ct.name, p.container_position, p.dex_id, p.name,
                   p.level_value, p.current_hp, p.exp, p.moves, p.moves_pp, p.iv_values,
                   p.ev_values, p.ability, p.has_hidden_ability, p.is_shiny, p.is_alpha,
                   p.item, p.friend_value, p.pp_up_times, p.ball_type, p.form_type,
                   p.ot_name, p.status
            FROM public.pokemon p
            LEFT JOIN public.container ct ON ct.id = p.container_id
            WHERE p.trainer_id = %s
            ORDER BY p.container_id, p.container_position
            """,
            (character_id,),
        ).fetchall()
    result = []
    for r in rows:
        result.append({
            "id": r[0], "container_id": r[1], "container_name": r[2], "position": r[3],
            "dex_id": r[4], "nickname": r[5], "level": r[6], "current_hp": r[7], "exp": r[8],
            "moves": list(r[9] or []), "moves_pp": list(r[10] or []), "ivs": list(r[11] or []),
            "evs": list(r[12] or []), "ability": r[13], "hidden_ability": r[14],
            "shiny": r[15], "alpha": r[16], "held_item": r[17], "friendship": r[18],
            "pp_up": r[19], "ball_type": r[20], "form_type": r[21], "ot_name": r[22],
            "status": r[23],
        })
    return result


def update_pokemon(character_id: int, pokemon_id: int, changes: dict, force: bool) -> None:
    sets: list[str] = []
    params: list = []
    for field, (low, high) in POKEMON_EDITABLE_SCALARS.items():
        if field in changes:
            value = int(changes[field])
            if not low <= value <= high:
                raise DbError(f"{field} 超出范围 {low}..{high}")
            sets.append(f"{field} = %s")
            params.append(value)
    for field in POKEMON_EDITABLE_BOOLS:
        if field in changes:
            sets.append(f"{field} = %s")
            params.append(bool(changes[field]))
    for field, size in (("moves", 4), ("moves_pp", 4), ("iv_values", 6), ("ev_values", 6)):
        if field in changes:
            values = changes[field]
            if not isinstance(values, list) or len(values) != size:
                raise DbError(f"{field} 必须是长度 {size} 的数组")
            fixed = []
            for value in values:
                value = int(value)
                if field == "moves":
                    if not 0 <= value <= 10000:
                        raise DbError("招式编号无效")
                elif field == "iv_values":
                    if not 0 <= value <= 31:
                        raise DbError("个体值必须在 0..31")
                elif field == "ev_values":
                    if not 0 <= value <= 252:
                        raise DbError("努力值必须在 0..252")
                else:
                    if not 0 <= value <= 999:
                        raise DbError(f"{field} 数值无效")
                fixed.append(value)
            sets.append(f"{field} = %s")
            params.append(fixed)
    if "nickname" in changes:
        nick = str(changes["nickname"] or "").strip()
        if len(nick) > 32 or any(unicodedata.category(ch).startswith("C") for ch in nick):
            raise DbError("昵称非法")
        sets.append("name = %s")
        params.append(nick)
    if not sets:
        raise DbError("没有可更新的字段")
    params.extend([pokemon_id, character_id])
    with _connect() as conn:
        with conn.cursor() as cur:
            _require_offline(cur, character_id, force)
            cur.execute(
                f"UPDATE public.pokemon SET {', '.join(sets)} WHERE id = %s AND trainer_id = %s",
                params,
            )
            if cur.rowcount != 1:
                raise DbError("宝可梦不存在或不属于该角色")
        conn.commit()


def delete_pokemon(character_id: int, pokemon_id: int, force: bool) -> None:
    with _connect() as conn:
        with conn.cursor() as cur:
            _require_offline(cur, character_id, force)
            cur.execute(
                "DELETE FROM public.pokemon WHERE id = %s AND trainer_id = %s",
                (pokemon_id, character_id),
            )
            if cur.rowcount != 1:
                raise DbError("宝可梦不存在或不属于该角色")
        conn.commit()
