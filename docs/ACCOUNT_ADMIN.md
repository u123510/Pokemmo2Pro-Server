# 账号管理网页

`tools/account_admin.py` 是一个本地网页管理工具。它直接连接项目的 PostgreSQL，可查看和维护账号、角色及角色金钱。

## 功能

- 输入账号名、明文密码、角色名和性别。
- 明文密码只在内存中使用，保存为当前客户端兼容的 SHA-1 原始摘要字节。
- 查看全部账号及其角色、性别、金钱和出生位置。
- 给已有账号新增角色，修改角色金钱，删除角色或删除整个账号。
- 账号权限固定为普通玩家权限 `3`。
- 角色出生点沿用初始化脚本中 Kyu 的位置：`region_id=0`、`map_header_id_or_gba_map_group_id=4`、`gba_map_id=1`、`x=6`、`y=6`、`z=0`。
- 角色默认金钱、点券和对战点数为 `0`，不会复制 Kyu 的管理员资产或默认宝可梦。
- 账号和角色创建失败时整体回滚。
- 启动时固定只监听 `127.0.0.1`，网页不需要输入密码；页面表单使用随机页面令牌。

## 安装

在项目根目录执行：

```powershell
py -m pip install -r tools/requirements.txt
```

## 配置与启动

工具会优先读取环境变量 `DB_HOST`、`DB_PORT`、`DB_NAME`、`DB_USER`、`DB_PASSWORD`。如果这些变量没有设置，它会自动读取现有 `server.login/Main.java` 中的 `new Database(...)` 配置（找不到时再读取 `server.game/Main.java`），所以不需要把数据库密码重复写入 Python 文件。

PowerShell 启动示例：

```powershell
$env:WEB_PORT = "8080"
py tools/account_admin.py
```

浏览器直接打开 `http://127.0.0.1:8080/`，不需要输入网页密码。页面提供新增账号/角色、修改金钱、删除角色和删除账号功能。

不要把数据库密码或其他凭据提交到 Git。删除账号/角色前应确认对应玩家已经下线；删除会清理其宝可梦、道具和数据库关联数据，操作不可恢复。程序固定绑定本机地址，不提供公网访问。

## 数据库前提

数据库必须已经执行 `db/schemas/init.sql`。工具会在事务中调整 `account.account_id` 和 `character.id` 的序列位置，以兼容初始化脚本显式插入 `character.id=1` 的情况；不会创建 `account_context`，该表由登录/游戏流程在玩家进入游戏时维护。
