# 宝可梦属性聊天命令说明

本文档记录通过 `NORMAL` 聊天执行的个体值、隐藏特性和特性槽修改命令。

命令默认需要 `GM` 及以上权限，命令消息不会进入普通聊天广播。

## 1. 设置个体值

```text
//setivs <party slot 0-5> <HP> <攻击> <防御> <特攻> <特防> <速度>
```

示例：

```text
//setivs 2 31 31 31 31 31 31
```

示例表示把当前角色队伍第 `2` 个位置的宝可梦六项个体值全部设置为 `31`。

参数顺序严格为：

```text
HP、攻击、防御、特攻、特防、速度
```

每项个体值限制为 `0..31`。服务端写入 `pokemon.iv_values`，同时更新在线角色内存，并发送个体值和能力值刷新包。

## 2. 设置隐藏特性

```text
//setha <party slot 0-5> <true|false>
```

示例：

```text
//setha 2 true
```

其中 `true` 表示开启隐藏特性标志，也就是梦特；`false` 表示关闭隐藏特性标志。

服务端写入 `pokemon.has_hidden_ability`，更新在线内存，并通过稀有度刷新字段同步客户端。

## 3. 设置努力值

```text
//setevs <party slot 0-5> <HP|ATTACK|DEFENSE|SPEED|SP_ATTACK|SP_DEFENSE> <0-252>
```

示例：

```text
//setevs 3 ATTACK 252
```

表示把当前角色队伍第 `3` 个位置的宝可梦攻击努力值设置为 `252`。支持的属性名称为：

```text
HP、ATTACK、DEFENSE、SPEED、SP_ATTACK、SP_DEFENSE
```

其中 `SP_ATTACK` 是特攻，`SP_DEFENSE` 是特防；也兼容写成 `SPECIAL_ATTACK` 和 `SPECIAL_DEFENSE`。单项努力值限制为 `0..252`，修改后会同步能力值和 EV 数据。

## 4. 设置亲密度

```text
//sethappiness <party slot 0-5> <0-255>
```

示例：

```text
//sethappiness 3 255
```

表示把当前角色队伍第 `3` 个位置的宝可梦亲密度设置为 `255`。服务端写入 `pokemon.friend_value` 并同步客户端。

## 5. 设置特性槽

```text
//setability <character name> <party slot 0-5> <ability slot>
```

示例：

```text
//setability LM 3 1
```

参数含义：

| 参数 | 示例 | 说明 |
| --- | --- | --- |
| 角色名 | `LM` | 目标角色名 |
| 队伍位置 | `3` | 目标宝可梦队伍位置 |
| 特性槽 | `1` | `Pokemon.jsonc` 中该宝可梦 `abilities` 数组的索引 |

特性槽从 `0` 开始，服务端会根据目标宝可梦的资料校验索引范围，并写入 `pokemon.ability`。目标在线时同步内存和客户端，离线时只更新数据库。

## 6. `0x16` 更新中的 OT 保留

客户端 `f.OM` 会在处理任意宝可梦 `0x16` 增量更新后，无条件把包对象中的临时 OT 写回宝可梦。bit `32768` 不存在时临时 OT 保持默认空字符串，因此只刷新 IV、EV、特性、亲密度或稀有度也会意外清空原训练家显示。

`UpdatePokemonDataCodec` 统一为所有 `0x16` 更新附加 bit `32768`、四个可回忆技能槽和当前 OT。命令不需要各自重复设置该 flag；编码时如果技能槽不是四项或 OT 为 `null`，会拒绝发送无效封包。

## 7. 实现位置

```text
server.game/src/main/java/org/pokemmo/gameserver/command/commands/SetIvsCommand.java
server.game/src/main/java/org/pokemmo/gameserver/command/commands/SetEvsCommand.java
server.game/src/main/java/org/pokemmo/gameserver/command/commands/SetHappinessCommand.java
server.game/src/main/java/org/pokemmo/gameserver/command/commands/SetHiddenAbilityCommand.java
server.game/src/main/java/org/pokemmo/gameserver/command/commands/SetAbilityCommand.java
server.game/src/main/java/org/pokemmo/gameserver/codecs/UpdatePokemonDataCodec.java
server.game/src/main/java/org/pokemmo/gameserver/services/GameServerService.java
```
