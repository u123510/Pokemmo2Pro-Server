# 对战经验与等级结算封包

对战中击败敌方宝可梦后，服务端向参战玩家发送等级经验更新与经验获得明细封包。

## 封包概览与时序契约

对战回合结算时，必须遵循严格的客户端事件队列时序：

```text
1. S2C 0x33: 广播技能行动（技能释放动画、扣血、濒死倒下动画）
2. S2C 0x16: 发送宝可梦等级经验更新包（SendUpdatePokemonDataPacket）
3. S2C 0x79: 发送经验获得明细包（SendPokemonGetExpPacket）
4. S2C 0x31: （若战斗胜负已分）发送战斗结束包（SendBattleFinishPacket）
```

> [!IMPORTANT]
> **关键时序约束（先 0x16 后 0x79）**：
> 客户端（`cn.pokemmo.net.packet.inbound.PokedexEntryUpdatePacket` / 混淆类 `OM`）在收到 `0x16` 且包含等级经验标志位（`flag & 1 != 0`）时，会在对战事件队列 `manager.Tk0` 中创建 `LM`（`BattlePokedexCatchModifier`）事件对象。
> 随后，客户端在收到 `0x79`（`BattleOpcode121Packet` / 混淆类 `sy0_0`）时，会遍历 `manager.Tk0` 查找到对应的 `LM`，并将经验明细挂载至 `LM.M6`。
> **如果服务端先发 0x79 后发 0x16**，客户端在处理 0x79 时队列中尚无 `LM`，经验明细被静默丢弃；之后到达的 0x16 创建默认 `M6`（全 0），导致结算动画计算 `expValue = 0` 并输出「获得了 0 点经验值！」。

---

## 封包字段结构

### 1. 宝可梦数据更新包（S2C `0x16`）

```text
flow: SERVER_TO_CLIENT
opcode: 0x16 (十进制 22)
```

经验等级更新涉及的核心字段：

| 偏移 | 长度 | 类型 | 字段 | 说明 |
| ---: | ---: | --- | --- | --- |
| `0x00` | 8 | `longLE` | `pokemonId` | 宝可梦全局唯一 ID |
| `0x08` | 4 | `intLE` | `flag` | 更新字段标志位（位 `0` 为重载等级与经验，位 `3` 为 HP，位 `7` 为努力值，位 `15` 为回忆招式/OT 保留位） |
| 可选 | 1 | `byte` | `level` | 仅当 `flag & 1 != 0` 时存在，更新后的等级（`1..100`） |
| 可选 | 4 | `intLE` | `exp` | 仅当 `flag & 1 != 0` 时存在，更新后的总经验值 |

### 2. 经验获得明细包（S2C `0x79`）

```text
flow: SERVER_TO_CLIENT
opcode: 0x79 (十进制 121)
```

| 偏移 | 长度 | 类型 | 字段 | 说明 |
| ---: | ---: | --- | --- | --- |
| `0x00` | 8 | `longLE` | `pokemonId` | 获得经验的宝可梦全局 ID（用于在客户端对战队列匹配 `LM`） |
| `0x08` | 4 | `intLE` | `baseExp` | 单只宝可梦分摊的基础经验值 |
| `0x0C` | 1 | `byte` | `flag` | 加成位掩码（见下表） |
| 可变 | 4 | `intLE` | `trainerBattleBonus` | 条件字段：训练家对战加成（`flag & 1 != 0`） |
| 可变 | 4 | `intLE` | `tradeBonus` | 条件字段：交换宝可梦经验加成（`flag & 2 != 0`） |
| 可变 | 4 | `intLE` | `charmBonus` | 条件字段：经验护符加成（`flag & 4 != 0`） |
| 可变 | 4 | `intLE` | `donatorBonus` | 条件字段：捐赠者状态加成（`flag & 8 != 0`） |
| 可变 | 4 | `intLE` | `heldItemBonus` | 条件字段：携带道具加成（`flag & 16 != 0`） |
| 可变 | 4 | `intLE` | `reamplifierBonus` | 条件字段：经验重放大器加成（`flag & 32 != 0`） |

---

## 经验计算规则与经验池维护

1. **基础经验公式**：
   $$gainedBaseExp = \left\lfloor \frac{\text{defeatedLevel} \times \text{yieldBaseExp}}{7.0} \times \text{scale} \right\rfloor + 1$$
   其中 $\text{scale} = \frac{\text{defeatedLevel}}{\text{defeatedLevel} + \text{defeaterLevel}}$（当 $\text{scale} < 0.5$ 时乘以 $1.5$ 防止低级刷怪惩罚）。训练家对战的基础经验额外乘以 $1.5$。
2. **经验池（`expPoolPokemons`）**：
   - 战斗开始时，敌方首发宝可梦的经验池包含我方存活登场宝可梦；
   - 战斗中双方换宠（`SWAP`）时，新上场的存活宝可梦加入敌方登场宝可梦的经验池；
   - 结算击败时，最终完成击杀的 `defeaterPokemon` 若存活且未满 100 级，保证加入被击败怪的经验池。
3. **经验分摊**：
   $$baseExpPerPokemon = \max\left(1, \left\lfloor \frac{gainedBaseExp}{\text{eligibleCount}} \right\rfloor\right)$$
   对经验池内所有未满 100 级且当前 HP $>0$ 的宝可梦独立计算加成并结算。

---

## 状态与持久化同步

经验结算完成后，服务端同时更新三层状态：
1. **在线内存**：更新 `gainedExpPokemon.getPokemonData()` 的 `exp`、`level` 及 `pokemonEvs`。
2. **客户端同步**：先发送 S2C `0x16`，再发送 S2C `0x79`。
3. **数据库落库**：调用 `GameServerService.updatePokemonGrowth(characterId, pokemonId, newExp, newLevel, evYields)`，双重条件匹配 `POKEMON.ID` 与 `POKEMON.TRAINER_ID`，持久化 `EXP`、`LEVEL_VALUE` 与 `EV_VALUES`。

---

## 回合结算触发与防重机制

1. **触发入口（双重保障）**：
   - **技能动作广播后即时触发**：在 `BattleRoundSettlement.InRoundCommandSettlement` 广播 `0x33` 技能行动封包（包含扣血与倒下动画）后，立即调用 `settleFaintedPokemonsExp()`，确保客户端在收到战斗结束 `0x31` 前先接收到 `0x16` 与 `0x79` 经验事件。
   - **胜负判定前兜底触发**：在 `BattleOutcomeResolver.checkAndHandleBattleFinish` 的入口处兜底调用 `settleFaintedPokemonsExp()`，防止回合末状态伤害（中毒、灼伤等）致死后漏结算经验。
2. **阵营过滤**：
   - 经验结算仅对**敌方阵营**中濒死倒下（`getCurrentHp() == 0`）的宝可梦触发；
   - 玩家阵营宝可梦濒死倒下跳过经验结算。
3. **防重复结算（`expSettled` 标志位）**：
   - 每只登场宝可梦在 `BattlePokemonData` 中维护 `expSettled` 标志位；
   - 结算完成后立即标记 `expSettled = true`；
   - 仅在宝可梦退场（`clearDataAfterExit`）时重置为 `false`，回合末清理（`clearDataAfterRound`）保留该标志位，防止等待替补期间的多回合重复结算。
