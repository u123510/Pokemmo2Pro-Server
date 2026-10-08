# OpenMMO 后台管理系统

`tools/web/` 是统一的后台管理入口（`main.py`）：登录鉴权 + 玩家与账号运营 +
游戏内容配置 + 地图可视化操作。纯 Python 标准库实现（数据库驱动 psycopg 除外），
前端为原生 ES Module，无构建步骤。

## 启动（项目根目录）

```text
python tools/web/main.py
# 浏览器打开 http://127.0.0.1:8081/（NPC_ADMIN_PORT 可改端口）
```

**首次启动会自动生成随机管理口令并打印在控制台**，哈希存入 `tools/web/config.json`
（不入库）。也可用环境变量 `ADMIN_PASSWORD=你的口令` 首启自设，或登录后点右上角「改口令」。
忘记口令：删除 `config.json` 重启即可重新生成。

数据库连接沿用项目配置（自动读取 Java `Main.java` 里的 `Database(...)` 参数，
可用 `DB_HOST/DB_NAME/DB_USER/DB_PASSWORD` 覆盖）。

## 安全设计（防渗透）

- **登录鉴权**：PBKDF2-SHA256 口令哈希；会话 Cookie `HttpOnly + SameSite=Strict`；
  12 小时过期；登录失败 5 次锁定 5 分钟。
- **CSRF**：所有写操作要求 `X-CSRF` 头 = 会话派生令牌（Cookie 不可被 JS 读取）。
- **仅监听 127.0.0.1**。如需局域网访问，请自担风险改 `main()` 里的 host，
  并务必先设置强口令。
- **审计日志**：所有写操作（含登录成败）追加到 `tools/web/audit/audit.jsonl`，
  「操作日志」页可查。
- 静态文件白名单；SQL 全参数化；玩家数据更新带双重条件（id + 归属）。
- **在线保护**：在线角色的数据库修改默认拒绝（游戏服内存会覆盖），确要修改需
  在 UI 确认 force 提示。

## 模块

### 玩家与账号（数据库）

- **账号与角色**：搜索、封禁/解封（`login_permission` + `ban_reason`，封禁不踢在线会话）、
  重置密码、删号（级联清理角色/道具/宝可梦/GTL 历史）；角色改名、金钱/钻石、
  按地图选择传送（坐标直写）。
- **背包与宝可梦**：按角色 ID 加载；发放/改数量/删除道具（背包或仓库，编号经
  Item.jsonc 校验）；宝可梦逐只编辑昵称/等级/HP/经验/特性/携带物/亲密/闪光/
  IV/EV/招式/PP，可放生。

### 内容配置（资源文件，写前备份 + 原子替换）

- **商店**（`resource/shop`）与**自定义 NPC**（`resource/npc/custom`）：原有能力，
  见下文地图浏览的联动。
- **道具字典**（`Item.jsonc`）：名称/描述/iconId/地区/本地化字符串 ID 编辑、
  新建、删除（有引用拒绝）、**全库引用扫描**（商店/礼包/ItemUse）。
  注意：服务端运行时字典是 `Item.bin`，此处只影响显示层；改 itemIndexId 的
  事务式联动属二期。
- **商城与赠送**（`GiftShop.jsonc` / `Gift.jsonc`）：礼包条目与赠送宝可梦的
  行级增删改。
- **招式编辑**（`resource/move/gen3..gen9`）：威力/命中/PP/优先度 + 完整 JSON
  编辑（复杂效果字段）。
- **图鉴/捕捉**：`CaptureSpecies.jsonc` 捕捉率/初始亲密度行级编辑；
  `Pokemon.jsonc` 名称/经验类型/性别比/可获取等平铺字段。
- **遇敌表**：按地图加载 `encounterFields` + `wildEncounters`，增删遇敌组、
  按图鉴选择种类、调等级区间与遇敌率。
- **事件 NPC**：按 eventId（万圣节等 0..6 分类）分组统计，一键批量启用/停用。

### 地图浏览

真实贴图渲染（关都 425/425、丰缘 494/518，需桌面 pret 反编译工程）；
NPC/传送点/柜台/碰撞标记；点格子看信息；左键拖动绿框移动自定义 NPC
（校验后保存）；右键新建 NPC / 编辑 / 停用 / 绑定为店员。

## 结构

```text
main.py                薄入口：校验资源目录、初始化口令、启动服务
admin_lib/
  http.py              请求工具（会话/CSRF/请求体/查询参数/审计）
  server.py            HTTP 服务器（静态白名单、鉴权中间件、路由分发）
  handlers_auth.py     登录/登出/口令路由
  handlers_players.py  玩家域路由（账号/角色/背包/宝可梦）
  handlers_content.py  内容域路由（字典/礼包/招式/图鉴/遇敌/事件/审计）
  handlers_world.py    世界域路由（商店/自定义NPC/地图/图片渲染）
  auth.py / audit.py / db.py    登录鉴权、审计、数据库玩家域
  jsonc_io.py / paths.py / backups.py   JSONC 读写、路径白名单、备份
  item_index.py / map_index.py  只读索引
  shop_repo.py / custom_npc_repo.py / map_render.py
  item_repo.py / gift_repo.py / move_repo.py / dex_repo.py
  encounter_repo.py / event_repo.py
static/
  index.html / style.css
  js/{api,app,mapview,shops,npcs,accounts,players,itemsdict,gifts,moves,dex,encounters,events}.js
cache/ backups/ audit/ config.json   （自动生成，均不入库）
```

`tools/account_admin.py` 为旧版独立账号工具，功能已并入本系统，保留仅作参考。

## 验证状态

- 已实测：登录/CSRF/限速拦截、全部资源模块接口、玩家背包读取与发放/删除
  （真实库，测试数据已清理）、审计日志落盘。
- 未实测（避免改动真实玩家数据）：宝可梦字段保存、改名/传送/封禁的实际写入——
  逻辑与背包同链路，请在测试服先用测试账号验证。
