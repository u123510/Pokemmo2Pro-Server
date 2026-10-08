"""后台登录鉴权：口令配置、会话 Cookie、CSRF、登录限速。

- 口令哈希（PBKDF2-SHA256）存放在 tools/web/config.json；首次运行自动生成随机口令并打印。
- 会话为内存态（重启失效），Cookie HttpOnly + SameSite=Strict；写操作需 X-CSRF 头
  等于会话 token（Cookie 不可被 JS 读取，天然防 CSRF）。
- 连续失败 5 次锁定 5 分钟。
"""

from __future__ import annotations

import hashlib
import hmac
import json
import secrets
import sys
import time
from pathlib import Path

WEB_ROOT = Path(__file__).resolve().parent.parent
CONFIG_FILE = WEB_ROOT / "config.json"

SESSION_TTL_SECONDS = 12 * 3600
MAX_FAILED_LOGINS = 5
LOCKOUT_SECONDS = 300

_sessions: dict[str, float] = {}  # token -> 过期时间戳
_failed: list[float] = []
_config: dict | None = None


class AuthError(Exception):
    """鉴权失败。"""


def _load_config() -> dict:
    global _config
    if _config is not None:
        return _config
    if CONFIG_FILE.exists():
        try:
            _config = json.loads(CONFIG_FILE.read_text(encoding="utf-8"))
        except ValueError:
            _config = None
    if not isinstance(_config, dict) or "password_hash" not in _config:
        import os

        password = os.getenv("ADMIN_PASSWORD") or secrets.token_urlsafe(9)
        _config = {"password_hash": _hash_password(password), "created_at": int(time.time())}
        CONFIG_FILE.write_text(json.dumps(_config, indent=2), encoding="utf-8")
        print("=" * 60)
        print(f"首次运行：后台管理口令已生成 -> {password}")
        print(f"（哈希已存入 {CONFIG_FILE.name}，请立即记下并妥善保存）")
        print("=" * 60)
        if not os.getenv("ADMIN_PASSWORD"):
            sys.stderr.write("提示：可用环境变量 ADMIN_PASSWORD 自设口令后删除 config.json 重新生成。\n")
    return _config


def set_password(password: str) -> None:
    if len(password) < 6:
        raise AuthError("口令至少 6 位")
    config = _load_config()
    config["password_hash"] = _hash_password(password)
    CONFIG_FILE.write_text(json.dumps(config, indent=2), encoding="utf-8")


def _hash_password(password: str) -> str:
    salt = secrets.token_hex(16)
    digest = hashlib.pbkdf2_hmac("sha256", password.encode("utf-8"), salt.encode("ascii"), 200_000)
    return f"pbkdf2${salt}${digest.hex()}"


def verify_password(password: str) -> bool:
    stored = _load_config().get("password_hash", "")
    parts = stored.split("$")
    if len(parts) != 3 or parts[0] != "pbkdf2":
        return False
    digest = hashlib.pbkdf2_hmac("sha256", password.encode("utf-8"), parts[1].encode("ascii"), 200_000)
    return hmac.compare_digest(digest.hex(), parts[2])


def login(password: str) -> str:
    """校验口令，返回新会话 token；失败抛 AuthError。"""
    now = time.time()
    while _failed and now - _failed[0] > LOCKOUT_SECONDS:
        _failed.pop(0)
    if len(_failed) >= MAX_FAILED_LOGINS:
        wait = int(LOCKOUT_SECONDS - (now - _failed[0])) + 1
        raise AuthError(f"失败次数过多，请 {wait} 秒后再试")
    if not isinstance(password, str) or not verify_password(password):
        _failed.append(now)
        raise AuthError("口令错误")
    _failed.clear()
    token = secrets.token_urlsafe(32)
    _sessions[token] = now + SESSION_TTL_SECONDS
    return token


def logout(token: str) -> None:
    _sessions.pop(token, None)


def validate(token: str | None) -> bool:
    if not token:
        return False
    expiry = _sessions.get(token)
    if expiry is None:
        return False
    if time.time() > expiry:
        _sessions.pop(token, None)
        return False
    _sessions[token] = time.time() + SESSION_TTL_SECONDS
    return True


def csrf_token(token: str) -> str:
    """会话的 CSRF 令牌：由会话 token 派生，泄露 cookie 之外不可得。"""
    return hmac.new(token.encode("ascii"), b"csrf", hashlib.sha256).hexdigest()[:32]


def check_csrf(token: str | None, header_value: str | None) -> bool:
    return bool(token) and isinstance(header_value, str) and hmac.compare_digest(csrf_token(token), header_value)
