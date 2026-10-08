# 性格命令说明

`setnature` 通过 `NORMAL` 聊天修改当前角色 PARTY 中宝可梦的性格。命令默认需要 `GM` 及以上权限，不会进入普通聊天广播。

## 输入格式

```text
//setnature <party slot 0-5> <包含英文性格枚举的文本>
```

第二个参数只检查是否包含英文性格枚举，不依赖中文名称，因此以下写法都有效：

```text
//setnature 1 BOLD
//setnature 1 大胆(BOLD)
```

支持全部 25 种英文性格枚举：

```text
HARDY LONELY BRAVE ADAMANT NAUGHTY
BOLD DOCILE RELAXED IMPISH LAX
TIMID HASTY SERIOUS JOLLY NAIVE
MODEST MILD QUIET BASHFUL RASH
CALM GENTLE SASSY CAREFUL QUIRKY
```

队伍位置使用服务端 `0..5` 数组下标。参数 `1` 表示数组中的第 `1` 个位置，也就是按界面从 `1` 开始计数时的第 `2` 只宝可梦。

## 状态同步

性格由 `personality_value` 对 `25` 取模决定。命令会寻找满足目标性格且保持原有性别的最近 personality 值，然后按以下顺序更新：

```text
带 trainer 条件更新 pokemon.personality_value
  -> 更新在线 PokemonData.personalityValue 和 natureType
  -> 发送 0x16 性格、能力值、捕获信息、可回忆技能和 OT 增量
  -> 发送完整 PARTY 容器刷新
```

数据库写入失败时不会更新内存或发送成功反馈。重新登录时也统一通过 floor-mod 恢复全部 25 种性格，负数 personality 值不会导致数组越界。

客户端 `f.OM` 在处理任意 `0x16` 更新后都会无条件把包内临时 OT 写回宝可梦；当 bit `32768` 未设置时，该临时值是空字符串。服务端的共享 `UpdatePokemonDataCodec` 因此会为所有 `0x16` 更新强制携带 bit `32768`、四个可回忆技能槽和当前 OT，防止修改性格后原训练家显示被清空。该行为已通过 Recaf JASM 确认。

## 实现位置

```text
server.game/src/main/java/org/pokemmo/gameserver/command/commands/SetNatureCommand.java
server.game/src/main/java/org/pokemmo/gameserver/command/GameCommandModule.java
server.game/src/main/java/org/pokemmo/gameserver/game/pokemon/PokemonNatureType.java
server.game/src/main/java/org/pokemmo/gameserver/game/pokemon/PokemonData.java
server.game/src/main/java/org/pokemmo/gameserver/codecs/UpdatePokemonDataCodec.java
server.game/src/main/java/org/pokemmo/gameserver/services/GameServerService.java
```
