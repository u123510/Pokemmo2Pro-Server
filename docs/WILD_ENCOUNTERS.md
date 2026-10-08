# 地区野外遭遇表

## 1. 原始资源

`resource/encounter` 保存 FireRed 物种常量和旧的未匹配地图文件；关都、丰缘的 GBA 遭遇数据分别来自 `pret/pokefirered`、`pret/pokeemerald`，神奥遭遇数据来自 `pret/pokeplatinum` 并通过 OpenMMO-DS 解析器生成。运行时地图 JSON 保留对应表结构：

| 本地文件 | 上游路径 | SHA-256 |
| --- | --- | --- |
| 拆分前的上游 `wild_encounters.json` | `src/data/wild_encounters.json`（拆分来源） | `a6399199987e1bdf8502e9bfdcfc9d8ad57d74df206d22ba20f020492bd3bf7c` |
| `resource/encounter/species.h` | `include/constants/species.h` | `ec3f9f9c2717dcd453aa7a27506c32e1e6ca067864534dee494695d74344c51a` |

上游表包含 `wild_encounter_groups -> fields -> encounters` 层级、`encounter_rates`、钓鱼 `groups`、`MAP_*` 地图键、物种常量和等级范围。拆分后的每个 `Wild_Encounters.jsonc` 仍使用同一层级，只包含该地图的记录；`resource/encounter/unmapped/MAP_*.jsonc` 保存当前项目尚未提供地图文件的记录。全部拆分文件合计 124 个地图文件、264 条双版本记录，其中 132 条是 FireRed，132 条是 LeafGreen。

## 2. 运行时规则

`ScriptManager` 在游戏服务器启动时将 `resource/encounter` 传给 `WildEncounterManager`。管理器扫描地图 JSON 和 `resource/encounter/unmapped/*.jsonc`；关都只接受 `_FireRed`，丰缘接受 Emerald 地图表，神奥接受 OpenMMO-DS 生成的数字物种 ID。物种常量和数字 ID 都会转换为本项目图鉴 ID。

地图 JSON 文件名会成为 `MapData.mapKey`。运行时会忽略大小写、下划线和 `MAP_` 前缀进行匹配；`Cinnabar_City` 兼容上游的 `MAP_CINNABAR_ISLAND`。当前已导入 116 张丰缘地图和 154 张神奥地图的固定遭遇表。神奥地图还保存 256 项 NDS tile behavior，用于识别草地和冲浪水面。时间、雷达、群聚、双槽和鱼竿表会保留基础数据，但暂不由普通移动触发。

陆地、水面、碎岩和钓鱼表均会读取原始槽位权重。钓鱼额外按 `old_rod`、`good_rod`、`super_rod` 的原始槽位分组查询；该查询 API 已就绪，但本项目尚未确认钓鱼和碎岩的客户端请求链路，因此当前实际移动触发范围是草丛和冲浪水面。

## 3. 移动触发

成功移动后，`MovePacket` 先执行传送和剧情/坐标事件；没有触发事件时，`WildEncounterService` 才检查目标格。它拒绝隐身、战斗、交互或交易中的角色，在关都/丰缘的草地、地下地图可行走地面或冲浪水面，以及神奥 NDS tile behavior 标记的草地/冲浪水面触发。隐身期间会清空遭遇累计状态，恢复后不会沿用隐身前的遭遇步数。选出的野生宝可梦使用非持久化 `PokemonContainerType.EVENT`，通过 `BattleGenerator.generatorWildBattle` 进入既有 `WildBattle`，不会在遭遇开始时写入 PARTY、PC 或数据库。

当前为临时测试概率：每次野生遭遇必定生成闪光（`1/1`，100%）；如果已经闪光，成为秘密闪光的概率为 `1/2`（50%），因此每次野生遭遇直接成为秘密闪光的总概率为 `1/2`（50%）。闪光野生精灵不会伪造一个普通可解锁粒子：服务端将战斗粒子值设为客户端保留值 `0`，客户端再根据 `SECRET` 稀有度位在普通闪光出场和秘密闪光出场之间选择。粒子 ID `4` 实际对应普通自定义粒子（客户端 `spawn_ghost` 路径），不能当作闪光特效。恢复正式概率时，将 `PokemonManager` 中的两个临时常量改回 `50000` 和 `10`。

## 4. 静态验证

- 拆分前的上游 `wild_encounters.json` SHA-256 为 `a6399199987e1bdf8502e9bfdcfc9d8ad57d74df206d22ba20f020492bd3bf7c`，本地 `species.h` SHA-256 与上游对应文件一致。
- JSON 可解析，表中物种常量均可在 `species.h` 解析。
- 槽位等级范围满足 `min_level >= 1` 且 `max_level >= min_level`。
- 已完成拆分文件的 JSON 解析、源记录逐条比对和目录匹配静态检查；Gradle 编译和服务器启动由当前环境中的 JDK 25/Kotlin DSL 兼容性问题阻止，客户端钓鱼/碎岩封包尚未抓包验证。
