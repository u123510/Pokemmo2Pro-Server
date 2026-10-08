# 资源目录说明

## 1. 加载入口

游戏服务器由 `ScriptManager` 加载以下根目录，路径相对于项目工作目录：

```text
resource/map
resource/npc/custom
resource/story
resource/pokemon
resource/item
resource/shop
resource/gift
resource/trainer
resource/move
resource/encounter
```

## 2. 目录清单

| 目录 | 当前规模 | 内容 |
| --- | ---: | --- |
| `resource/map` | 包含 Kanto、Hoenn、Sinnoh 与一之岛地图资源 | GBA/NDS 地图、matrix terrain、地图连接、NPC、坐标、地图脚本入口，以及地图遭遇表 |
| `resource/npc/custom` | 独立自定义 NPC 目录，初始仅有 README | 每 NPC 一个 JSONC，按地区/地图分类；固定序号、出生位置、外观、移动范围与启用开关 |
| `resource/story` | 6 个 JSONC + README | 关都开场、包裹、捕获教学、护士、早期主线和冠军前主线；旧 interact/event 的 39 个剧情文件已删除 |
| `resource/pokemon` | 2 个 JSONC + gen6~gen9 目录 | Pokemon.jsonc 主图鉴（1-667 与自定义 10000-1052）、CaptureSpecies.jsonc 捕获率；gen6/7/8/9 子目录下每个 *.json 为一只宝可梦（含 2026-09 批量补入的官方 668-1025 与自定义 10000 起） |
| `resource/item` | 6 个文件 | 道具配置、球种捕获概率、道具数据和原版 Kanto 拾取目录 |
| `resource/shop` | 2 个示例店铺 JSONC，可递归扩展 | 按地区/城市分目录，每店独立 NPC 绑定、商品、买入价、回收价与买卖开关 |
| `resource/gift` | 1 个 JSONC | 礼物宝可梦配置 |
| `resource/trainer` | 2 个 JSONC | 基础训练家队伍和由 FireRed 原版脚本生成的 Kanto 训练家/地图绑定 |
| `resource/move` | 1 个文件 | 技能数据 |
| `resource/encounter` | 1 个物种常量文件 + 87 个未匹配地图文件 | FireRed 物种常量，以及暂时没有本地地图目录的旧 FireRed 拆分遭遇文件；关都、丰缘和神奥的已导入记录位于各自地图 JSON。 |
| `resource/mmo64` | 1 个二进制文件 | 客户端/ROM 相关数据 |

`tools/generate_openmmo_maps.py` 读取 `pret/pokefirered` 和 `pret/pokeemerald`
的布局、地图块、边界块、tileset 属性和 NPC 数据，输出 Kanto/Hoenn 的
openmmo 地图 JSON。旧的 JSONC/BIN 地图资源不再作为运行时输入。

`tools/generate_sinnoh_maps.py` 读取 `OpenMMO-DS` 地图生成器的 Sinnoh 输出，
导入 593 个 NDS map header、matrix terrain、terrain chunks、NDS tile behavior、
陆地/水面遭遇表、传送点和 NPC。
Sinnoh 地图通过地区号 `3` 的 NDS 地图包加载，不使用 GBA 的宽高、tileset 或 border
字段。


神奥生成源来自 `pret/pokeplatinum`，参考解析逻辑为
`AKCore/OpenMMO-DS` 的 `PlatinumNdsParser`：地图头从 `generated/map_headers.txt`
读取，matrix 从 `res/field/matrices` 读取，terrain chunk 从 `res/field/maps/data`
读取，事件和 NPC 来自对应的 NDS events archive。

地图加载器还接受 `openmmo老外` 风格的地图 JSON：`blockData` 和
`behaviorData` 使用 Base64 保存 GBA 地图块与归一化行为，`npcs`、`connections`
和 `warps` 直接作为地图定义的一部分。事件默认关闭，NPC 默认保留；开关见
[`MODULE_SERVER_GAME.md`](MODULE_SERVER_GAME.md)。

GBA 地图支持可选 `interactionCounters: [{x, y}]`，只给服务端补充明确的柜台坐标，
不改变 Base64 地图块与碰撞位。当前常磐市商店标记 `(3,3)`；加载器校验缺失、重复、
越界和与已有非普通行为冲突的标记。地图重新生成时需要保留该服务端扩展字段。

`resource/shop` 在地图和 `Item.bin` 加载后扫描，不使用旧的单文件商店目录。
自定义 NPC 在原地图之后、商店之前恢复；`//spawnnpc` 先保存到独立 `resource/npc/custom`，
再更新在线地图，不修改 `resource/map` 中的固有 NPC。格式与备份见
[`resource/npc/custom/README.md`](../resource/npc/custom/README.md)。
`//eventdeletenpc` 只将实际管理的自定义 NPC 文件原子更新为 `enabled=false`，
并移除在线实体，不物理删文件，不回收固定序号，不修改原生地图。
事件工具的无条件手动新建使用版本 2，新增 `eventId`、`sparkles` 和 `spriteScale`，
仍写到相同独立目录。版本 1 普通文件保持兼容；分类仅用于保存编辑器选项，不启用节日调度。
每个文件用 `shopId` 标识，推荐直接填写 `npcs: [{map, entityIdx}]` 指定店员；
省略 `npcs` 才沿用地图 NPC 的 `shopId`；`npcs: []` 解除本店全部绑定。
买入和回收价来自 JSON，不改 `Item.bin`。
当前示例包含常磐市和尼比市店员；旧客户端只读，适配扩展后支持买卖。
配置热重载使用 [`//reloadshops`](CHAT_RELOADSHOPS_COMMAND.md)，商品、价格和店铺内 NPC 绑定一起生效，
不回写地图。`//spawnnpc` 新建的自定义 NPC 即时生效并保存；手工更改出生文件需要重启，
原地图的坐标、外观、柜台变更仍需地图资源重载。
详见 [`NPC_SHOP_PACKET.md`](NPC_SHOP_PACKET.md)。

根目录还存在 `item.json`、`item_1.json`、`move.json` 等大体积数据文件，使用前应确认它们是否仍是运行时输入，避免修改了未加载的副本。

`resource/encounter` 与地图目录中的拆分遭遇文件，其来源、校验哈希、FireRed/LeafGreen 双版本记录和当前触发边界见 [`WILD_ENCOUNTERS.md`](WILD_ENCOUNTERS.md)。

## 3. JSONC 约定

剧情按章节拆文件：`kanto/pallet_town/opening.jsonc` 管开场，`kanto/oak_parcel/chapter.jsonc`
管包裹/图鉴，`kanto/viridian_city/catch_tutorial/chapter.jsonc` 管捕获教学，
`kanto/pokemon_center_nurse/chapter.jsonc` 管护士治疗。
`kanto/early_route_to_vermilion/chapter.jsonc` 管尼比至枯叶市的早期主线。
玩家检查点和物品保存在 PostgreSQL，不回写章节配置；五个章节共用
`StoryCatalog`、`StoryRuntime` 和原生交互会话。
配置重启读取，详见 [章节目录](../resource/story/README.md)、[包裹章](OAK_PARCEL_STORY.md)
和 [捕获教学](VIRIDIAN_CATCH_TUTORIAL.md)。

JSONC 文件通常包含注释，不能简单当作严格 JSON 处理。新增字段必须同步：

1. JSON 配置类；
2. `ScriptManager` 或对应 manager 的转换方法；
3. 运行时领域对象；
4. 相关文档和资源示例。

`resource/pokemon/CaptureSpecies.jsonc` 提供第五世代种族捕获率和基础亲密度。

`PokemonManager` 在加载主图鉴 `Pokemon.jsonc` 后，会递归扫描 `resource/pokemon`
下的自定义宝可梦目录：每个 `*.json` 文件对应一只宝可梦（内容为单个宝可梦对象，
字段结构与 Pokemon.jsonc 数组内条目一致，不带 `pokemonInfos` 包装），按世代放子
文件夹（如 `gen7/炽焰咆哮虎.json`），文件名建议使用宝可梦中文名。相同 id 会覆盖
主图鉴条目；目录不存在时跳过；单个文件解析失败只记错误日志，不影响其他文件。
新增宝可梦只往子目录加文件，不改主图鉴。
`resource/item/CaptureBall.jsonc` 只提供可选的通用球种倍率覆盖；条件球效果由
`Gen5CaptureCalculator` 根据第五世代 `1/4096` 定点公式和战斗状态计算，不能再配置最终成功百分比。

## 4. 地区结构

地图、事件和交互按 `kanto`、`hoenn`、`sinnoh`、`unova` 等地区分目录；`public` 子目录存放跨地区资源。新增地区内容时优先沿用现有目录命名和资源引用方式。
