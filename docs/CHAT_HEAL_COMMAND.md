# `//heal` 管理员队伍恢复

## 用法

```text
//heal
```

无参数。命令默认要求 **GM 及以上权限（权限值 >= 7）**，普通玩家和 STAFF、CM、MOD
都会被命令分发器拒绝。输入参数时只返回中文用法提示，不会执行恢复。

## 恢复范围

- 只处理当前角色的 PARTY 队伍，不处理 PC、交易容器、邮件或其他角色。
- 每只非蛋宝可梦的当前 HP 恢复到按当前等级、个体值、努力值和性格计算的最大 HP。
- 每个已学习招式的 PP 恢复到包含 PP Up 次数后的最大值；空招式槽保持 `0`。
- 不修改等级、经验、PP Up、状态异常、携带道具、技能、属性、亲密度或其他字段。
- 队伍为空或队伍中只有蛋时返回中文失败提示。

## 状态同步

`PokemonHealingService` 先按 `trainer_id + container_id = PARTY` 锁定数据库中的整支队伍，
确认在线队伍的 Object ID、槽位、训练家、等级、个体值、努力值、招式和 PP Up 数据仍一致，
再在一个事务中更新 `current_hp` 与 `moves_pp`。任意保存失败都会整体回滚，不提前修改在线内存。

事务成功后，`HealCommand` 更新当前角色的在线 PARTY 对象，并为每只恢复的宝可梦发送已有
S2C `0x16` 增量包，刷新 HP 和四个招式 PP。不会新增 opcode，也不需要修改客户端。

战斗、观战、交易、场景交互或地图仍未加载完成时拒绝执行；数据库保存成功但会话刚好失效时，
服务端保留数据库结果并记录中文日志，角色重新登录后会从数据库得到正确状态。

## 文件

- `org.pokemmo.gameserver.command.commands.HealCommand`：无参数命令、权限/状态校验、在线刷新和中文反馈。
- `org.pokemmo.gameserver.services.pokemon.PokemonHealingService`：队伍一致性检查、最大 HP/PP 计算和事务持久化。
- `org.pokemmo.gameserver.services.GameServerService`：兼容门面转发队伍恢复服务。
- `org.pokemmo.gameserver.command.GameCommandModule`：注册 `heal` 命令。

2026-09-09：完成源码、数据库字段、在线对象和现有 `0x16` 刷新路径静态核对；按项目约定未编译、
未运行 JUnit、未启动服务器。实机需使用 GM 角色输入 `//heal`，再确认 PARTY 面板的 HP 和 PP 已更新，
并确认普通权限角色收到权限不足提示。
