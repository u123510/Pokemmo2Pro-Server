"""OpenMMO 后台管理系统入口（薄启动层，路由见 admin_lib/handlers_*.py）。

模块：账号/角色/背包/宝可梦（数据库）+ 商店/自定义NPC/地图/道具字典/商城礼包/
招式/图鉴/遇敌表/事件NPC（资源文件）。

安全：登录鉴权（PBKDF2）、会话 Cookie（HttpOnly/SameSite=Strict）、CSRF、
登录限速、审计日志、仅监听 127.0.0.1、静态白名单、SQL 参数化。

启动（项目根目录）：
    python tools/web/main.py                # http://127.0.0.1:8081/
    NPC_ADMIN_PORT=9000 ADMIN_PASSWORD=xxx python tools/web/main.py
"""

from __future__ import annotations

import os
import sys
from pathlib import Path

WEB_ROOT = Path(__file__).resolve().parent
sys.path.insert(0, str(WEB_ROOT))

from admin_lib import auth  # noqa: E402
from admin_lib import shop_repo  # noqa: E402
from admin_lib.server import serve  # noqa: E402


def main() -> None:
    if not shop_repo.SHOP_DIR.is_dir():
        print(f"错误: 未找到资源目录 {shop_repo.SHOP_DIR}，请从项目根目录运行。")
        sys.exit(1)
    auth._load_config()  # 首次运行生成并打印口令
    serve()


if __name__ == "__main__":
    main()
