# `server` 通用核心模块

## 1. 职责

`server` 是所有登录、游戏和聊天服务共享的底层网络模块。它不包含具体游戏规则，主要提供：

- Netty Server Bootstrap 和事件循环选择；
- `Protocol`、`Packet`、`BufferedPacket` 和 `DataFlow` 抽象；
- 帧编解码、封包编解码、日志、超时、压缩和 TLS；
- Guice 注入的 Session 和服务生命周期；
- Redis 连接工具和跨服务聊天数据结构；
- 通用语言、聊天和 IP 工具。

## 2. 服务启动

`org.server.Server` 根据 `TransportMethod` 选择 NIO、epoll 或 kqueue，创建 acceptor/client 两组 EventLoop，并安装：

```text
ReadTimeoutHandler(25 minutes)
FrameCodec
LoggingHandler("packets")
PacketCodec
Session
```

上层模块只需要传入端口和 `SessionInitializer`，不直接管理 Netty Channel pipeline。

`LoggingHandler("packets")` 保留帧解码后的 Netty 原始字节日志。`Session` 额外在 `TRACE` 级别记录每个
收发包的 Session/Channel ID、递增封包序号、数据流、协议类、十六进制和十进制 opcode、注册封包类型、
payload 与 opcode+payload 长度、编码/解码/排队/处理耗时、原始 packet hex 及反射得到的协议字段值。
密码、token、credentials、session key、硬件标识等敏感字段和对应原始 payload 会被脱敏，不写入新增日志。

## 3. 协议模型

`Protocol` 为每个 `DataFlow` 维护两张表：

- `byte opcode -> Packet class`：解码；
- `Packet class -> byte opcode`：编码。

解码过程会通过 Guice 创建封包，调用 `decode`，然后要求 ByteBuf 已经完全读取。未注册 opcode 或剩余字节都会抛出异常并由 Session 关闭连接。
解析失败日志还会包含数据流、opcode、预期 packet 类、payload 长度、已消费字节数和剩余字节数。

## 4. Session 生命周期

`Session` 包含：

- 未加密/已加密两种状态；
- 未压缩/已压缩 pipeline；
- 服务端加密出包在 opcode 后始终保留一个压缩标志字节；当前未压缩时写入 `0`，由客户端网络层先消费；
- 同步或异步封包处理；异步模式下普通封包按连接顺序串行执行，标记为长操作的封包使用独立串行队列，避免脚本等待阻塞移动等实时请求；
- `send(Packet...)` 编码、写入和 flush；
- `DisconnectListener` 断线通知。

游戏服务通过断线监听器处理账号上下文和角色状态。新增服务时应明确 `ServerType`，否则断线清理逻辑可能不会执行。

## 5. 扩展规则

新增通用协议能力时优先放在本模块；只服务于游戏、登录或聊天的封包应留在对应模块。不要在核心模块中引用 `server.game` 领域类，避免依赖环。
