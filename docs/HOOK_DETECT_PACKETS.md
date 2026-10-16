# Hook 检测与环境扫描封包

本文件记录游戏服务器 `GameProtocol` 中 Hook 检测 / 环境扫描相关封包的 opcode、方向和当前字段布局。
本次为这些封包补齐 opcode 注册，并重构了 `decode` / `encode` 内部实现。

## 状态说明

- 8 个封包类已全部在 `GameProtocol` 注册，opcode 与方向沿用原有抓包记录，未改动。
- **没有任何服务端代码会发送这些 S2C 封包**；C2S 封包解码后不产生业务副作用（不写数据库、不改在线内存、不回包）。
- 除长度校验外，下列字段语义**均未通过真实抓包或客户端字节码验证**，一律按“未知”处理，实现不推测含义。
- 重构原则：保持原有字节序列不变。已用脚本比对确认三处硬编码字节数组（73 + 49 + 49 字节）与重构前逐字节一致。

## C2S（客户端 -> 游戏服务器）

| opcode | 封包 | 字段（按读取顺序） | 校验 |
| --- | --- | --- | --- |
| `0xA6` | `EnvironmentInfoExceptWhiteListPacket` | 未知结构，整包保留为 `byte[] payload` | 无长度下限 |
| `0xA7` | `EnvironmentInfoExceptBlackListPacket` | 未知结构，整包保留为 `byte[] payload` | 无长度下限 |
| `0xF1` | `DetectHookResultPacket` | `long id` + `int totalBufferLength` + `short fragmentLength` + `byte[fragmentLength] resultBuffer` | 总长 >= 14；`totalBufferLength >= 0`；`fragmentLength` 必须等于剩余字节数 |

说明：

- 旧实现在 `0xA6` / `0xA7` 中用 `while (buffer.isReadable())` 循环读取单个字节，只保留最后一个字节，
  前面的内容被丢弃。该循环的真实结构（键值对还是连续记录）没有抓包证据，因此改为原样保留整个 payload，
  同时删除了 `0xA6` 中声明后从未赋值的 `hashSize` / `value` / `listSize` / `listValue` 字段。
- `0xF1` 的 `fragmentLength` 需要等于实际剩余字节数，是因为 `Protocol.decode` 要求封包被完整读取；
  分片长度与 payload 不一致时直接抛 `IllegalArgumentException`，由协议层记录并断开。
- `Protocol` 解析失败会抛异常并关闭连接，因此 C2S 封包必须接受客户端实际发送的合法长度。

## S2C（游戏服务器 -> 客户端）

| opcode | 封包 | 字段（按写入顺序） | 当前取值 |
| --- | --- | --- | --- |
| `0xA6` | `SendScanEnvironmentWithWhiteList` | `short hashCount` + `short bitSetSize` + `short bitSetLength` + `byte[bitSetLength] bitSet` | `0x180` / `3` / `49` |
| `0xA7` | `SendScanEnvironmentWithBlackList` | `short hashCount` + `short bitSetSize` + `byte[bitSetSize] bitSet` | `0` / `0`（当前不写出位图字节，整包 4 字节） |
| `0xAB` | `SendHookDetectBeginPacket` | `long sessionId` + `int marker` + `byte[73] detectPayload` | `0` / `46701` / 固定载荷 |
| `0xAC` | `SendHookDetectShellCodePacket` | `byte[] shellCodeBuffer` | 由调用方提供，无调用方 |
| `0xFD` | `SendInputRecordPacket` | `int executeTime` | 由调用方提供，无调用方 |

说明：

- `0xA6` 中 `bitSetSize`(3) 与实际写出的 `bitSetLength`(49) 不一致，含义未知；本次**保持原字节序列不变**，
  只把 `int[]` 改成 `byte[]`，避免 `int` 到 `byte` 的隐式截断。
- `0xA7` 中 `hashCount` 与 `bitSetSize` 抓包记录均为 `0`，故当前只写出 4 字节头部。
  构造函数外的位图数组保留作为抓包记录，当前不会被写出。
- `0xAB` 去掉了原先从未使用、也不会被 Guice 注入的 `@Inject ServerService` 字段；写入的字节不变。
- `0xAC` 构造时做防御性拷贝并拒绝 `null` / 空数组，此前没有校验。
- `0xFD` 增加非负校验，此前没有校验。

## 方向复用

`0xA6`、`0xA7`、`0xF1` 在同一 opcode 上同时存在 C2S 与 S2C 封包（`0xF1` 的 S2C 是 `SendMaxChannelAmountPacket`）。
`Protocol` 的注册表按 `DataFlow` 分开存储，因此双向复用同一 opcode 不会冲突。

## 验证情况

已执行的静态检查：

- `rg` 搜索确认全仓库没有任何地方 `new` 或发送这 8 个封包，S2C 封包当前是死代码。
- 脚本比对确认 `SendHookDetectBeginPacket`(73)、`SendScanEnvironmentWithBlackList`(49)、
  `SendScanEnvironmentWithWhiteList`(49) 的字节常量与重构前逐字节一致。
- `GameProtocol.java` 已注册全部 8 行（`0xA6` C2S+S2C、`0xA7` C2S+S2C、`0xAB`、`0xAC`、`0xF1` C2S、`0xFD`），opcode 与方向与上表一致。
- 用到的 `ByteBufEx` / `ByteBufDelegator` 方法（`readLongLE`、`readIntLE`、`readShortLE`、
  `readableBytes`、`readByteArray`、`writeLongLE`、`writeIntLE`、`writeShortLE`、`writeBytes`）均已存在于代理类中。

**未执行**（按仓库约定由用户自行完成）：编译、启动服务器、连接客户端、实际抓包比对。
上表字段名与语义属于“未验证”，如需正式启用这些封包，应先补齐抓包证据和客户端字节码分析。
