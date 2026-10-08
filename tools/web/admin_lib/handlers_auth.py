"""登录/登出/口令路由。"""

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
from . import auth


def api_login(body: dict, handler: BaseHTTPRequestHandler) -> dict:
    try:
        token = auth.login(str(body.get("password", "")))
    except auth.AuthError as exc:
        _audit("login", {"ok": False, "reason": str(exc)})
        raise ApiError(str(exc), HTTPStatus.UNAUTHORIZED) from exc
    csrf = auth.csrf_token(token)
    cookie = f"{SESSION_COOKIE}={token}; HttpOnly; SameSite=Strict; Path=/; Max-Age={12*3600}"
    handler.send_response(HTTPStatus.OK)
    handler.send_header("Content-Type", "application/json; charset=utf-8")
    handler.send_header("Set-Cookie", cookie)
    handler.send_header("Content-Length", "2")
    handler.end_headers()
    handler.wfile.write(b"{}")
    _audit("login", {"ok": True})
    return {}  # 不走通用 JSON 发送（需要 Set-Cookie）


def api_me(params: dict, handler: BaseHTTPRequestHandler) -> dict:
    token = _session_token(handler)
    if not auth.validate(token):
        raise ApiError("未登录", HTTPStatus.UNAUTHORIZED)
    return {"csrf": auth.csrf_token(token)}


def api_logout(body: dict, handler: BaseHTTPRequestHandler) -> dict:
    token = _session_token(handler)
    if token:
        auth.logout(token)
    handler.send_response(HTTPStatus.OK)
    handler.send_header("Set-Cookie", f"{SESSION_COOKIE}=; HttpOnly; SameSite=Strict; Path=/; Max-Age=0")
    handler.send_header("Content-Length", "2")
    handler.end_headers()
    handler.wfile.write(b"{}")
    return {}


def api_change_password(body: dict) -> dict:
    _require_csrf(handler)
    try:
        auth.set_password(str(body.get("newPassword", "")))
    except auth.AuthError as exc:
        raise ApiError(str(exc)) from exc
    _audit("change_admin_password", {})
    return {"ok": True, "message": "后台口令已修改（会话保持）"}



GET_ROUTES = {
    "/api/me": lambda params, handler: api_me(params, handler),
}

POST_ROUTES = {
    "/api/password": api_change_password,
}
