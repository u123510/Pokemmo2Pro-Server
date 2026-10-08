# 宝可梦携带道具封包说明

本文档记录客户端给宝可梦装备或卸下携带道具时发送的 C2S `0x0F` 封包。

## 0. 使用道具封包（C2S `0x26`）

客户端使用背包道具时发送固定 14 字节 payload（不含 opcode）：

| 偏移 | 长度 | 类型 | 含义 |
| ---: | ---: | --- | --- |
| `0` | `2` | `short LE` | `itemIndexId`，道具类型编号，不是 `owned_item.item_id` |
| `2` | `8` | `long LE` | 目标宝可梦 Object ID；自行车等角色道具为 `0` |
| `10` | `2` | `short LE` | 使用数量，当前必须为 `1..9999` |
| `12` | `1` | `byte` | 目标技能位置，`-1` 表示全部/不指定 |
| `13` | `1` | `byte` | `unuse` 标记，当前接受 `0` 或 `1` |

服务端处理链：

```text
C2S 0x26
  -> UseItemPacket.decode 校验长度和字段范围
  -> GameServerService.useItem
  -> 普通消耗品按角色锁定 owned_item + PARTY 宝可梦
  -> 自行车等角色系统道具只锁定角色状态
  -> 应用 Item.bin 通用效果或 ItemUse.jsonc 特殊规则
  -> 普通消耗品在事务内扣除道具并保存宝可梦
  -> 更新在线 partyPokemons
  -> 普通消耗品发送 S2C 0x16 增量刷新 + S2C 0x40 背包刷新
  -> 自行车切换角色 transportation 并发送 S2C 0x28
```

当前通用效果包括 HP 恢复、复活、状态解除、PP 恢复、经验、等级和亲密度。`Item.bin` 中带有
`itemParticleEffectType` 的质子道具（当前实际质子类型为 `6..38`）会追加到目标宝可梦的
`particle_effects`，重复使用同一质子时拒绝请求并保留背包道具；成功后通过 `0x16` 的粒子列表位同步。
自行车由
`resource/item/ItemUse.jsonc` 配置。自行车是角色系统道具：不要求主背包存在 `owned_item`，也不扣除
道具，只切换 `character.transportation`；仍会校验角色存在及配置的地区限制。未确认的技能教学等效果
不会静默扣道具，需增加独立规则/handler。
`itemIndexId=1476` 在 `Item.bin` 中是 `NOT_USABLE` 且 `canUseOnPokemon=false` 的携带道具，
因此使用 `0x26` 时会被拒绝；装备它应使用本文后面的 C2S `0x0F`。

## 1. 封包格式

除 opcode 外，payload 固定为 11 字节，字段均为小端序：

| 偏移 | 长度 | 类型 | 含义 |
| ---: | ---: | --- | --- |
| `0` | `8` | `long LE` | 宝可梦 Object ID（`pokemon.id`） |
| `8` | `2` | `short LE` | 携带道具索引（`pokemon.item`）；`-1` 是服务端和客户端的规范无道具值，`0` 仅兼容旧客户端请求 |
| `10` | `1` | `byte` | 宝可梦容器：`0 = PC`、`1 = PARTY` |

示例日志：

```text
0f 01 00 00 00 00 00 00 00 c4 05 01
```

解析结果：

```text
opcode       = 0x0F
pokemonId    = 1
itemIndexId  = 1476
containerId  = 1 (PARTY)
```

客户端 `f.SF0` 的写入顺序为 `long 宝可梦 Object ID`、`short 道具索引`、`byte 容器类型`。
道具索引不是 `owned_item.item_id`；服务端使用当前角色主背包中同一
`item_index_id` 的一件道具完成装备。

## 2. 服务端处理

```text
C2S 0x0F
  -> UpdatePokemonItemPacket.decode
  -> 校验 11 字节、Object ID、道具索引和 PC/PARTY 容器
  -> PokemonService.updatePokemonItem
  -> trainer_id + pokemon.id + container_id 锁定宝可梦
  -> 校验 Item.bin 的 itemCanGiveToPokemon 和主背包数量
  -> 同一事务扣除新道具、返还旧携带道具并更新 pokemon.item
  -> S2C 0x16 bit 256 更新宝可梦道具
  -> S2C 0x40 刷新主背包
```

装备道具时扣除主背包一件；替换或卸下时，旧携带道具以普通无限制堆叠返还主背包。
交易期间、已报价宝可梦、非本角色宝可梦、容器不匹配、未知/不可携带道具和数量不足
都会被拒绝，不会修改数据库。

数据库中的 `pokemon.item <= 0` 都表示未携带道具。服务端读取记录时会把历史值 `0` 规范化为 `-1`，
初始化 schema 的默认值也为 `-1`。已有数据库可手动执行
[`db/migrate_pokemon_empty_item.sql`](../db/migrate_pokemon_empty_item.sql)；该脚本不会修改正数道具索引。

## 3. 当前验证边界

- 已用客户端 `f.SF0` 源码确认字段顺序和 `0x0F` opcode。
- 已用抓包 `0f 01 00 00 00 00 00 00 00 c4 05 01` 对上 `pokemonId=1`、`itemIndexId=1476`、`PARTY`。
- 本次未按项目约定启动 Gradle、服务器或数据库；需要用户自行运行后验证客户端装备、替换和卸下流程。
