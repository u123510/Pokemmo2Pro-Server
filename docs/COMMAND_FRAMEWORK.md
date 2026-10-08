# 游戏服聊天命令框架

## 目标

游戏服聊天包只负责传输聊天数据。以 `//` 开头的消息由命令分发器处理，具体命令各自维护参数校验和业务流程，避免所有命令不断堆积到 `ChatPacket`。

## 当前结构

```text
server.game/src/main/java/org/pokemmo/gameserver/
├── command/
│   ├── Command.java
│   ├── CommandContext.java
│   ├── CommandDispatcher.java
│   ├── CommandFeedbackService.java
│   ├── GameCommandModule.java
│   ├── CommandParser.java
│   ├── CommandRegistry.java
│   └── commands/
│       ├── AddMonsterCommand.java
│       ├── AddParticleCommand.java
│       ├── CreateItemCommand.java
│       ├── HideCommand.java
│       ├── HealCommand.java
│       ├── EventDeleteNpcCommand.java
│       ├── EventSpawnNpcCommand.java
│       ├── PcCommand.java
│       ├── ReloadShopsCommand.java
│       ├── SpawnNpcCommand.java
│       ├── SetAbilityCommand.java
│       ├── SetAlphaCommand.java
│       ├── SetBallTypeCommand.java
│       ├── SetGenderCommand.java
│       ├── SetGiftCommand.java
│       ├── SetEvsCommand.java
│       ├── SetFormCommand.java
│       ├── SetHappinessCommand.java
│       ├── SetHiddenAbilityCommand.java
│       ├── SetIvsCommand.java
│       ├── SetNatureCommand.java
│       ├── SetOtCommand.java
│       ├── SetPokemonRarityCommand.java
│       ├── SetRibbonsCommand.java
│       ├── SetShinyCommand.java
│       ├── SetSecretShinyCommand.java
│       ├── UnlockDexCommand.java
│       └── WinBattleCommand.java
├── protocol/packets/c2s/
│   └── ChatPacket.java
└── services/
    ├── GameServerService.java
    ├── character/CharacterService.java
    ├── gtl/GtlListingService.java
    ├── gtl/GtlQueryService.java
    ├── gtl/GtlPurchaseService.java
    ├── gtl/GtlClaimService.java
    ├── gtl/GtlCancellationService.java
    ├── inventory/InventoryService.java
    ├── pokemon/PokemonService.java
    └── world/WorldService.java
```

## 调用流程

```text
ChatPacket.decode
  -> ChatPacket.handle
  -> CommandDispatcher.dispatch
  -> CommandParser.parse
  -> CommandRegistry.find
  -> Command.execute
```

普通聊天消息仍然进入原有 Redis 聊天队列。已识别的命令和未知的 `//` 命令都会被拦截，不会广播给其他玩家。

## 核心职责

### `ChatPacket`

只负责读取 `ChatType` 和 UTF-16LE 消息，并在 `NORMAL` 聊天中尝试调用 `CommandDispatcher`。它不应包含具体命令名、权限判断或数据库操作。

### `CommandParser`

负责识别 `//` 前缀、命令名和参数。当前支持空格分隔参数，并支持单引号、双引号和反斜杠转义，为以后处理带空格的名称或文本参数保留扩展空间。

### `CommandRegistry`

保存命令名、别名到命令实例的映射。命令通过 Guice `Multibinder<Command>` 注册，新增命令时只需要新增类并在 `GameCommandModule` 中增加一条绑定；`GameProtocol` 只负责安装该模块。

### `CommandDispatcher`

负责统一处理：

1. 是否为命令消息；
2. 命令是否存在；
3. 当前角色是否完成加载；
4. 命令所需权限；
5. 创建 `CommandContext` 并调用命令；
6. 记录命令未处理异常的完整堆栈，并向客户端返回统一失败提示。

### `CommandContext`

向命令提供当前会话、角色管理器、游戏服务和统一反馈入口。命令不需要重复从 Netty `Session` 获取这些上下文。

### `Command`

每个具体命令实现以下信息：

```text
命令名称
别名
最低权限
用法说明
执行逻辑
```

默认最低权限为 `GM`。如果以后需要普通玩家命令，可以在具体命令中覆盖权限配置。

## `setalpha` 当前行为

```text
//setalpha <party slot 0-5> <true|false>
```

处理步骤：

1. 校验参数数量和队伍位置；
2. 解析 `true` 或 `false`；
3. 从当前角色队伍中找到目标宝可梦；
4. 更新 `pokemon.is_alpha`；
5. 更新内存中的 `PokemonData.isAlpha`；
6. 发送 `SendUpdatePokemonDataPacket` 刷新客户端稀有度；
7. 通过统一反馈服务回复执行结果。

## 新增命令流程

例如新增 `//setshiny`：

1. 在 `command/commands/` 新建 `SetShinyCommand.java`；
2. 实现 `Command` 接口；
3. 在 `GameCommandModule` 的 Guice 配置中增加一条 `Multibinder` 注册；
4. 在对应领域服务中增加数据库和内存更新逻辑；
5. 不修改 `GameProtocol`、`ChatPacket`、解析器和分发器。

## 当前宝可梦状态命令

管理员地图隐身：

```text
//hide
```

无参数切换，仅 GM 及以上可用。隐身角色对普通玩家不可见，对自己与 GM 及以上仍可见；
再次输入恢复完整角色、跟随和交通状态。状态仅属于在线角色上下文，不写数据库。
`PlayerVisibilityService` 统一过滤加载、移动、外观和跟随等广播，以及附近玩家交互目标。
权限范围、重连与同步约束见 [`CHAT_HIDE_COMMAND.md`](CHAT_HIDE_COMMAND.md)。

管理员恢复当前队伍：

```text
//heal
```

无参数，仅 GM 及以上可用。服务端按当前 PARTY 的最大 HP 和包含 PP Up 的招式最大 PP
恢复整队，事务成功后更新在线队伍并发送既有 `0x16` 增量包。蛋、空队伍、队伍数据不一致、
战斗/交易/交互中或地图未加载完成时拒绝；不修改异常状态或其他宝可梦属性。详细边界见
[`CHAT_HEAL_COMMAND.md`](CHAT_HEAL_COMMAND.md)。

客户端 NPC Tool 的自定义 NPC 生成命令：

```text
//spawnnpc <spriteId> <scriptOffset 0> <spriteRegion> <movementType> <leashX 0..4> <leashY 0..4>
```

默认 GM 权限。在当前角色前方一格创建 NPC，由 `game.entity.NpcSpawnService`
校验站位、分配固定高序号，先经 `game.npc` 原子保存到独立 `resource/npc/custom`，
再插入在线地图并发送已有 S2C `0x12`；重启由 `ScriptManager` 在商店前恢复。
地图跨频道共享，不写数据库或原地图 NPC 数组。脚本偏移暂只支持 `0`，
不支持 `//eventspawnnpc` 的条件编辑。完整参数、协议证据和验证见
[`CHAT_SPAWNNPC_COMMAND.md`](CHAT_SPAWNNPC_COMMAND.md)。

客户端事件模式使用独立命令：

```text
//eventspawnnpc 0 248 10 0 0 0 0 -1 -1 false false false 1.0 0
```

`EventSpawnNpcCommand` / `EventNpcSpawnRequest` 按 14 个基础参数和条件对数量严格校验。
当前支持手动、无条件的新建，保存事件分类、闪光和缩放；开头 `0` 是万圣节分类，不是无事件。
`NpcSpawnAppearance` 复用原生实体视觉字段，`NpcSpawnService` 复用先保存后发布的生成链。
非零脚本、标志条件、附加条件、更新已有 NPC 和忽略重复明确拒绝，不静默丢弃；
事件分类不自动启用节日活动或日期控制。详情见
[`CHAT_EVENTSPAWNNPC_COMMAND.md`](CHAT_EVENTSPAWNNPC_COMMAND.md)。

删除当前地图中的已保存自定义 NPC：

```text
//eventdeletenpc <NPC运行时Object ID>
```

默认 GM 权限，ID 按 `long` 解析，不能使用 `entityIdx` 代替。
通过 `NpcDeleteService` 在商店目录写锁下先保存 `enabled=false`，
再从在线地图移除，关闭匹配商店报价并发送 S2C `0x08`。不物理删除配置、不释放序号、
不允许删除原生或未保存 NPC。异常时反馈中文错误，不通过猜测相邻格选择目标。
详见 [`CHAT_EVENTDELETENPC_COMMAND.md`](CHAT_EVENTDELETENPC_COMMAND.md)。

店铺定义支持 GM 命令 `//reloadshops`，无参数。递归加载 `resource/shop`，
全部校验通过才切换价格版本并关闭旧报价；失败保留旧配置。不会修改地图、数据库或运行环境。
详见 [`CHAT_RELOADSHOPS_COMMAND.md`](CHAT_RELOADSHOPS_COMMAND.md)。

客户端 Teleport 调试菜单使用相邻格移动命令：

```text
//moveclose <dx -1..1> <dy -1..1>
```

服务端忽略地图边界和瓦片可行走性，并同步本人和当前可见其他玩家的位置；战斗、交互、交易中以及目标格被占用时拒绝。

客户端地图调试菜单使用地图坐标命令：

```text
//moveto <region> <mapHeader/group> <gbaMap> <x> <y>
```

服务端按地区和地图资源查找目标地图，更新角色地图标识与坐标后复用完整地图重载流程，因此本人会收到地图包，旧地图玩家会收到移除，新地图可见玩家会收到加载。

在当前战斗中直接将玩家所在阵营判定为胜利：

```text
//winbattle
```

命令只在角色持有有效 `BattleManager` 且双方仍处于 `IN_BATTLE` 状态时生效。服务端将
当前会话阵营设为 `VICTORY`、对方阵营设为 `DEFEAT`，复用现有 `0x31`
`SendBattleFinishPacket` 广播；客户端随后通过 `0x33 BattleFinishSuccessPacket` 完成
战斗引用清理。该调试命令不模拟击倒，不补发经验，当前也不增加奖励金钱。详细边界见
[`CHAT_WINBATTLE_COMMAND.md`](CHAT_WINBATTLE_COMMAND.md)。

打开当前角色的 PC 界面：

```text
//pc
```

命令无参数。服务端先发送箱子信息（`0x6D`）和 `0x27 01` 开窗状态包，随后尝试发送
PC 宝可梦容器（`0x13`）。数据库刷新失败不会阻止客户端创建并显示 PC 界面。详细协议
证据和刷新顺序见 [`CHAT_PC_PACKET.md`](CHAT_PC_PACKET.md)。

按角色名创建或增加背包道具：

```text
//createitem <character name> <item id> <amount>
```

例如：

```text
//createitem LM 6220 1
```

其中 `6220` 是道具编号，`1` 是数量。命令写入目标角色的普通背包；如果普通背包中已经有同编号、无颜色/地区/PVP 限制的道具，则合并数量，否则创建新的道具实例。目标在线时发送完整背包刷新包，目标离线时只写入数据库。详细流程见 [`CHAT_CREATEITEM_PACKET.md`](CHAT_CREATEITEM_PACKET.md)。

按角色名添加一只野生模板宝可梦：

```text
//addmonster <character name> <pokemon index id> <level>
```

例如：

```text
//addmonster LM 6 20
```

其中 `LM` 是目标角色名，`6` 是宝可梦图鉴编号，`20` 是等级。命令会优先放入目标队伍的第一个空位；队伍已满时放入 PC。目标在线时会同步内存并发送 `SendAddPokemonPacket`，目标离线时只写入数据库，下次登录时加载。详细协议和生成规则见 [`CHAT_ADDMONSTER_PACKET.md`](CHAT_ADDMONSTER_PACKET.md)。

当前角色队伍还支持修改宝可梦性别：

```text
//setgender <party slot 0-5> <0 male|1 female>
```

例如：

```text
//setgender 0 0
//setgender 0 1
```

其中 `0` 表示公，`1` 表示母。队伍位置使用服务端内部的 `0..5` 索引，参数 `3` 是数组下标 `3`；如果按界面从 1 开始数的第 3 个位置，应输入 `2`。命令通过修改 `personality_value` 的低字节来匹配客户端性别边界，同时尽量保持原有性格值（nature）不变。`gender_ratio == 255` 的无性别宝可梦和 `gender_ratio == 0` 的公性宝可梦不能设置为母。数据库、当前内存对象、增量刷新包和 PARTY 容器刷新会同步更新。详细字段和处理流程见 [`CHAT_SETGENDER_PACKET.md`](CHAT_SETGENDER_PACKET.md)。

当前角色队伍还支持修改宝可梦性格：

```text
//setnature 1 BOLD
//setnature 1 大胆(BOLD)
```

第二个参数只需包含 25 种英文性格枚举之一，不依赖中文名称。命令持久化 `personality_value`，保持原有性别，并同步在线内存、能力值、性格字段和 PARTY 容器。完整枚举见 [`CHAT_SETNATURE_PACKET.md`](CHAT_SETNATURE_PACKET.md)。

客户端宝可梦调试菜单还会通过普通聊天发送原训练家名称和礼物勋章命令：

```text
//setot 0 {STRING_5685}
//setgift 0
```

`setot` 的第二个参数既可以是普通名称，也可以是客户端本地化占位符。`{STRING_5684}`、`{STRING_5685}` 由客户端故意原样发送，服务端不解析、不翻译，直接写入 `pokemon.ot_name`。`setgift` 只为目标宝可梦添加 Gift Ribbon，对应 `normal_ribbon[2]` 和客户端 64 位 ribbon 字段的 bit 21；它不是礼物模板编号，也不会读取 `resource/gift/Gift.jsonc`。详细行为见 [`CHAT_SETOT_PACKET.md`](CHAT_SETOT_PACKET.md) 和 [`CHAT_SETGIFT_PACKET.md`](CHAT_SETGIFT_PACKET.md)。

当前客户端调试菜单还支持粒子、精灵球类型和完整勋章 mask：

```text
//addparticle 1 0
//setballtype LM 1 0
//setribbons 1 65536
```

`addparticle` 追加一个 `0..38` 粒子 ID；`setballtype` 的最后一个参数是 `0..24` 球类型 ID；`setribbons` 的最后一个参数是完整 64 位 ribbon mask，`65536` 表示只保留 bit `16` 的 Champion Ribbon。详细行为见 [`CHAT_ADDPARTICLE_PACKET.md`](CHAT_ADDPARTICLE_PACKET.md)、[`CHAT_SETBALLTYPE_PACKET.md`](CHAT_SETBALLTYPE_PACKET.md) 和 [`CHAT_SETRIBBONS_PACKET.md`](CHAT_SETRIBBONS_PACKET.md)。

以下命令的第一个参数是目标角色名，例如 `LM`：

```text
//setshiny LM 0 false
//setsecretshiny LM 0 false
```

参数顺序为：

```text
<目标角色名> <队伍位置 0-5> <true|false>
```

目标角色在线时，命令会同时更新其内存队伍并向目标会话发送稀有度刷新包；目标离线时只更新数据库，角色下次登录时会从数据库重新加载状态。

当前角色队伍还支持修改个体值和隐藏特性：

```text
//setivs 2 31 31 31 31 31 31
//setha 2 true
```

`setivs` 参数顺序为 `HP、攻击、防御、特攻、特防、速度`，每项限制为 `0..31`。`setha` 的 `true` 表示开启隐藏特性标志，`false` 表示关闭。详细说明见 [`CHAT_POKEMON_PROPERTIES.md`](CHAT_POKEMON_PROPERTIES.md)。

设置目标角色的特性槽：

```text
//setability LM 3 1
```

其中 `1` 是目标宝可梦 `abilities` 数组中的特性槽索引。服务端会校验该索引是否存在，并同步数据库、在线内存和客户端。详细说明见 [`CHAT_POKEMON_PROPERTIES.md`](CHAT_POKEMON_PROPERTIES.md)。

当前角色队伍还支持修改努力值和亲密度：

```text
//setevs 3 ATTACK 252
//sethappiness 3 255
```

`setevs` 支持 `HP`、`ATTACK`、`DEFENSE`、`SPEED`、`SP_ATTACK`、`SP_DEFENSE`，单项范围为 `0..252`；其中 `SP` 分别表示特攻和特防。`sethappiness` 的范围为 `0..255`。详细说明见 [`CHAT_POKEMON_PROPERTIES.md`](CHAT_POKEMON_PROPERTIES.md)。

当前角色队伍还支持修改与查看宝可梦形态：

```text
//setform 0 1
//setform LM 0 1
//setform list
//setform list 0
//setform list LM
//setform list LM 0
```

支持自身或指定玩家的队伍槽位（`0..5`）和形态 ID（`0..255`）。支持子命令 `list` 查看自身或指定目标玩家队伍当前形态概览及槽位宝可梦的已知形态变化分支列表。落库 `pokemon.form_type`，下发 `0x16` 增量刷新与 `0x14` 容器刷新；若该宝可梦正在跟随，还会重新计算跟随稀有度标识并向视野广播 `SendSetFollowPokemonPacket`。详细说明见 [`CHAT_SETFORM_COMMAND.md`](CHAT_SETFORM_COMMAND.md)。

当前角色队伍还支持修改宝可梦技能（招式）：

```text
//setmove <party slot 0-5> <move slot 0-3> <moveId>
//setmove <party slot 0-5> <moveId1> <moveId2> <moveId3> <moveId4>
```

普通权限（`NORMAL`）可用，别名 `setmoves`、`setskill`、`setskills`。支持单技能替换、技能遗忘（`moveId` 为 0，要求至少保留 1 个有效技能）和 4 技能批量配置。自动从 `MoveManager` 读取招式基础 PP，更新 `pokemon.moves`、`pokemon.moves_pp` 和 `pp_up_times`，在线内存同步并通过 `SendUpdatePokemonDataPacket`（`0x16`，启用招式刷新标志位）即时下发客户端。客户端配合 `pro.pokemmo2.skill` 可视化窗口快捷修改。详细说明见 [`CHAT_SETMOVE_COMMAND.md`](CHAT_SETMOVE_COMMAND.md)。

管理员点亮全部图鉴（包含前五代及 Gen 6~9 自定义宝可梦）：

```text
//unlockdex
//unlockdex LM
```

仅限管理员权限（`ADM`）使用。不加参数表示为自身点亮；加玩家名表示为指定玩家点亮。数据库持久化 `public.pokemon_dex` 的 4 层位图（遇见、收服、形态、完美），在线玩家会即时收到 `SendLoadDexPacket` 刷新客户端图鉴。详细说明见 [`CHAT_UNLOCKDEX_COMMAND.md`](CHAT_UNLOCKDEX_COMMAND.md)。

## 领域服务边界

`GameServerService` 已作为兼容门面保留，具体持久化业务按领域放置：

```text
PokemonService       宝可梦数据和容器换位
InventoryService     道具和背包
CharacterService     角色资产、跟随精灵和外观
GTL focused services 交易行上架、分类型查询、筛选、购买、取消和成交款领取
WorldService         节点、容器、事件、图鉴和实例
ShopTransactions     店铺购买/出售的金钱与道具原子事务
```

命令层只编排流程，数据库访问和游戏状态修改由领域服务负责；旧命令可继续通过兼容门面调用。
