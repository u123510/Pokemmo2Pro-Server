"""Database operations used by the local OpenMMO account admin page."""

from __future__ import annotations

import hashlib
import os
from pathlib import Path
import re
import unicodedata

import psycopg


PROJECT_ROOT = Path(__file__).resolve().parents[1]
SEQUENCE_LOCK_KEY = 28887
_DATABASE_SETTINGS: dict[str, str] | None = None


def java_database_settings() -> dict[str, str]:
    pattern = re.compile(
        r'''new\s+Database\(\s*"jdbc:postgresql://(?P<host>[^:/"]+)(?::(?P<port>\d+))?/(?P<dbname>[^"]+)"\s*,\s*"(?P<user>[^"]+)"\s*,\s*"(?P<password>(?:\\.|[^"])*)"\s*\)''',
        re.DOTALL,
    )
    source_paths = (
        PROJECT_ROOT / "server.login/src/main/java/org/pokemmo/loginserver/Main.java",
        PROJECT_ROOT / "server.game/src/main/java/org/pokemmo/gameserver/Main.java",
    )
    for source_path in source_paths:
        try:
            source = source_path.read_text(encoding="utf-8")
        except OSError:
            continue
        match = pattern.search(source)
        if match:
            return {
                "host": match.group("host"),
                "port": match.group("port") or "5432",
                "dbname": match.group("dbname"),
                "user": match.group("user"),
                "password": match.group("password").replace('\\"', '"').replace('\\\\', '\\'),
            }
    return {}


def database_settings() -> dict[str, str]:
    global _DATABASE_SETTINGS
    if _DATABASE_SETTINGS is None:
        java_settings = java_database_settings()
        _DATABASE_SETTINGS = {
            "host": os.getenv("DB_HOST", java_settings.get("host", "127.0.0.1")),
            "port": os.getenv("DB_PORT", java_settings.get("port", "5432")),
            "dbname": os.getenv("DB_NAME", java_settings.get("dbname", "mmo_db")),
            "user": os.getenv("DB_USER", java_settings.get("user", "openmmo")),
            "password": os.getenv("DB_PASSWORD", java_settings.get("password", "")),
        }
    if not _DATABASE_SETTINGS["password"]:
        raise RuntimeError(
            "未找到数据库密码：请检查 Java Main.java 中的 Database 配置，"
            "或设置 DB_PASSWORD 环境变量"
        )
    return _DATABASE_SETTINGS


def database_connect() -> psycopg.Connection:
    settings = database_settings()
    return psycopg.connect(
        host=settings["host"],
        port=int(settings["port"]),
        dbname=settings["dbname"],
        user=settings["user"],
        password=settings["password"],
        connect_timeout=10,
    )


def database_label() -> str:
    settings = database_settings()
    return f"{settings['host']}:{settings['port']} / {settings['dbname']}"


def normalize_name(value: str, field_name: str, max_length: int) -> str:
    value = unicodedata.normalize("NFC", value.strip())
    if not value:
        raise ValueError(f"{field_name}不能为空")
    if len(value) > max_length:
        raise ValueError(f"{field_name}不能超过 {max_length} 个字符")
    if any(character.isspace() or unicodedata.category(character).startswith("C")
           for character in value):
        raise ValueError(f"{field_name}不能包含空白或控制字符")
    return value


def normalize_password(value: str) -> str:
    if not value:
        raise ValueError("密码不能为空")
    if len(value) > 128:
        raise ValueError("密码不能超过 128 个字符")
    if any(unicodedata.category(character).startswith("C") for character in value):
        raise ValueError("密码不能包含控制字符")
    return value


def parse_positive_id(value: str, field_name: str) -> int:
    try:
        parsed = int(value)
    except ValueError as exc:
        raise ValueError(f"{field_name}无效") from exc
    if parsed <= 0:
        raise ValueError(f"{field_name}必须为正数")
    return parsed


def parse_sex(value: str) -> int:
    try:
        sex = int(value)
    except ValueError as exc:
        raise ValueError("性别参数无效") from exc
    if sex not in (0, 1):
        raise ValueError("性别只能选择 0 或 1")
    return sex


def parse_money(value: str) -> int:
    try:
        money = int(value)
    except ValueError as exc:
        raise ValueError("金钱必须是整数") from exc
    if money < 0 or money > 2_147_483_647:
        raise ValueError("金钱范围必须是 0 到 2147483647")
    return money


def _lock(cursor) -> None:
    cursor.execute("SELECT pg_advisory_xact_lock(%s)", (SEQUENCE_LOCK_KEY,))


def _repair_sequence(cursor, table: str, column: str) -> None:
    cursor.execute(
        f"""
        SELECT setval(
            pg_get_serial_sequence('public.{table}', '{column}'),
            COALESCE((SELECT MAX({column}) FROM public.{table}), 0) + 1,
            false
        )
        """
    )


def _ensure_character_name_available(cursor, character_name: str) -> None:
    cursor.execute(
        """
        SELECT 1 FROM public.character
        WHERE lower(name) = lower(%s)
        LIMIT 1
        """,
        (character_name,),
    )
    if cursor.fetchone() is not None:
        raise ValueError(f"角色 {character_name} 已存在")


def _create_character(cursor, account_id: int, character_name: str, sex: int) -> int:
    cursor.execute(
        'SELECT 1 FROM public."account" WHERE account_id = %s',
        (account_id,),
    )
    if cursor.fetchone() is None:
        raise ValueError(f"账号 ID {account_id} 不存在")
    _ensure_character_name_available(cursor, character_name)
    _repair_sequence(cursor, "character", "id")
    cursor.execute(
        """
        INSERT INTO public.character (
            account_id, name, union_name, sex,
            money, coins, battle_points,
            pc_box_expansion_number, battle_box_expansion_number,
            region_id, map_header_id_or_gba_map_group_id, gba_map_id,
            x, y, z, toward, transportation,
            permission, model_region_index_id, model_index_id,
            forehead, forehead_color, hat, hat_color, hair, hair_color,
            eyes, eyes_color, facial_hair, facial_hair_color,
            back, back_color, top, top_color, gloves, gloves_color,
            footwear, footwear_color, leggings, leggings_color
        )
        VALUES (
            %s, %s, '', %s,
            0, 0, 0,
            2, 1,
            0, 4, 1,
            6, 6, 0, 0, 2,
            3, 0, 0,
            0, 0, 0, 0, 0, 0,
            0, 0, 0, 0,
            0, 0, 0, 0, 0, 0,
            0, 0, 0, 0
        )
        RETURNING id
        """,
        (account_id, character_name, sex),
    )
    return cursor.fetchone()[0]


def create_account_and_character(
    account_name: str,
    password: str,
    character_name: str,
    sex: int,
) -> tuple[int, int]:
    password_hash = hashlib.sha1(password.encode("utf-8")).digest()
    with database_connect() as connection:
        with connection.cursor() as cursor:
            _lock(cursor)
            cursor.execute(
                'SELECT 1 FROM public."account" WHERE lower(account_name) = lower(%s)',
                (account_name,),
            )
            if cursor.fetchone() is not None:
                raise ValueError(f"账号 {account_name} 已存在")
            _repair_sequence(cursor, "account", "account_id")
            cursor.execute(
                """
                INSERT INTO public."account" (account_name, password, login_permission)
                VALUES (%s, %s, 3)
                RETURNING account_id
                """,
                (account_name, password_hash),
            )
            account_id = cursor.fetchone()[0]
            character_id = _create_character(cursor, account_id, character_name, sex)
        connection.commit()
    return account_id, character_id


def create_character(account_id: int, character_name: str, sex: int) -> int:
    with database_connect() as connection:
        with connection.cursor() as cursor:
            _lock(cursor)
            character_id = _create_character(cursor, account_id, character_name, sex)
        connection.commit()
    return character_id


def list_accounts() -> list[dict[str, object]]:
    with database_connect() as connection:
        with connection.cursor() as cursor:
            cursor.execute(
                """
                SELECT a.account_id, a.account_name, a.login_permission, a.created_at,
                       c.id, c.name, c.sex, c.money,
                       c.region_id, c.map_header_id_or_gba_map_group_id,
                       c.gba_map_id, c.x, c.y, c.z
                FROM public."account" a
                LEFT JOIN public.character c ON c.account_id = a.account_id
                ORDER BY a.account_id, c.id
                """
            )
            rows = cursor.fetchall()
    accounts: dict[int, dict[str, object]] = {}
    for row in rows:
        account_id = row[0]
        account = accounts.setdefault(account_id, {
            "account_id": account_id,
            "account_name": row[1],
            "login_permission": row[2],
            "created_at": row[3],
            "characters": [],
        })
        if row[4] is not None:
            account["characters"].append({
                "id": row[4], "name": row[5], "sex": row[6], "money": row[7],
                "region_id": row[8], "map_group": row[9], "map_id": row[10],
                "x": row[11], "y": row[12], "z": row[13],
            })
    return list(accounts.values())


def set_character_money(character_id: int, money: int) -> None:
    with database_connect() as connection:
        with connection.cursor() as cursor:
            cursor.execute(
                "UPDATE public.character SET money = %s WHERE id = %s",
                (money, character_id),
            )
            if cursor.rowcount != 1:
                raise ValueError(f"角色 ID {character_id} 不存在")
        connection.commit()


def _delete_character_assets(cursor, character_ids: list[int]) -> None:
    if not character_ids:
        return
    cursor.execute(
        "DELETE FROM public.gtl_trade_history "
        "WHERE buyer_id = ANY(%s) OR seller_id = ANY(%s)",
        (character_ids, character_ids),
    )
    cursor.execute(
        "DELETE FROM public.pokemon WHERE trainer_id = ANY(%s)",
        (character_ids,),
    )
    cursor.execute(
        "DELETE FROM public.owned_item WHERE owner_id = ANY(%s)",
        (character_ids,),
    )


def delete_character(character_id: int) -> None:
    with database_connect() as connection:
        with connection.cursor() as cursor:
            _lock(cursor)
            cursor.execute(
                "SELECT account_id FROM public.character WHERE id = %s FOR UPDATE",
                (character_id,),
            )
            if cursor.fetchone() is None:
                raise ValueError(f"角色 ID {character_id} 不存在")
            cursor.execute(
                "UPDATE public.account_context SET character_id = NULL WHERE character_id = %s",
                (character_id,),
            )
            _delete_character_assets(cursor, [character_id])
            cursor.execute("DELETE FROM public.character WHERE id = %s", (character_id,))
        connection.commit()


def delete_account(account_id: int) -> None:
    with database_connect() as connection:
        with connection.cursor() as cursor:
            _lock(cursor)
            cursor.execute(
                'SELECT 1 FROM public."account" WHERE account_id = %s FOR UPDATE',
                (account_id,),
            )
            if cursor.fetchone() is None:
                raise ValueError(f"账号 ID {account_id} 不存在")
            cursor.execute(
                "SELECT id FROM public.character WHERE account_id = %s FOR UPDATE",
                (account_id,),
            )
            character_ids = [row[0] for row in cursor.fetchall()]
            _delete_character_assets(cursor, character_ids)
            cursor.execute("DELETE FROM public.character WHERE account_id = %s", (account_id,))
            cursor.execute('DELETE FROM public."account" WHERE account_id = %s', (account_id,))
        connection.commit()
