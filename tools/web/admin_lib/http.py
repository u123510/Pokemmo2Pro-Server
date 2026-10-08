"""HTTP 请求工具：会话/CSRF 校验、请求体解析、查询参数、审计。"""

from __future__ import annotations

import json
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler
from urllib.parse import parse_qs, urlparse

from . import audit, auth

SESSION_COOKIE = "admin_session"
MAX_BODY_BYTES = 4 * 1024 * 1024


class ApiError(Exception):
    def __init__(self, message: str, status: HTTPStatus = HTTPStatus.BAD_REQUEST) -> None:
        super().__init__(message)
        self.status = status


def _session_token(handler: BaseHTTPRequestHandler) -> str | None:
    cookie = handler.headers.get("Cookie", "")
    for part in cookie.split(";"):
        key, _, value = part.strip().partition("=")
        if key == SESSION_COOKIE and value:
            return value
    return None


def _require_session(handler: BaseHTTPRequestHandler) -> str:
    token = _session_token(handler)
    if not auth.validate(token):
        raise ApiError("未登录或会话已过期", HTTPStatus.UNAUTHORIZED)
    return token  # type: ignore[return-value]


def _require_csrf(handler: BaseHTTPRequestHandler) -> str:
    token = _require_session(handler)
    if not auth.check_csrf(token, handler.headers.get("X-CSRF")):
        raise ApiError("CSRF 校验失败，请刷新页面", HTTPStatus.FORBIDDEN)
    return token


def _body(handler: BaseHTTPRequestHandler) -> dict:
    length = int(handler.headers.get("Content-Length", "0"))
    if length > MAX_BODY_BYTES:
        raise ApiError("请求体过大", HTTPStatus.REQUEST_ENTITY_TOO_LARGE)
    raw = handler.rfile.read(length) if length else b"{}"
    try:
        data = json.loads(raw.decode("utf-8"))
    except ValueError as exc:
        raise ApiError(f"请求体不是合法 JSON: {exc}") from exc
    if not isinstance(data, dict):
        raise ApiError("请求体必须是 JSON 对象")
    return data


def _q(handler: BaseHTTPRequestHandler) -> dict:
    return parse_qs(urlparse(handler.path).query)


def _one(params: dict, key: str, default: str = "") -> str:
    values = params.get(key)
    return values[0] if values and values[0] else default


def _int(params: dict, key: str, default: int | None = None) -> int:
    raw = _one(params, key, "")
    if raw == "":
        if default is None:
            raise ApiError(f"缺少参数 {key}")
        return default
    try:
        return int(raw)
    except ValueError as exc:
        raise ApiError(f"参数 {key} 必须是整数") from exc


def _audit(action: str, body: dict | None = None) -> None:
    safe = {k: v for k, v in (body or {}).items() if k not in ("password", "newPassword")}
    audit.record(action, safe)




def send_json(handler: BaseHTTPRequestHandler, payload: dict, status: int = HTTPStatus.OK) -> None:
    data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    handler.send_response(status)
    handler.send_header("Content-Type", "application/json; charset=utf-8")
    handler.send_header("Content-Length", str(len(data)))
    handler.send_header("Cache-Control", "no-store")
    handler.end_headers()
    handler.wfile.write(data)


def login_status(handler: BaseHTTPRequestHandler) -> dict:
    token = _session_token(handler)
    return {"logged_in": auth.validate(token)}
