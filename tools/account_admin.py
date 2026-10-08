"""Local OpenMMO account and character administration page."""

from __future__ import annotations

import html
import os
import secrets
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs

import psycopg
from psycopg import errors

from account_admin_db import (
    create_account_and_character,
    create_character,
    database_label,
    database_settings,
    delete_account,
    delete_character,
    list_accounts,
    normalize_name,
    normalize_password,
    parse_money,
    parse_positive_id,
    parse_sex,
    set_character_money,
)


MAX_FORM_BYTES = 16 * 1024
CSRF_TOKEN = secrets.token_urlsafe(32)


def esc(value: object) -> str:
    return html.escape(str(value), quote=True)


def page(message: str = "", error: bool = False, load_data: bool = True) -> bytes:
    message_block = ""
    if message:
        message_block = f'<p class="notice {"error" if error else "success"}">{esc(message)}</p>'
    accounts = list_accounts() if load_data else []
    account_options = "".join(
        f'<option value="{esc(account["account_id"])}">'
        f'{esc(account["account_name"])} (ID {esc(account["account_id"])})</option>'
        for account in accounts
    )
    account_rows = []
    for account in accounts:
        characters = account["characters"]
        if not characters:
            character_rows = '<tr><td colspan="6" class="muted">暂无角色</td></tr>'
        else:
            character_rows = "".join(
                f"""
                <tr>
                  <td>{esc(character['id'])}</td>
                  <td>{esc(character['name'])}</td>
                  <td>{esc(character['sex'])}</td>
                  <td>
                    <form class="inline" method="post">
                      <input type="hidden" name="csrf" value="{esc(CSRF_TOKEN)}">
                      <input type="hidden" name="action" value="set_money">
                      <input type="hidden" name="character_id" value="{esc(character['id'])}">
                      <input class="money" name="money" type="number" min="0" max="2147483647" value="{esc(character['money'])}" required>
                      <button type="submit">保存</button>
                    </form>
                  </td>
                  <td>{esc(character['region_id'])}/{esc(character['map_group'])}/{esc(character['map_id'])} ({esc(character['x'])}, {esc(character['y'])}, {esc(character['z'])})</td>
                  <td>
                    <form method="post" onsubmit="return confirm('确定删除这个角色及其宝可梦、道具和关联数据吗？');">
                      <input type="hidden" name="csrf" value="{esc(CSRF_TOKEN)}">
                      <input type="hidden" name="action" value="delete_character">
                      <input type="hidden" name="character_id" value="{esc(character['id'])}">
                      <button class="danger" type="submit">删除角色</button>
                    </form>
                  </td>
                </tr>
                """
                for character in characters
            )
        account_rows.append(
            f"""
            <section class="account">
              <div class="account-head">
                <div><strong>{esc(account['account_name'])}</strong> <span class="muted">账号 ID {esc(account['account_id'])} · 权限 {esc(account['login_permission'])}</span></div>
                <form method="post" onsubmit="return confirm('确定删除这个账号及其全部角色、宝可梦、道具和关联数据吗？');">
                  <input type="hidden" name="csrf" value="{esc(CSRF_TOKEN)}">
                  <input type="hidden" name="action" value="delete_account">
                  <input type="hidden" name="account_id" value="{esc(account['account_id'])}">
                  <button class="danger" type="submit">删除账号</button>
                </form>
              </div>
              <div class="table-wrap"><table>
                <thead><tr><th>角色 ID</th><th>角色名</th><th>性别</th><th>金钱</th><th>出生位置</th><th>操作</th></tr></thead>
                <tbody>{character_rows}</tbody>
              </table></div>
            </section>
            """
        )
    document = f"""<!doctype html>
<html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>OpenMMO 账号管理</title>
<style>
:root {{ font-family: system-ui, -apple-system, "Segoe UI", sans-serif; color: #17202a; background: #eef2f6; }}
body {{ margin: 0; }} main {{ width: min(1180px, calc(100% - 32px)); margin: 28px auto 56px; }}
section, .toolbar {{ background: #fff; border: 1px solid #d8e0e8; border-radius: 8px; padding: 20px; margin-bottom: 16px; box-shadow: 0 6px 20px #17202a0d; }}
h1 {{ margin: 0 0 5px; font-size: 1.45rem; }} h2 {{ margin: 0 0 14px; font-size: 1.05rem; }}
.muted {{ color: #657586; font-size: .9rem; }} .notice {{ padding: 10px 12px; border-radius: 5px; }}
.success {{ background: #e8f7ed; color: #145c2b; }} .error {{ background: #fff0f0; color: #9c2020; }}
.forms {{ display: grid; grid-template-columns: repeat(auto-fit, minmax(280px, 1fr)); gap: 18px; }}
label {{ display: block; margin: 10px 0 5px; font-weight: 600; }} input, select {{ box-sizing: border-box; width: 100%; padding: 9px 10px; border: 1px solid #aebdca; border-radius: 5px; font: inherit; }}
button {{ padding: 8px 11px; border: 0; border-radius: 5px; background: #1769aa; color: #fff; font: inherit; font-weight: 700; cursor: pointer; }} button:hover {{ background: #12578d; }}
.danger {{ background: #b52b34; }} .danger:hover {{ background: #8e2028; }} .inline {{ display: flex; gap: 6px; align-items: center; }}
.money {{ min-width: 130px; }} .account-head {{ display: flex; justify-content: space-between; gap: 12px; align-items: center; }}
.table-wrap {{ overflow-x: auto; }} table {{ width: 100%; border-collapse: collapse; min-width: 760px; }} th, td {{ border-top: 1px solid #e1e7ed; padding: 9px 7px; text-align: left; vertical-align: middle; }} th {{ color: #536273; font-size: .85rem; }}
@media (max-width: 650px) {{ main {{ width: min(100% - 20px, 1180px); }} section, .toolbar {{ padding: 15px; }} .account-head {{ align-items: flex-start; flex-direction: column; }} }}
</style></head><body><main>
<div class="toolbar"><h1>OpenMMO 账号管理</h1><div class="muted">数据库：{esc(database_label()) if load_data else '不可用'} · 本页面无登录密码，仅建议在本机使用</div>{message_block}</div>
<div class="forms">
<section><h2>新增账号和角色</h2><form method="post"><input type="hidden" name="csrf" value="{esc(CSRF_TOKEN)}"><input type="hidden" name="action" value="create_account">
<label>账号名</label><input name="account_name" maxlength="12" required><label>明文密码</label><input name="password" type="password" maxlength="128" required><label>角色名</label><input name="character_name" maxlength="32" required><label>性别</label><select name="sex"><option value="0">0</option><option value="1">1</option></select><br><br><button type="submit">创建账号</button></form></section>
<section><h2>给已有账号新增角色</h2><form method="post"><input type="hidden" name="csrf" value="{esc(CSRF_TOKEN)}"><input type="hidden" name="action" value="create_character"><label>账号</label><select name="account_id" required>{account_options}</select><label>角色名</label><input name="character_name" maxlength="32" required><label>性别</label><select name="sex"><option value="0">0</option><option value="1">1</option></select><br><br><button type="submit">创建角色</button></form></section>
</div><h2>账号和角色</h2>{''.join(account_rows) or '<section class="muted">暂无账号</section>'}
</main></body></html>"""
    return document.encode("utf-8")


class AccountAdminHandler(BaseHTTPRequestHandler):
    server_version = "OpenMMOAccountAdmin/2.0"

    def log_message(self, format: str, *args: object) -> None:
        print(f"[{self.log_date_time_string()}] {format % args}")

    def send_page(self, message: str = "", error: bool = False, status: int = 200, load_data: bool = True) -> None:
        body = page(message, error, load_data)
        self.send_response(status)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.send_header("Cache-Control", "no-store")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self) -> None:
        if self.path != "/":
            self.send_error(HTTPStatus.NOT_FOUND)
            return
        try:
            self.send_page()
        except (OSError, psycopg.Error, RuntimeError) as exc:
            print(f"数据库读取失败: {exc.__class__.__name__}: {exc}")
            self.send_page("数据库读取失败，请检查服务端日志", error=True, status=HTTPStatus.INTERNAL_SERVER_ERROR, load_data=False)

    def do_POST(self) -> None:
        if self.path != "/":
            self.send_error(HTTPStatus.NOT_FOUND)
            return
        try:
            content_length = int(self.headers.get("Content-Length", "0"))
            if content_length <= 0 or content_length > MAX_FORM_BYTES:
                raise ValueError("请求体大小无效")
            form = parse_qs(self.rfile.read(content_length).decode("utf-8"), strict_parsing=True)
            if form.get("csrf", [""])[0] != CSRF_TOKEN:
                raise ValueError("页面令牌已失效，请刷新后重试")
            action = form.get("action", [""])[0]
            if action == "create_account":
                account_name = normalize_name(form.get("account_name", [""])[0], "账号名", 12)
                password = normalize_password(form.get("password", [""])[0])
                character_name = normalize_name(form.get("character_name", [""])[0], "角色名", 32)
                account_id, character_id = create_account_and_character(account_name, password, character_name, parse_sex(form.get("sex", [""])[0]))
                self.send_page(f"创建成功：账号 ID {account_id}，角色 ID {character_id}")
                return
            if action == "create_character":
                account_id = parse_positive_id(form.get("account_id", [""])[0], "账号 ID")
                character_name = normalize_name(form.get("character_name", [""])[0], "角色名", 32)
                character_id = create_character(account_id, character_name, parse_sex(form.get("sex", [""])[0]))
                self.send_page(f"创建角色成功：角色 ID {character_id}")
                return
            if action == "set_money":
                character_id = parse_positive_id(form.get("character_id", [""])[0], "角色 ID")
                set_character_money(character_id, parse_money(form.get("money", [""])[0]))
                self.send_page(f"角色 ID {character_id} 的金钱已更新")
                return
            if action == "delete_character":
                character_id = parse_positive_id(form.get("character_id", [""])[0], "角色 ID")
                delete_character(character_id)
                self.send_page(f"角色 ID {character_id} 已删除")
                return
            if action == "delete_account":
                account_id = parse_positive_id(form.get("account_id", [""])[0], "账号 ID")
                delete_account(account_id)
                self.send_page("账号及其角色已删除")
                return
            raise ValueError("未知操作")
        except ValueError as exc:
            self.send_page(str(exc), error=True, status=HTTPStatus.BAD_REQUEST)
        except errors.UniqueViolation:
            self.send_page("账号名或角色名已存在", error=True, status=HTTPStatus.CONFLICT)
        except (OSError, psycopg.Error, RuntimeError) as exc:
            print(f"数据库操作失败: {exc.__class__.__name__}: {exc}")
            self.send_page("数据库操作失败，请检查服务端日志", error=True, status=HTTPStatus.INTERNAL_SERVER_ERROR, load_data=False)


def main() -> None:
    host = "127.0.0.1"
    port = int(os.getenv("WEB_PORT", "8080"))
    database_settings()
    server = ThreadingHTTPServer((host, port), AccountAdminHandler)
    print(f"OpenMMO 账号管理页面: http://{host}:{port}/")
    print("本页面不需要登录密码；按 Ctrl+C 停止。")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("正在停止账号管理页面。")
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
