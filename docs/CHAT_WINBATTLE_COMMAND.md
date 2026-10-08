# `//winbattle` 战斗胜利命令

## 输入格式

客户端通过 `ChatType.NORMAL` 发送：

```text
//winbattle
```

该命令不接收参数，并沿用聊天命令框架的默认 `GM` 权限。

## 前置条件

命令执行时必须满足：

1. 当前角色已经完成加载；
2. `CharacterManager.battleManager` 不为空；
3. 当前 `Session` 属于战斗中的一个阵营；
4. 双方阵营状态仍为 `IN_BATTLE`。

不在战斗中、会话不属于该战斗或战斗已经结束时，命令只返回失败反馈，不会再次发送结束包。

## 处理流程

```text
WinBattleCommand
  -> BattleManager.forceVictory(session)
  -> 当前会话阵营设置为 VICTORY
  -> 对方阵营设置为 DEFEAT
  -> BattleContext.checkAndHandleBattleFinish(...)
  -> 向战斗内所有玩家发送 0x31 SendBattleFinishPacket
  -> 客户端回发 0x33 BattleFinishSuccessPacket
  -> 清除当前角色的 BattleManager
```

合作战斗中，命令按阵营结算；同一玩家阵营中的所有玩家都会被判定为胜利，并收到战斗结束包。

## 结算边界

- 命令复用已有战斗结束包和客户端成功回执，不会提前清除 `BattleManager`。
- 命令不会把敌方宝可梦生命值改为 `0`。
- 命令不会补发击败宝可梦经验。
- 当前战斗结束实现发送的奖励金钱和拾取金钱均为 `0`，命令不额外修改数据库。
- 正常战斗脚本、奖励和胜利事件仍受现有战斗结算实现限制。

## 静态验证

- `WinBattleCommand` 已在 `GameCommandModule` 注册；
- 命令参数限定为零个；
- 强制胜利前会校验会话阵营和双方战斗状态；
- 结束后仍由客户端 `0x33` 回执执行原有清理流程；
- 未新增或修改 opcode。

运行时客户端收包、多人/PVP 和脚本战斗行为尚未验证。
