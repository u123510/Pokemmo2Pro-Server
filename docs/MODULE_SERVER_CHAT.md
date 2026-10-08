# `server.chat` 聊天服务器模块

## 1. 入口

`org.pokemmo.chatserver.Main` 启动聊天服务，监听 `7778`，使用通用 Netty/TLS 协议。

## 2. 聊天链路

```text
游戏服务或客户端
  -> ConnectChatServerPacket / JoinChatServerPacket
  -> ChatProtocol
  -> ChatManager
  -> Redis 队列或订阅
  -> ChatBroadcastListener
  -> SendSpeakerChatPacket
```

`ChatServerService` 负责聊天节点信息和连接所需的服务逻辑。聊天消息使用通用 `ChatMessage`、`ChatType` 和 `LanguageType`，跨进程传递依赖 Redis。

## 3. 与游戏服务的关系

游戏服务的 `ChatPacket` 先处理 `NORMAL` 里的 `//` 命令；非命令消息才进入 Redis 聊天广播。新增游戏命令不应直接写入 `server.chat`，否则会破坏命令拦截和普通聊天边界。

## 4. 扩展规则

新增聊天协议时需要同时更新：

1. `protocol/c2s` 或 `protocol/s2c` 封包；
2. `ChatProtocol` 注册；
3. `ChatManager` 状态处理；
4. Redis 消息模型或广播监听器；
5. 对应协议文档。

