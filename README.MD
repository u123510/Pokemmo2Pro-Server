# OpenMMO

[![加入 Discord](https://dcbadge.limes.pink/api/server/SQwGXyY2gz)](https://discord.gg/SQwGXy2gz)
![Java CI with Gradle](https://github.com/Fiereu/OpenMMO/actions/workflows/gradle.yml/badge.svg)

> OpenMMO 目前仍处于开发阶段，不建议用于生产环境。

OpenMMO 是一个开源的 PokeMMO 服务端实现，目标是提供一个免费的开源替代方案。

_[PokeMMO](https://pokemmo.eu/) 与本项目没有任何关系。使用或搭建私人服务器可能违反 [PokeMMO 服务条款](https://pokemmo.com/tos/)。_

本项目通过逆向分析 PokeMMO 客户端以及观察网络通信开发，仅用于学习和研究，不得用于商业用途。

客户端主要使用 Java 和 LibGDX 编写。本项目的网络层使用 Netty 实现。当前项目主要适配客户端版本 **26211**。

## 当前状态

目前已经实现基础登录流程，以及从登录服务器跳转到游戏服务器的流程。客户端可以加载地图和玩家位置，移动和多人相关功能仍在持续开发中。

## 开发文档

项目结构、模块职责、源码索引、资源目录和后续开发约定统一记录在 [`docs/README.md`](docs/README.md)。

仓库开发代理规范见 [`AGENTS.md`](AGENTS.md)，其中明确了代码规范、package 放置规则、协议/命令扩展流程和文档同步要求。

## 环境要求

- Windows、Linux 或 macOS
- JDK 17 或更高版本
- Docker Desktop，以及 Docker Compose
- 已准备好项目根目录中的证书文件：`game.public`、`game.private`

## 快速启动

以下命令默认在项目根目录执行。

### 1. 启动 PostgreSQL

```powershell
docker compose up -d --build
```

查看数据库容器状态：

```powershell
docker compose ps
```

默认数据库连接信息：

| 项目 | 值 |
| --- | --- |
| 地址 | `127.0.0.1` |
| 端口 | `5432` |
| 数据库 | `mmo_db` |
| 用户名 | `openmmo` |
| 密码 | `12345678` |

数据库首次启动时会自动执行 `db/schemas/init.sql`，并创建默认账号：

```text
账号：admin
密码：admin
```

### 2. 启动登录服务器

打开第一个终端窗口：

```powershell
.\gradlew.bat :server.login:run
```

登录服务器监听端口：`2106`。

### 3. 启动游戏服务器

再打开第二个终端窗口：

```powershell
.\gradlew.bat :server.game:run
```

游戏服务器监听端口：`7777`。

### 4. 启动聊天服务器（可选）

如果需要聊天功能，再打开第三个终端窗口：

```powershell
.\gradlew.bat :server.chat:run
```

聊天服务器监听端口：`7778`。

## 启动客户端

先把项目根目录下的 `game.public` 复制到：

```text
patcher/src/main/resources/game.public
```

然后构建补丁程序：

```powershell
.\gradlew.bat :patcher:shadowJar
```

最后在 PokeMMO 安装目录中运行补丁程序。将下面的路径替换为实际的 `patcher.jar` 路径：

```powershell
java -javaagent:C:\path\to\patcher.jar -jar "C:\Program Files\PokeMMO\PokeMMO.exe"
```

## 常用 Docker 命令

停止数据库：

```powershell
docker compose down
```

查看数据库日志：

```powershell
docker compose logs -f db
```

Adminer 管理页面地址：<http://localhost:5050>

登录 Adminer 时选择：

- 系统：`PostgreSQL`
- 服务器：`db`（从宿主机连接时使用 `127.0.0.1`）
- 用户名：`openmmo`
- 密码：`12345678`
- 数据库：`mmo_db`

## 免责声明

本项目仍在开发中，可能存在未完成的功能和兼容性问题。请仅将其用于学习、研究和测试。

## 贡献

欢迎提交 Issue 和 Pull Request，帮助完善项目。
