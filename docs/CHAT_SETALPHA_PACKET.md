# `//setalpha` 聊天命令数据包说明

本文档记录客户端通过游戏服聊天包发送 `//setalpha` 命令时的抓包结果，以及服务端后续处理该命令时需要遵循的字段含义。

## 1. 功能

命令用于修改当前角色队伍中某一只宝可梦的头目（Alpha）状态：

```text
//setalpha <队伍位置> <状态>
```

示例：

```text
//setalpha 0 true
//setalpha 0 false
```

含义：

| 命令 | 作用 |
| --- | --- |
| `//setalpha 0 true` | 将队伍位置 `0` 的宝可梦设置为头目 |
| `//setalpha 0 false` | 将队伍位置 `0` 的宝可梦设置为非头目 |

队伍位置使用当前项目中的 `container_position`。队伍数组容量为 6，因此正常范围预计为 `0` 到 `5`；服务端实现时必须检查位置是否越界以及该位置是否有宝可梦。

## 2. 抓包原文

测试时间：`02:28:51.523`

聊天类型：`NORMAL`

输入内容：`//setalpha 0 true`

客户端到游戏服的 Netty 抓包：

```text
READ: 38B

00000000  08 00 2f 00 2f 00 73 00 65 00 74 00 61 00 6c 00
00000010  70 00 68 00 61 00 20 00 30 00 20 00 74 00 72 00
00000020  75 00 65 00 00 00
```

其中日志中的 `READ COMPLETE` 表示该包已经完整读取，不是另一个业务包。

## 3. 字节布局

| 偏移 | 长度 | 字段 | 值 | 说明 |
| ---: | ---: | --- | --- | --- |
| `0x00` | 1 | 游戏包编号 | `0x08` | 客户端到服务端的聊天包；由 `GameProtocol` 注册为 `ChatPacket` |
| `0x01` | 1 | 聊天类型 | `0x00` | `ChatType.NORMAL` |
| `0x02` | 34 | 聊天内容 | UTF-16LE | `//setalpha 0 true`，共 17 个字符 |
| `0x24` | 2 | 字符串结束符 | `00 00` | UTF-16LE 空字符，由 `readUtf16LE()` 读取并消费 |

总长度为：

```text
1 + 1 + (17 * 2) + 2 = 38 字节
```

### UTF-16LE 内容拆分

```text
2f 00  -> /
2f 00  -> /
73 00  -> s
65 00  -> e
74 00  -> t
61 00  -> a
6c 00  -> l
70 00  -> p
68 00  -> h
61 00  -> a
20 00  -> 空格
30 00  -> 0
20 00  -> 空格
74 00  -> t
72 00  -> r
75 00  -> u
65 00  -> e
00 00   -> 字符串结束
```

## 4. 当前代码对应关系

当前协议注册位置：

```java
registerPacket(DataFlow.CLIENT_TO_SERVER, (byte) 0x08, ChatPacket.class);
```

`ChatPacket.decode()` 的读取顺序为：

```java
chatType = ChatType.getByType(buffer.readByte());
message = buffer.readUtf16LE();
```

协议层已经消费了包编号 `0x08` 后，`ChatPacket` 实际读取的内容就是：

```text
00                         -> ChatType.NORMAL
2f 00 ... 65 00 00 00      -> //setalpha 0 true
```

`readUtf16LE()` 会每次读取两个字节，直到读取到 `00 00`。因此聊天消息不是 UTF-8，也没有单独的字符串长度字段。

## 5. 服务端处理流程

当前服务端已经通过通用命令框架处理该命令。`ChatPacket` 只负责收包，命令由 `CommandDispatcher` 分发到 `SetAlphaCommand`：

```text
收到 ChatPacket
  |
  |-- chatType == NORMAL 且 message 以 "//" 开头
  |       |
  |       |-- 解析命令名、队伍位置和布尔值
  |       |-- 校验执行权限
  |       |-- 查找 partyPokemons[position]
  |       |-- 修改 PokemonData.isAlpha
  |       |-- 持久化 public.pokemon.is_alpha
  |       |-- 向客户端发送宝可梦稀有度刷新包
  |       `-- 不推送到公共聊天频道
  |
  `-- 普通消息：继续推入 Redis 聊天队列
```

当前校验规则：

1. 命令名为 `setalpha`，参数必须为位置和状态两个参数。
2. 位置必须是整数，并且范围为 `0..5`。
3. 状态只接受 `true` 或 `false`，建议不区分大小写。
4. 目标位置不能为空。
5. 只允许 `GM` 及以上权限执行。
6. 解析失败时不要抛出未处理异常，也不要把管理命令广播给其他玩家。

## 6. 头目状态字段与客户端刷新

项目中已经存在头目状态字段：

| 层级 | 字段 | 位置 |
| --- | --- | --- |
| 数据库 | `pokemon.is_alpha BOOLEAN NOT NULL DEFAULT FALSE` | `db/schemas/init.sql` |
| jOOQ | `PokemonRecord.getIsAlpha()` / `setIsAlpha(Boolean)` | `db/src/main/java/.../PokemonRecord.java` |
| 内存对象 | `PokemonData.isAlpha` | `server.game/.../game/pokemon/PokemonData.java` |
| 战斗/显示编码 | `PokemonRarity.ALPHA` 位 | `UpdatePokemonDataCodec` |

修改后客户端需要刷新宝可梦稀有度。项目现有更新结构支持：

```java
new UpdatePokemonData.Builder()
    .setUpdatePokemon(targetPokemon)
    .setIsReloadPokemonRarity(true)
    .build();
```

`UpdatePokemonDataCodec` 在 `isReloadPokemonRarity` 为 `true` 时，会根据以下状态重新组装稀有度位：

```text
SHINY
HIDDEN_ABILITY
ALPHA
SECRET
```

因此 `isAlpha` 修改后必须同时发送 `SendUpdatePokemonDataPacket`，否则数据库和服务端内存虽然已经改变，客户端界面可能不会立即更新。

## 7. 需要注意的实现差异

- 抓包只能确认这是一个聊天字符串命令，`0` 和 `true` 是命令参数，不是独立的二进制字段。
- `0` 应解释为队伍位置时，需要按 `PokemonData.containerPosition` 查找；不要把它误认为宝可梦数据库 ID。
- `ChatPacket` 会把 `NORMAL` 类型的 `//` 消息交给 `CommandDispatcher`；已知命令和未知命令都会被拦截，不会进入 Redis 普通聊天队列。
- 具体命令实现位于 `server.game/src/main/java/org/pokemmo/gameserver/command/commands/SetAlphaCommand.java`。
- 命令框架的整体设计记录在 `docs/COMMAND_FRAMEWORK.md`。
- 修改持久化记录时应使用目标宝可梦的数据库 ID，而不是队伍位置。
- `true` 表示 `is_alpha = true`，`false` 表示 `is_alpha = false`；这与 `PokemonRarity.ALPHA` 的显示位保持一致。

## 8. 验证清单

- [ ] 输入 `//setalpha 0 true` 后，位置 `0` 宝可梦的 `isAlpha()` 为 `true`。
- [ ] 输入 `//setalpha 0 false` 后，位置 `0` 宝可梦的 `isAlpha()` 为 `false`。
- [ ] 重载角色或重启游戏服后，状态仍从 `pokemon.is_alpha` 正确读取。
- [ ] 客户端收到稀有度刷新包并显示/取消头目标记。
- [ ] 空位置、越界位置、非法布尔值和无权限账号不会修改数据。
- [ ] `//setalpha` 命令不会出现在普通聊天广播中。

## 9. 相关闪光命令

命令框架还支持按角色名和队伍位置修改闪光状态：

```text
//setshiny <character name> <party slot 0-5> <true|false>
//setsecretshiny <character name> <party slot 0-5> <true|false>
```

例如：

```text
//setshiny LM 0 false
//setsecretshiny LM 0 false
```

其中 `LM` 按目标角色名处理，`0` 是该角色队伍位置，最后一个参数控制开启或关闭对应状态。具体实现位于 `command/commands/SetShinyCommand.java` 和 `SetSecretShinyCommand.java`。
