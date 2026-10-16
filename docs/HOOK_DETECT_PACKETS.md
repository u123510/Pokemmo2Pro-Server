# Hook 检测与环境扫描封包

本文件记录游戏服务器 `GameProtocol` 中 Hook 检测 / 环境扫描相关封包的 opcode、方向和当前字段布局。
这些封包类以新增文件的形式加入，并在 `GameProtocol` 中注册对应 opcode；`decode` / `encode` 按抓包记录的
原始顺序读写字段，不改变客户端可见的字节序列。

## 状态说明

- 8 个封包类已全部在 `GameProtocol` 注册，opcode 与方向沿用原有抓包记录，未改动。
- **没有任何服务端代码会发送这些 S2C 封包**；C2S 封包解码后不产生业务副作用（不写数据库、不改在线内存、不回包）。
- 下列字段语义**均未通过真实抓包或客户端字节码验证**，一律按“未知”处理，实现不推测含义。
- 字段顺序按客户端实际发送 / 接收的字节序列保留，当前不额外做长度下限或取值范围校验；
  封包长度与声明的字段不一致时，由 `ByteBufEx` 读取越界和 `Protocol` 解析失败路径处理（记录异常并关闭连接）。

## C2S（客户端 -> 游戏服务器）

| opcode | 封包 | 字段（按读取顺序） | 校验 |
| --- | --- | --- | --- |
| `0xA6` | `EnvironmentInfoExceptWhiteListPacket` | 未知结构，整包保留为 `byte[] payload` | 按可读字节数读取，无长度下限 |
| `0xA7` | `EnvironmentInfoExceptBlackListPacket` | 未知结构，整包保留为 `byte[] payload` | 按可读字节数读取，无长度下限 |
| `0xF1` | `DetectHookResultPacket` | `long payloadSize` + `long id` + `int bufferRemain` + `short fragmentLength` + `byte[fragmentLength] remainBuffer` | 无长度下限 |

说明：

- `0xF1` 的字段顺序按客户端上报结构保留：`payloadSize` 是报文声明的长度字段，`bufferRemain` 和
  `fragmentLength` 的含义尚无抓包或字节码证据；`remainBuffer` 按 `fragmentLength` 读取。
- `DetectHookResultPacket` 中保留的 `HEADER_SIZE`（`long + int + short`）当前未被使用，属于待验证字段语义的遗留声明。
- `0xA6` / `0xA7` 的条目结构（键值对还是连续记录）尚未验证，因此整包保留为 `payload`，不做字段拆分，
  避免只保留最后一个字节而丢掉上报内容。
- `Protocol` 解析失败会抛异常并关闭连接，因此 C2S 封包必须接受客户端实际发送的合法长度。

## S2C（游戏服务器 -> 客户端）

| opcode | 封包 | 字段（按写入顺序） | 当前取值 |
| --- | --- | --- | --- |
| `0xA6` | `SendScanEnvironmentWithWhiteList` | `short hashCount` + `short bitSetSize` + `short bitSetLength` + `byte[bitSetLength] bitSet` | `0x180` / `3` / `49` |
| `0xA7` | `SendScanEnvironmentWithBlackList` | `short idSize` + `short bufferSize` + `byte[bufferSize] payload` | `0` / `0`（当前不写出位图字节，整包 4 字节） |
| `0xAB` | `SendHookDetectBeginPacket` | `long id` + `int unk2` + `byte[73] payload` | `0` / `46701` / 固定载荷 |
| `0xAC` | `SendHookDetectShellCodePacket` | `byte[] shellCodeBuffer` | 由调用方提供，无调用方 |
| `0xFD` | `SendInputRecordPacket` | `int executeTime` | 由调用方提供，无调用方 |

说明：

- `0xA6` 中 `bitSetSize`(3) 与实际写出的 `bitSetLength`(49) 不一致，含义未知；本次**保持原字节序列不变**，
  只把 `int[]` 改成 `byte[]`，避免 `int` 到 `byte` 的隐式截断。
- `0xA7` 中 `idSize` 与 `bufferSize` 抓包记录均为 `0`，故当前只写出 4 字节头部。
  位图数组以 `payload` 保留作为抓包记录，当前不会被写出（`bufferSize` 为 `0`）。
- `0xAB` 写入 `id`(0) + `unk2`(46701) + 73 字节固定载荷，字节序列与抓包记录一致；
  该类没有 `@Inject` 字段，构造完全由 `GameProtocol` 完成。
- `0xAC` 构造时做防御性拷贝并拒绝 `null` / 空数组。
- `0xFD` 构造时做非负校验。

## 方向复用

`0xA6`、`0xA7`、`0xF1` 在同一 opcode 上同时存在 C2S 与 S2C 封包（`0xF1` 的 S2C 是 `SendMaxChannelAmountPacket`）。
`Protocol` 的注册表按 `DataFlow` 分开存储，因此双向复用同一 opcode 不会冲突。

## 验证情况

已执行的静态检查：

- `rg` 搜索确认全仓库没有任何地方 `new` 或发送这 8 个封包，S2C 封包当前是死代码。
- 脚本比对确认 `SendHookDetectBeginPacket`(73)、`SendScanEnvironmentWithBlackList`(49)、
  `SendScanEnvironmentWithWhiteList`(49) 的字节常量与抓包记录逐字节一致。
- `GameProtocol.java` 已注册全部 8 行（`0xA6` C2S+S2C、`0xA7` C2S+S2C、`0xAB`、`0xAC`、`0xF1` C2S、`0xFD`），opcode 与方向与上表一致。
- 用到的 `ByteBufEx` / `ByteBufDelegator` 方法（`readLongLE`、`readIntLE`、`readShortLE`、
  `readableBytes`、`readByteArray`、`writeLongLE`、`writeIntLE`、`writeShortLE`、`writeBytes`）均已存在于代理类中。
- 本文档的字段名、读取顺序和取值已按当前源码逐文件核对，与实际实现一致（无长度校验）。

**未执行**（按仓库约定由用户自行完成）：编译、启动服务器、连接客户端、实际抓包比对。
上表字段名与语义属于“未验证”，如需正式启用这些封包，应先补齐抓包证据和客户端字节码分析。

## 更新日志

| 日期 | 提交 | 内容 |
| --- | --- | --- |
| 2026-10-16 | `add new packet` | 新增 8 个封包类（`0xA6` C2S+S2C、`0xA7` C2S+S2C、`0xAB`、`0xAC`、`0xF1` C2S、`0xFD`）并在 `GameProtocol` 注册对应 opcode，同步建立本文档。 |
| 2026-10-16 | `add new packet` | 同步 C2S 解析顺序（`0xF1` 首位 `payloadSize`、`0xA6` / `0xA7` 整包读取），按实际实现更新本文档的字段表、校验说明与取值。 |