# 玩家观战

## 1. 客户端入口

客户端 `f.Qy0` 的右键菜单在目标玩家状态为 `RL0.Com5` 或 `RL0.YC` 时，
把原生“挑战”菜单项替换为观战回调。回调调用 `gD(player)`，由客户端正式封包
`f.da0_1` 发送开始观战请求，不创建额外的可见菜单项，也不经过聊天窗口。

`f.da0_1` 使用十进制 opcode `52`（即 C2S `0x34`），payload 严格为目标玩家
Object ID 的 8 字节 little-endian `long`。服务端同一 opcode 的 S2C 方向仍保留
`SendBattleRunResultPacket`，用于逃跑/投降结果；C2S 与 S2C 按方向分别注册。

`//spectate <player_name>` 仅作为兼容调试入口，直接调用同一个
`BattleSpectatingService`，不影响原生右键菜单链路。

## 2. 服务端校验与状态

服务端只允许目标在线、同频道、同地区、同地图且正在进行双方均为 `IN_BATTLE` 的
`PlayerBattle`；请求者不能处于战斗、交易或交互状态，每场战斗最多 16 名观战者。
观战者只接收战斗广播，不能提交战斗行动。

PvP 建立时服务端向同地图玩家发送现有 S2C `0x0F` 战斗状态包（`long characterId`
和 `byte status`，状态 `2` 表示战斗中），战斗结束时发送状态 `0`，使客户端及时把
右键菜单切换为“观战”或恢复为“挑战”。

加入观战时发送现有 S2C `0x30`，观战视角使用 `playerFactionIndex = -1`、
`selfTeamInDebutIndex = -1` 和 `isSpectate = true`，双方队伍按只读视角编码。攻击、
状态、回合、宝可梦换位和战斗结束广播都会发送给观战者；换位包对观战者使用只读阵营
视角。观战者关闭窗口发送 C2S `0x35`（无字段）退出观战；断线、重连和战斗结束都会
清理观战状态。
