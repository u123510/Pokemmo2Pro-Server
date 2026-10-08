# 附近玩家交易请求封包

## 范围

当前实现覆盖“向同地图、同频道的在线玩家发送交易请求确认框”，以及接受/拒绝后进入完整玩家交易的入口。交易窗口、报价和原子提交的后续字段见 [`TRADE_PACKET.md`](TRADE_PACKET.md)。

## C2S `0x51` / `0x62`

| 字段 | 编码 | 示例 |
| --- | --- | --- |
| `targetPlayerName` | UTF-16LE，以 `00 00` 结束 | `Kyu` |

抓包示例：

```text
62 4B 00 79 00 75 00 00 00
```

官方客户端使用 `0x51`，用户抓包中还出现 `0x62`；两者在 `GameProtocol` 都注册为 `RequestTradePacket`，共享相同 UTF-16LE 名称解析和目标校验逻辑。服务端只从发起者当前地图的玩家会话池查找目标，并校验目标在线、同频道、同地图且不是发起者本人；不满足条件时记录警告并忽略请求。

## S2C `0x21`

服务端复用 `SendInteractPacket`，字段头保持现有交互格式：

```text
interactTimes byte
interactionType byte = 22 (0x16, REQUEST_TRADE；服务端内部枚举值仍为 18)
stringOffset int LE = 0
entityGameId long LE = -1 (Scene)
interactDelay int LE = 0
localStringFormatSize byte = 0
requesterName UTF-16LE NUL
```

请求尾部的发起者名称是客户端显示确认文本所需的字段。客户端当前将 `REQUEST_TRADE` 映射到线值 `22 (0x16)`；使用旧线值 `18 (0x12)` 时不会弹出交易请求窗口。请求在 30 秒内没有回执会自动取消并清理目标的交互状态。

## 校验与边界

- 目标名称不能为空，最多 20 个字符，不接受控制字符。
- 不允许向自己发起交易。
- 发起者和目标都必须有有效且 active 的游戏会话。
- 目标必须位于发起者当前地图和同一频道，且不能处于对话或战斗状态。
- 请求阶段不写数据库、不改变角色资产；目标接受后由 `TradeManager` 建立在线会话，具体报价和提交边界见 [`TRADE_PACKET.md`](TRADE_PACKET.md)。
