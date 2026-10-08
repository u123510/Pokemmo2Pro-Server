# 战斗行动封包

客户端使用 C2S `0x32` 提交战斗中的技能、换位、道具、逃跑或投降命令。

## 基本字段

| 偏移 | 长度 | 字段 | 说明 |
| ---: | ---: | --- | --- |
| `0` | `1` | `commandUserData` | 低 4 位为阵营索引，高 4 位为登场宝可梦索引 |
| `1` | `1` | `commandType` | `0=MOVE`、`1=ITEM`、`2=SWAP`、`3=RUN`、`13=FORFEIT` |

### 技能（MOVE）

技能命令在基本字段后携带一个 little-endian `short moveIndexId`。单打战斗允许省略最后的
`commandTargetData` 字节；服务端会把目标阵营设为对手，并使用目标登场索引 `0`。因此抓包：

```text
32 00 00 9D 00
```

表示阵营 `0` 的第 `0` 个宝可梦使用技能 `0x009D`，不是截断的非法封包。双打、多打和其它需要
选择目标的战斗必须追加一个 `commandTargetData`：低 4 位为目标阵营，高 4 位为目标登场索引。

### 换位与道具

- `SWAP`/`COWER`：追加一个 little-endian `short swapIndex`，之后没有目标字节。例如抓包 `32 00 02 01 00` 表示阵营 0 槽位 0 更换为队伍索引 1 的宝可梦。
- `ITEM`：追加 `short itemIndexId`、`long itemTargetId`（均 little-endian）和一个
  `commandTargetData`。

### 逃跑与投降

`RUN` 和 `FORFEIT` 只有基本字段，没有任何附加字段。战斗类型、可逃跑/可投降状态和 Session
控制权由服务端在处理阶段校验，结果分别见 [`BATTLE_RUN_PACKET.md`](BATTLE_RUN_PACKET.md)。

## 换人机制与生命周期

换人由 `BattleSwitchService` 统一管理，分为主动轮换与阵亡濒死替补换人：

1. **主动轮换（常规换人）**：
   - 玩家在正常行动阶段选择更换宝可梦，作为本回合行动提交，`commandType = SWAP`。
   - 在全员提交行动后，回合结算阶段优先通过 `BattleSwitchService.executeInRoundSwap` 处理换人：校验目标宝可梦不在场且 HP > 0，执行登场替换并广播 S2C `0x35`（`SendSwapPokemonPacket`），随后继续本回合其他行动。

2. **阵亡濒死替补换人**：
   - 当回合结束或受到致命伤害导致宝可梦 HP 归零，若该阵营队伍中仍有存活宝可梦，进入替补等待阶段：
     - 服务端向被击败方发送 S2C `0x36`（`SendPokemonDiedPacket`，`selectorData = faction | (slot << 4)`），触发客户端弹出队伍换人界面；
     - 同时向对手发送 S2C `0x3B`（`SendBattleWaitForPlayerActionPacket`），客户端显示“等待对方行动中”；
     - NPC 阵营直接自动选择首个存活后备宝可梦替补登场。
   - 玩家在换人界面选择宝可梦后，客户端发送 C2S `0x32 SWAP`。
   - `BattleManager.handlePlayerSwap` 检测到目标槽位处于濒死替补等待状态（或原宝可梦 HP <= 0），立即调用 `BattleSwitchService.handleFaintReplacementSwap`：
     - 更新阵营登场槽位并清空旧宝可梦退场数据；
     - 广播 S2C `0x35` 完成替补入场动画与数值同步（必须在退场数据清空前保留正确的 `selectorData`，避免编码为 `0xFF` 导致客户端异常）；
     - 从 `waitingFaintSlots` 集合中移除该槽位。
   - 当所有阵亡槽位均替补完毕后，服务端先等待替补封包和客户端登场动画完成，再调用 `startNextRound()`：递增战斗回合数、广播 S2C `0xC4`（`SendBattleRoundSelectorPacket`），并向所有在场存活宝可梦发送 S2C `0x32`（`SendBattleDebutPokemonCanActionPacket(slot, true)`），正常进入下一回合行动决策。

3. **行动权限包（S2C `0x32`）时序**：
   - 仅在战斗开始及新回合开启（`startNextRound()`）时向存活宝可梦发送 `canAction = true`；
   - 客户端提交行动后不提前回显 `canAction = true`（否则会重置客户端已选行动状态）；仅在行动被服务端拒绝时回显以解除客户端按钮锁定。

## 解码边界

- 未知命令类型、缺少必需字段或道具命令缺少目标字节会拒绝解析。
- MOVE 的目标字节仅在单打中可省略；多目标战斗省略时拒绝处理，不会静默选择目标 `0`。
- 解析完成后仍由通用 `Protocol` 检查是否存在多余字节。

客户端 `f.oj.ig0` 的命令字段顺序与上述结构一致；单打省略目标的形式由实际抓包
`32 00 00 9D 00` 确认；濒死替补换人形式由实际抓包 `32 00 02 01 00` 确认。
