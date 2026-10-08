# `//setgender` 聊天命令说明

本文档记录 `NORMAL` 聊天中的 `//setgender` 命令，以及服务端如何把它转换为宝可梦性别状态。

## 1. 命令格式

```text
//setgender <队伍位置 0-5> <性别>
```

性别参数约定：

| 参数 | 含义 |
| ---: | --- |
| `0` | 公 |
| `1` | 母 |

示例：

```text
//setgender 0 0
//setgender 0 1
```

命令只操作当前执行者的队伍。默认权限为 `GM` 及以上。

## 2. 聊天包格式

命令通过现有聊天包发送，不是新增独立的二进制包。客户端使用：

```text
ChatType.NORMAL
//setgender 0 0
```

聊天内容使用 UTF-16LE，并以 `00 00` 结束。以 `//setgender 0 0` 为例，字符串有 15 个字符，因此包含包编号时长度为：

```text
1       包编号
+ 1     ChatType.NORMAL
+ 15*2  UTF-16LE 内容
+ 2     UTF-16LE 结束符
= 34 字节
```

`ChatPacket` 读取聊天类型和字符串后，将 `//` 消息交给通用 `CommandDispatcher`，不会把命令推送到普通聊天队列。

## 3. 服务端处理流程

```text
收到 NORMAL ChatPacket
  |
  |-- CommandParser 解析 setgender、队伍位置和性别参数
  |-- CommandDispatcher 校验角色状态和 GM 权限
  |-- 读取当前角色 partyPokemons[队伍位置]
  |-- 根据 gender_ratio 计算新的 personality_value
  |-- 更新数据库 pokemon.personality_value
  |-- 更新内存 PokemonData.personalityValue 和 natureType
  |-- 发送 SendUpdatePokemonDataPacket
  `-- 回复命令执行结果
```

具体实现位于：

```text
server.game/src/main/java/org/pokemmo/gameserver/command/commands/SetGenderCommand.java
server.game/src/main/java/org/pokemmo/gameserver/game/pokemon/PokemonGenderUtil.java
```

数据库更新由 `GameServerService.updatePokemonPersonalityValue(...)` 完成，并使用宝可梦数据库 ID 和训练家 ID 作为条件，避免把队伍位置误当成数据库主键。

## 4. personality_value 与性别

项目当前的性别判定位于 `PokemonData.getPokemonSex()`：

```java
(personalityValue & 255) >= genderRatio ? 0 : 1
```

因此服务端设置时使用以下低字节：

```text
公（0）: gender_ratio
母（1）: gender_ratio - 1
```

母性别使用严格小于 `gender_ratio` 的值，避免客户端把刚好等于比例边界的低字节判为公。`gender_ratio == 0` 的宝可梦只有公性别，不能设置为母。

修改时只改变满足性别判定所需的 personality 值，并通过 `256` 的步进寻找与原值相同的 `personality_value % 25`，从而保持性格类型不变。由于 Java 的 `%` 对负整数使用有符号余数，内存对象刷新时使用 `Math.floorMod(value, 25)` 计算性格索引。

### 特殊 gender_ratio

- `255`：无性别，命令拒绝执行。
- `0`：公性宝可梦，只能设置为公。
- `254`：接近母性宝可梦，仍按边界公式写入目标性别。

## 5. 客户端刷新

性别来自 `personality_value`，所以更新后发送：

```java
new UpdatePokemonData.Builder()
    .setUpdatePokemon(targetPokemon)
    .setIsReloadPokemonCatchInfo(true)
    .setIsReloadNatureType(true)
    .build();
```

`isReloadPokemonCatchInfo` 会把新的 `personality_value` 发给客户端，`isReloadNatureType` 会同步性格类型。战斗预览、战斗队伍和跟随宝可梦在后续读取 `PokemonData.getPokemonSex()` 时使用新的性别。

为兼容只在完整队伍数据重载时重新计算性别的客户端，命令执行成功后还会重新发送 PARTY 容器。这样可以同时刷新队伍界面和服务端内存中的目标位置。

注意：队伍位置仍然使用项目内部的 `0..5` 索引；因此参数 `3` 指向数组下标 `3`（通常是界面从 1 开始数时的第 4 个位置）。如果你说的“第 3 个槽位”是从 1 开始数，应输入 `//setgender 2 1`。

## 6. 校验清单

- [ ] `//setgender 0 0` 将队伍位置 `0` 设置为公。
- [ ] `//setgender 0 1` 将队伍位置 `0` 设置为母。
- [ ] 队伍位置越界、空位置和非法性别参数不会修改数据库。
- [ ] `gender_ratio == 255` 的无性别宝可梦不会被修改。
- [ ] `gender_ratio == 0` 的公性宝可梦不会被设置为母。
- [ ] 更新后客户端收到新的 `personality_value` 和性格类型。
- [ ] 角色重载或服务器重启后，性别从数据库中的 `pokemon.personality_value` 恢复。
- [ ] 命令不会进入普通聊天广播。
