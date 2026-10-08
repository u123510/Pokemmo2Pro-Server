# `server.login` 登录服务器模块

## 1. 入口

`org.pokemmo.loginserver.Main` 创建 TLS 协议、数据库、`LoginProtocol` 和 `Server(2106, ...)`。登录服务依赖通用 `server` 与 `db` 模块。

## 2. 协议流程

```text
客户端连接 :2106
  -> TLS 握手
  -> LoginProtocol
  -> LoginPacket
  -> LoginService / ServerService
  -> LoginResultPacket
  -> RequestGameNodeListPacket
  -> SendGameNodeListPacket / SendGameNodeServerListPacket
  -> JoinGameServerPacket
  -> token / game server 地址
```

`LoginProtocol` 注册账号登录、节点列表、加入游戏服务器、重连和 MFA 相关封包。具体 opcode 以源码注册表为准，新增封包不能只添加 class 而遗漏注册。

## 3. 服务和状态

- `LoginService` 负责账号查询、密码校验、角色列表和登录上下文。
- `LoginState` 表示登录阶段。
- `LoginKickType`、`KickReason` 描述拒绝原因。
- s2c 包负责登录结果、服务器列表、重连和密钥/凭证更新。

## 4. 数据安全

数据库 schema 中账号密码保存为摘要字段，登录流程还会创建 server token 与 account context。任何认证相关改动都必须同时检查：账号校验、IP/节点绑定、token 生命周期、重连和断线清理。

## 5. 已知风险

- 连接配置当前在入口代码中固定。
- 登录和游戏服务共享数据库表，字段变更需要协调两个模块。
- 登录成功后的节点选择是客户端协议状态机的一部分，不能绕过 `LoginState` 随意发送后续包。

