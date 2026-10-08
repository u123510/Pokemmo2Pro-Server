"""HTTP 服务器：静态文件白名单、鉴权中间件、路由分发。"""

from __future__ import annotations

import json
import sys
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import os
from pathlib import Path
from urllib.parse import parse_qs, urlparse

from . import auth
from . import handlers_content as content
from . import handlers_players as players
from . import handlers_world as world
from . import handlers_auth
from .handlers_auth import api_login, api_logout
from .handlers_world import map_image
from .http import (
    ApiError,
    SESSION_COOKIE,
    _body,
    _q,
    _require_csrf,
    _require_session,
    _session_token,
    login_status,
    send_json,
)

sys.path.insert(0, str(Path(__file__).resolve().parent))
STATIC_DIR = Path(__file__).resolve().parent.parent / "static"

STATIC_FILES = {
    "/": (STATIC_DIR / "index.html", "text/html; charset=utf-8"),
    "/index.html": (STATIC_DIR / "index.html", "text/html; charset=utf-8"),
    "/static/style.css": (STATIC_DIR / "style.css", "text/css; charset=utf-8"),
    "/static/js/app.js": (STATIC_DIR / "js" / "app.js", "text/javascript; charset=utf-8"),
    "/static/js/api.js": (STATIC_DIR / "js" / "api.js", "text/javascript; charset=utf-8"),
    "/static/js/mapview.js": (STATIC_DIR / "js" / "mapview.js", "text/javascript; charset=utf-8"),
    "/static/js/shops.js": (STATIC_DIR / "js" / "shops.js", "text/javascript; charset=utf-8"),
    "/static/js/npcs.js": (STATIC_DIR / "js" / "npcs.js", "text/javascript; charset=utf-8"),
    "/static/js/accounts.js": (STATIC_DIR / "js" / "accounts.js", "text/javascript; charset=utf-8"),
    "/static/js/players.js": (STATIC_DIR / "js" / "players.js", "text/javascript; charset=utf-8"),
    "/static/js/itemsdict.js": (STATIC_DIR / "js" / "itemsdict.js", "text/javascript; charset=utf-8"),
    "/static/js/gifts.js": (STATIC_DIR / "js" / "gifts.js", "text/javascript; charset=utf-8"),
    "/static/js/moves.js": (STATIC_DIR / "js" / "moves.js", "text/javascript; charset=utf-8"),
    "/static/js/dex.js": (STATIC_DIR / "js" / "dex.js", "text/javascript; charset=utf-8"),
    "/static/js/encounters.js": (STATIC_DIR / "js" / "encounters.js", "text/javascript; charset=utf-8"),
    "/static/js/events.js": (STATIC_DIR / "js" / "events.js", "text/javascript; charset=utf-8"),
}

GET_ROUTES = {
    **handlers_auth.GET_ROUTES,
    **content.GET_ROUTES,
    **players.GET_ROUTES,
    **world.GET_ROUTES,
}
POST_ROUTES = {
    **content.POST_ROUTES,
    **players.POST_ROUTES,
    **world.POST_ROUTES,
}


class AdminHandler(BaseHTTPRequestHandler):
    server_version = "OpenMMO Admin/1.0"

    def log_message(self, format: str, *args: object) -> None:
        print(f"[admin] {self.address_string()} {format % args}")

    def _static(self, entry) -> None:
        path, content_type = entry
        data = path.read_bytes()
        self.send_response(HTTPStatus.OK)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self) -> None:  # noqa: N802
        parsed = urlparse(self.path)
        try:
            static_entry = STATIC_FILES.get(parsed.path)
            if static_entry is not None:
                self._static(static_entry)
                return
            if parsed.path == "/api/login-status":
                send_json(self, login_status(self))
                return
            if parsed.path == "/api/map/image":
                params = parse_qs(parsed.query)
                path, mode = map_image(params)
                self.send_response(HTTPStatus.OK)
                self.send_header("Content-Type", "image/png")
                self.send_header("Content-Length", str(path.stat().st_size))
                self.send_header("X-Map-Mode", mode)
                self.send_header("Cache-Control", "max-age=86400")
                self.end_headers()
                with path.open("rb") as image:
                    self.wfile.write(image.read())
                return
            route = GET_ROUTES.get(parsed.path)
            if route is None:
                send_json(self, {"error": "未知路径"}, HTTPStatus.NOT_FOUND)
                return
            _require_session(self)
            send_json(self, route(_q(self), self))
        except ApiError as exc:
            send_json(self, {"error": str(exc)}, exc.status)
        except ValueError as exc:
            send_json(self, {"error": str(exc)}, HTTPStatus.BAD_REQUEST)
        except Exception as exc:  # noqa: BLE001
            import traceback
            traceback.print_exc()
            send_json(self, {"error": f"服务器内部错误: {exc}"}, HTTPStatus.INTERNAL_SERVER_ERROR)

    def do_POST(self) -> None:  # noqa: N802
        parsed = urlparse(self.path)
        try:
            if parsed.path == "/api/login":
                api_login(_body(self), self)
                return
            if parsed.path == "/api/logout":
                api_logout(_body(self), self)
                return
            route = POST_ROUTES.get(parsed.path)
            if route is None:
                raise ApiError("未知路径", HTTPStatus.NOT_FOUND)
            _require_csrf(self)
            send_json(self, route(_body(self)))
        except ApiError as exc:
            send_json(self, {"error": str(exc)}, exc.status)
        except ValueError as exc:
            send_json(self, {"error": str(exc)}, HTTPStatus.BAD_REQUEST)
        except Exception as exc:  # noqa: BLE001
            import traceback
            traceback.print_exc()
            send_json(self, {"error": f"服务器内部错误: {exc}"}, HTTPStatus.INTERNAL_SERVER_ERROR)


def serve() -> None:
    host = "127.0.0.1"
    port = int(os.getenv("NPC_ADMIN_PORT", "8081"))
    server = ThreadingHTTPServer((host, port), AdminHandler)
    print(f"OpenMMO 后台管理系统: http://{host}:{port}/")
    print("默认仅监听本机；口令见上方输出或 tools/web/config.json。按 Ctrl+C 停止。")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("正在停止后台系统。")
    finally:
        server.server_close()
