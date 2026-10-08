# 战斗逃跑与投降封包

战斗菜单中的逃跑使用 C2S `0x32` `BattlePokemonCommandPacket`，命令类型为
`BattlePokemonCommandType.RUN = 3`。玩家对战投降使用同一 opcode，命令类型为
`BattlePokemonCommandType.FORFEIT = 13`。两种命令都没有技能、交换、道具或目标附加字段。

## 抓包

```text
32 00 03
```

| 偏移 | 长度 | 字段 | 值 | 说明 |
| ---: | ---: | --- | --- | --- |
| `0` | `1` | opcode | `0x32` | 战斗行动请求 |
| `1` | `1` | commandUserData | `0x00` | 低 4 位为阵营，高 4 位为出战/队伍选择索引 |
| `2` | `1` | commandType | `0x03` | `RUN` |

客户端 `f.oj.ig0` 字节码确认：封包先写 `commandUserData`，再写命令类型；只有
MOVE、SWAP/COWER 和 ITEM 分支会继续写附加字段，RUN 在第二个 payload 字节后结束。

玩家对战投降只将最后一个字节替换为 `0D`：

```text
32 00 0D
```

## 逃跑结果

S2C `0x34` `SendBattleRunResultPacket` 必须先于战斗结束包发送：

| 偏移 | 长度 | 字段 | 说明 |
| ---: | ---: | --- | --- |
| `0` | `1` | opcode | `0x34` |
| `1` | `1` | selectorData | 回显 C2S 的 `commandUserData` |
| `2` | `1` | resultType | 逃跑或投降结果 |

当前使用的结果为：

| 值 | 枚举 | 用途 |
| ---: | --- | --- |
| `0` | `NORMAL_SUCCESS` | 野外战斗成功逃跑 |
| `5` | `FORFEIT` | 玩家对战投降 |

客户端 `f.BJ` 将 `0x34` 解码为 `BattleRunMessage`。`NORMAL_SUCCESS` 显示成功逃跑消息；
`FORFEIT` 走玩家投降的独立消息路径。`NORMAL_FAIL` 等其它结果类型已存在，但当前服务端
尚未实现基于速度或特殊效果的逃跑失败判定。

## 服务端处理

```text
C2S 0x32 RUN
  -> BattlePokemonCommandPacket 校验 BattleManager、阵营、索引和 Session 控制权
  -> BattleOutcomeResolver 只接受 WildBattle 且校验 BattleBasisInfo.canRun
  -> 校验逃跑方和另一方仍为 IN_BATTLE
  -> 逃跑方设为 RUN，另一方设为 VICTORY
  -> S2C 0x34 NORMAL_SUCCESS
  -> S2C 0x31 SendBattleFinishPacket，展示阵营使用实际胜方阵营
  -> 客户端 C2S 0x33 BattleFinishSuccessPacket
  -> 清理 CharacterManager.battleManager
```

野生阵营的 `VICTORY` 同时作为结束包的实际胜方阵营；S2C `0x34 NORMAL_SUCCESS`
负责显示成功逃跑消息。成功逃跑不会进入正常回合结算，也不会再发送
`SendBattleDebutPokemonCanActionPacket`。客户端的 `0x33` 回执到达前仍保留
`BattleManager`，与胜利、失败和捕获结束路径一致。

玩家对战投降流程为：

```text
C2S 0x32 FORFEIT
  -> 仅 PlayerBattle 且 BattleBasisInfo.canSurrender = true 时接受
  -> 投降方设为 DEFEAT，另一方设为 VICTORY
  -> S2C 0x34 FORFEIT
  -> S2C 0x31，展示阵营使用实际胜方
  -> 客户端 C2S 0x33 清理战斗上下文
```

## 战斗类型边界

| 战斗类型 | `RUN = 3` | `FORFEIT = 13` |
| --- | --- | --- |
| `WildBattle` | 成功逃跑，发送 `NORMAL_SUCCESS` | 拒绝 |
| NPC/训练家/Boss 战斗 | 拒绝；初始化信息的 `canRun` 为假 | 拒绝 |
| `PlayerBattle` | 拒绝，不解释为逃跑或投降 | 投降并判对方胜利 |

## 边界

- 仅 `BattleType.WildBattle` 且 `BattleBasisInfo.canRun = true` 时允许 RUN。
- `PlayerBattle` 初始化时启用 `canSurrender`，当前投降限制回合为 `0`，即允许立即投降。
- 请求 Session 必须控制 `commandUserData` 指向的阵营/队伍，伪造其它阵营会被忽略。
- 任一阵营已经离开 `IN_BATTLE` 时拒绝重复结束请求。
- 逃跑不按 `DEFEAT` 处理，因此 `BattleFinishSuccessPacket` 不会执行战败后的队伍恢复逻辑。

## 验证

- 已静态确认 `GameProtocol` 将 C2S `0x32` 注册为 `BattlePokemonCommandPacket`、S2C
  `0x31` 注册为 `SendBattleFinishPacket`、S2C `0x34` 注册为
  `SendBattleRunResultPacket`、C2S `0x33` 注册为 `BattleFinishSuccessPacket`。
- 已对照客户端 `f.oj` 字节码确认 RUN/FORFEIT 请求均无尾随字段，并对照客户端
  `f.BJ`、`f.JL0` 确认 S2C `0x34` 的字段顺序和结果消息分支。
- 按项目约定未编译、未启动服务器；野外逃跑和玩家投降的实际动画仍需运行验证。
