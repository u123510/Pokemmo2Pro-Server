# `//addmonster` 聊天命令说明

本文档记录 `NORMAL` 聊天中的 `//addmonster` 命令，以及服务端生成和保存新宝可梦的处理流程。

## 1. 命令格式

```text
//addmonster <目标角色名> <宝可梦编号> <等级>
```

示例：

```text
//addmonster LM 6 20
```

参数含义：

| 参数 | 示例 | 说明 |
| --- | --- | --- |
| 目标角色名 | `LM` | 要接收宝可梦的角色名 |
| 宝可梦编号 | `6` | `resource/pokemon/Pokemon.jsonc` 中的 `id`，不是数据库主键 |
| 等级 | `20` | 新宝可梦等级，服务端限制为 `1..100` |

命令默认需要 `GM` 及以上权限。

## 2. 聊天包

命令使用现有 `ChatPacket`，聊天类型为 `ChatType.NORMAL`，内容为 UTF-16LE 字符串：

```text
ChatType.NORMAL
//addmonster LM 6 20
```

字符串以 `00 00` 结束。`ChatPacket` 不解析具体命令，而是将 `//` 消息交给 `CommandDispatcher`；命令不会进入普通聊天广播。

## 3. 处理流程

```text
收到 NORMAL ChatPacket
  |
  |-- 解析目标角色名、宝可梦编号和等级
  |-- 查找 Pokemon.jsonc 中的宝可梦资料
  |-- 查找目标角色队伍空位
  |       |-- 有空位：放入 PARTY
  |       `-- 队伍已满：寻找 PC 空位
  |-- 生成 personality、个体值、技能、经验和捕获信息
  |-- 写入 pokemon 数据表
  |-- 目标在线：更新目标内存队伍并发送 SendAddPokemonPacket
  `-- 回复执行结果
```

具体实现位置：

```text
server.game/src/main/java/org/pokemmo/gameserver/command/commands/AddMonsterCommand.java
server.game/src/main/java/org/pokemmo/gameserver/game/pokemon/PokemonManager.java
server.game/src/main/java/org/pokemmo/gameserver/game/pokemon/PokemonData.java
```

## 4. 生成规则

新宝可梦使用野生宝可梦模板：

- 个体值随机生成，范围为 `0..30`；
- 根据等级获取当前可学习的最多 4 个技能，并计算技能 PP；
- 随机生成 personality value，并根据 `% 25` 设置性格；
- 当前生命值按物种基础能力、个体值、等级和性格计算；
- 原训练家和 OT 名称设置为目标角色；
- 捕获等级使用命令等级，捕获地区使用目标角色地区；
- 使用普通精灵球，闪光和秘密闪光仍按野生生成概率随机；
- 新记录 ID 使用 `SnowflakeIdGenerator` 生成。

`PokemonData.toPokemonRecord()` 负责把内存对象转换成 jOOQ 的 `PokemonRecord`，由 `GameServerService.addPokemon(...)` 持久化。

## 5. 队伍和 PC 位置

队伍位置按 `0..5` 查找第一个未使用位置。队伍没有空位时，使用现有 PC 容量和扩展数量检查逻辑寻找 PC 位置。没有可用队伍或 PC 位置时，命令不会生成或写入宝可梦。

目标在线时：

1. 更新目标 `CharacterManager.partyPokemons`（仅当放入队伍）；
2. 向目标会话发送 `SendAddPokemonPacket`；
3. 命令反馈仍发送给执行命令的 GM。

目标离线时只更新数据库，目标下次登录时由角色加载流程读取。

## 6. 校验清单

- [ ] `//addmonster LM 6 20` 给角色 `LM` 添加 6 号宝可梦，等级为 20。
- [ ] 不存在的角色名不会写入数据库。
- [ ] 不存在的宝可梦编号不会写入数据库。
- [ ] 等级小于 1 或大于 100 时拒绝执行。
- [ ] 队伍有空位时放入 PARTY，队伍满时放入 PC。
- [ ] PARTY 和 PC 都满时拒绝执行。
- [ ] 目标在线时内存和客户端同步更新。
- [ ] 命令不会出现在普通聊天广播中。

