# `//setform` 设置与查看宝可梦形态命令

## 用法

```text
//setform <party slot 0-5> <form 0-255>
//setform [character name] <party slot 0-5> <form 0-255>
//setform list
//setform list <party slot 0-5>
//setform list [character name]
//setform list [character name] <party slot 0-5>
```

- **别名**：`//form`
- **权限要求**：**GM（权限值 >= 7）**。普通玩家无权调用。
- **参数说明**：
  - `party slot`：队伍槽位（`0 ~ 5`），0 为首发第一位宝可梦。
  - `form`：形态 ID（`0 ~ 255`）。例如洛托姆的微波炉、洗衣机、冰箱、电风扇、除草机；阿罗拉/伽勒尔/洗翠地区形态；代欧奇希斯/骑拉帝纳/谢米等特殊形态。
  - `character name`（可选）：指定目标玩家角色名。缺省时默认为当前操作者自身。
  - `list`（子命令）：查询队伍槽位形态概览或指定槽位的可用形态变化详情。

## 示例

```text
// 查看自己队伍 6 个槽位的当前形态概览
//setform list

// 查看自己队伍槽位 1 宝可梦（如洛托姆）的全部可选形态变化
//setform list 1

// 查看玩家 Red 的队伍形态概览
//setform list Red

// 查看玩家 Red 槽位 0 的形态变化详情
//setform list Red 0

// 将自己首发槽位 0 的宝可梦形态修改为 1
//setform 0 1

// 将玩家 Red 槽位 1 的宝可梦形态修改为 2
//setform Red 1 2
```

## `list` 查询反馈示例

### 1. 队伍形态概览
```text
=== 玩家 [小智] 队伍形态概览 ===
  [槽位 0] 皮卡丘 (No.25) - 当前形态: 0 [默认形态]
  [槽位 1] 洛托姆 (No.479) - 当前形态: 1 [加热洛托姆/微波炉(电/火)]
  [槽位 2] 代欧奇希斯 (No.386) - 当前形态: 2 [防御形态]
  [槽位 3] 小拉达 (No.19) - 当前形态: 1 [阿罗拉形态(恶/一般)]
  [槽位 4] 骑拉帝纳 (No.487) - 当前形态: 0 [别种形态]
  [槽位 5] (空)
提示: 输入 //setform list <槽位 0-5> 可查看该宝可梦支持的所有形态变化。
```

### 2. 指定槽位形态变化详情
```text
=== 玩家 [小智] 槽位 1 形态变化详情 ===
宝可梦: 洛托姆 (全国编号: 479)
当前形态: 1 [加热洛托姆/微波炉(电/火)]
支持的形态变化列表:
  [0] 普通洛托姆(电/幽灵)
  [1] 加热洛托姆/微波炉(电/火) <-- 当前
  [2] 清洗洛托姆/洗衣机(电/水)
  [3] 结冰洛托姆/冰箱(电/冰)
  [4] 旋转洛托姆/电风扇(电/飞)
  [5] 切割洛托姆/除草机(电/草)
切换形态: //setform 1 <形态编号>
```

## 状态同步与多层刷新

1. **数据库持久化**：
   - 更新目标表 `public.pokemon` 的 `form_type` 列。
   - 使用 `POKEMON.ID = ? AND POKEMON.TRAINER_ID = ?` 双重条件执行更新，防止越权篡改其他玩家宝可梦。
2. **在线内存状态同步**：
   - 更新目标 `PokemonData` 对象的 `formType` 字段。
   - 同步至目标玩家 `CharacterManager` 的 `partyPokemons` 槽位数组中。
3. **客户端增量更新与全量容器刷新**：
   - **S2C 0x16 增量更新 (`SendUpdatePokemonDataPacket`)**：
     设置 `isReloadPokemonIndexId = true`，客户端收到后读取 2 字节小端 `pokemonIndexId` 与 1 字节 `formType`，触发客户端模型动态重新加载。
   - **S2C 0x14 队伍容器刷新 (`SendPokemonContainerPacket`)**：
     下发当前队伍全量容器，确保队伍栏界面与状态栏形态数据同步。
4. **大地图跟随宝可梦（Follower）同步**：
   - 若被修改的宝可梦当前正在跟随玩家（`followPokemonIndexId == targetPokemon.pokemonIndexId`），重新计算跟随稀有度标识（低 5 位 `formType & 0x1F`，并保留性别、闪光、头目标识）。
   - 写库更新玩家跟随记录，并向全视野周围玩家广播 S2C `SendSetFollowPokemonPacket`，实现大地图跟随精灵形态外观即时变化。

## 客户端协议证据

在客户端 `28887-obf-project` 的 `cn.pokemmo.net.packet.inbound.PokedexEntryUpdatePacket`（即原始混淆类 `f.OM`，操作码 `0x16`）中：
- 掩码标志 `32`（`0x20`，对应服务端的 `isReloadPokemonIndexId`）下：
  ```java
  this.Ec = this.Rj.getShort(); // 2 字节小端 pokemonIndexId
  this.Xg = this.Rj.get();      // 1 字节 formType
  ```
- 随后的逻辑直接调用 `object.aG(...)` 重新装载物种与对应形态资源。
- 服务端 `UpdatePokemonDataCodec` 原先写入 `pokemonIndexId` 为 `buffer.writeByte`，本次已修正为 `buffer.writeShortLE`，避免网络流错位。
