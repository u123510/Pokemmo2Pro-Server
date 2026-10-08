# OpenMMO 项目架构总览

本文档是后续开发的入口。它记录源码模块、运行时边界、网络数据流、资源加载方式和主要扩展点。源码文件的逐文件清单见 [`SOURCE_FILE_INDEX.md`](SOURCE_FILE_INDEX.md)。

## 1. 项目范围

OpenMMO 是一个基于 Java 17、Gradle、Netty、Guice、PostgreSQL、jOOQ 和 Redis 的 PokeMMO 服务端研究项目。客户端协议通过逆向分析和网络抓包实现，当前 README 标注的主要适配客户端版本为 `26211`。

项目由以下 Gradle 子项目组成：

| 模块 | 作用 | 运行端口/产物 |
| --- | --- | --- |
| `server` | 通用 Netty 服务、协议抽象、TLS、压缩、Redis 和通用聊天模型 | 被其他服务依赖 |
| `server.login` | 登录、账号校验、游戏节点列表和进入游戏服务器 | `2106` |
| `server.game` | 游戏世界、角色、宝可梦、战斗、地图、脚本、命令和游戏协议 | `7777` |
| `server.chat` | 聊天节点、聊天频道和 Redis 广播 | `7778` |
| `db` | PostgreSQL 连接、数据库 schema 和 jOOQ 生成模型 | 被服务模块依赖 |
| `patcher` | Java Agent，替换客户端内嵌证书 | `patcher.jar` |

## 2. 运行时拓扑

```text
PokeMMO Client
   |
   | TLS / game protocol
   v
server.login :2106
   | 账号登录、节点选择、token
   v
server.game :7777 <----> PostgreSQL
   |                         ^
   | Redis chat messages      |
   v                         |
server.chat :7778 -----------+
```

各服务入口继续使用既有远程 PostgreSQL 配置，并通过 `Database` 内部的 HikariCP 连接池访问数据库。
连接池会验证、保活并替换失效连接，避免网络抖动后继续复用已关闭的 JDBC 连接。

## 3. 通用网络层

`server` 模块提供统一的协议执行链：

```text
Netty Channel
  -> ReadTimeoutHandler
  -> FrameCodec
  -> LoggingHandler
  -> PacketCodec
  -> Session
  -> Protocol.decode(...)
  -> Packet.decode / Packet.handle
```

`Protocol` 维护双向的 `opcode <-> Packet class` 注册表，负责通过 Guice 创建封包对象并校验封包是否完整读取。`Session` 管理加密状态、压缩状态、按连接串行的异步封包处理、发送和断线回调；发送路径不人为插入固定延迟。普通封包保持单线程顺序，可能包含脚本等待的长操作封包使用同一连接的独立串行队列，避免 `Thread.sleep` 把后续移动请求排队。`TRACE` 级别同时输出每个收发包的连接和封包序号、数据流、协议类、opcode、长度、packet hex、解码字段以及编码/解码/排队/处理耗时；凭据、token、session key 和硬件标识会脱敏。

好友操作使用 C2S `0x60`，请求携带目标玩家名称和一个客户端固定为 `--` 的 UTF-16LE 附加字段。
`FriendActionPacket` 先查询目标和已有关系：已有关系时删除并发送 S2C `0x63`，没有关系时仅向在线目标
发送 S2C `0x21 REQUEST_FRIEND` 好友请求 UI，不提前写数据库。目标通过 C2S `0x21` 接受后，
`FriendService` 在事务中同时写入 `player_id -> friend_id` 和反向关系，再分别发送双方的 `0x63` 完整列表；
拒绝、超时和断线只清理内存中的待处理请求。世界加载和重连从数据库读取好友列表；好友名称、添加时间
来自数据库，在线状态通过 `GameSessionPool` 实时判断，客户端尚未确认的元数据字段暂以 `0` 填充。字段
顺序和验证范围见 [`FRIEND_PACKET.md`](FRIEND_PACKET.md)。

玩家单挑使用 C2S `0x25`：`BattleRequestPacket` 严格解析客户端 `f.Bs0` 的 UTF-16LE 目标名称、单打格式、
flags 和可选字段，在当前地图会话池内确认目标在线、同频道、同地图且双方有存活 PARTY 宝可梦。通过后由
`BattleRequestManager` 保存 30 秒待处理请求并向目标发送 S2C `0x21 REQUEST_BATTLE`；目标通过 C2S `0x21`
接受或拒绝。接受时 `BattleGenerator` 创建共享 `PlayerBattle`，将同一 `BattleManager` 写入双方在线角色并分别
发送 S2C `0x17` 初始化；拒绝、超时、断线或忙碌状态只清理内存请求，不写入数据库。字段和当前未验证的客户端
辅助选项语义见 [`BATTLE_REQUEST_PACKET.md`](BATTLE_REQUEST_PACKET.md)。

玩家观战的正式链路由客户端 `Qy0` 的右键菜单触发：目标处于 `RL0.Com5` 或 `RL0.YC` 时，
客户端把原生“挑战”项替换为观战回调，发送 C2S `0x34` 和目标玩家 Object ID 的 8 字节 little-endian `long`。
服务端在 `BattleSpectatingService` 中校验同频道、同地区、同地图、在线状态、双方仍为 `IN_BATTLE` 的 `PlayerBattle`，并限制每场
最多 16 名观战者。观战者不写入阵营，只加入 `BattleManager` 的只读广播集合；S2C `0x30` 初始化包使用 `-1` 阵营/队伍
索引和 `isSpectate = true`，行动包会被拒绝。攻击、状态、回合和宝可梦换位等战斗广播会同步给观战者。客户端关闭观战
窗口发送 C2S `0x35` 无字段包，服务端同步清理观战状态。`//spectate <player_name>` 仅作为兼容调试入口。

宝可梦面板粒子选择使用 C2S `0x18`：封包携带宝可梦 Object ID 和一个粒子索引，服务端验证角色归属后持久化当前选择，并通过 S2C `0x16` 的当前粒子字段同步客户端。客户端的 `0xFD` 随机选择哨兵以 `-3` 持久化；普通宝可梦容器/增量 Codec 为满足客户端普通显示路径，会临时从已拥有粒子中投影一个正 ID，但不改写在线对象或数据库。客户端战斗粒子播放器不会处理 `0xFD`，因此战斗队伍、出场和换位 Codec 会在首次编码时从已拥有的可渲染粒子中解析一个正 ID，并在该场战斗后续封包中复用，数据库和在线对象仍保留 `-3`。

PC 自动排序使用 C2S `0x1B`：客户端提交容器类型、箱子索引数组和排序位。服务端在
`PokemonContainerService` 中按角色 `trainer_id` 锁定目标记录，当前仅处理已确认的等级排序位，
按箱内等级升序重新分配槽位，并通过 S2C `0x16` 位置位更新在线客户端；交易期间或未知排序位
会被拒绝，避免把未确认的客户端字段当作其它业务规则。

宝可梦重命名使用 C2S `0x0E`，封包携带宝可梦 Object ID 和 UTF-16LE 终止昵称。服务端按
`pokemon.id + trainer_id` 校验并更新 `pokemon.name`，随后更新在线 PARTY 对象并发送 S2C `0x14`
完整宝可梦记录；客户端按 Object ID 更新已有 PARTY/PC 容器条目，不替换已打开 PC 窗口的控制器。

PC 放生使用 C2S `0x0C`，封包只携带 8 字节 little-endian 宝可梦 Object ID。服务端按
`pokemon.id + trainer_id + container_id = PC` 锁定目标，拒绝交易中或已报价宝可梦，并在事务中
将记录移到 `deleted` 容器而不是直接删除；成功后发送 S2C `0x15` 按 Object ID 移除已打开 PC
窗口中的条目。放生请求不改变 PARTY 在线数组，因为客户端入口只允许 PC 容器。

宝可梦携带道具使用 C2S `0x0F`：封包携带宝可梦 Object ID、道具索引和 PC/PARTY 容器类型。
服务端在同一事务中校验主背包道具、扣除新道具、返还旧携带道具并更新 `pokemon.item`，
随后用 S2C `0x16` bit `256` 和 `0x40` 主背包快照同步在线客户端。`pokemon.item = -1`
是未携带道具的规范值，历史 `0` 会在读取边界归一化。

背包消耗道具使用 C2S `0x26`：封包携带 `itemIndexId`、PARTY 目标宝可梦 Object ID、数量、
目标技能位置和 `unuse` 标记。普通消耗品由 `ItemUseService` 在同一事务中锁定角色主背包道具和
目标宝可梦，从 `Item.bin` 读取通用恢复/状态/PP/经验/等级/亲密度字段，并由
`resource/item/ItemUse.jsonc` 覆盖特殊行为；成功后更新在线 `partyPokemons` 并发送对应的 `0x16` 与
`0x40`。自行车等角色系统道具不需要目标宝可梦或 `owned_item`，只锁定角色并切换
`character.transportation`，再发送交通状态更新。不属于角色、不是 PARTY、数量不足、交易中、地区不符
或效果无效时均拒绝且不扣除普通消耗品。

## 4. 游戏服务器主链路

2026-09-15 剧情入口已替换：旧 `resource/interact`、`resource/event` 的 39 个剧情文件及
地图/角色硬编码分支已删除，保留通用对话协议类型。`StoryCatalog` 加载
`resource/story/**/*.jsonc`，`game.story.StoryRuntime` 统一接收移动、NPC、地图确认、
登录/断线、交互回执和战斗回执，`StoryActionExecutor` 按有限动作推进节点。
旧章节类只提供领域兼容适配，不再决定生产剧情流程。
当前通用目录包含真新镇、大木包裹、常磐捕获教学、宝可梦中心护士和尼比至枯叶早期主线。
`PalletStoryScene` 负责异步原生表现，`PalletStoryNpcs` 按角色投影 NPC，不修改共享地图。
`services.story.PalletStoryStore` 原子保存检查点、初始宝可梦、图鉴和首次战斗结果；
复用 `oak_lab_status` 和关都 `first_partner_status`，4..9 阶段不重新进入开场，不改包裹状态。
完整范围、兼容存档及未运行验证项见 [`PALLET_TOWN_STORY.md`](PALLET_TOWN_STORY.md)。
首次战斗初始化先检查编码，再发布战斗引用，抛异常时清理引用；客户端自身的 `0x30`
训练家头漏读已按原始 JASM 修复，不能通过删服务端字段或清空剧情存档绕过。
协议证据与双端验证见 [`BATTLE_INIT_PACKET.md`](BATTLE_INIT_PACKET.md)。
`PalletStoryLifecycle` 在非战斗的登录/重连时按角色和账号读取最新两项剧情字段，
只更新剧情内存投影；数据库读取失败时阻止旧缓存触发剧情，进行中的战斗不被外部改表重置。
阶段 0 的玩家若已经站在真新镇触发格，地图加载确认会补查触发；普通移动入口继续保留。
开场检查点的一致性由 `PalletStoryProgress.validateOpeningCheckpoint` 复用校验：
阶段 0..2 与已选择初始宝可梦 `0/1/2` 冲突时，入口不先锁定剧情，事务不推进阶段或发放资产。
日志给出实际字段，存档部分重置需维护者核对；不会自动将其当成新角色或已完成角色。

`resource/story/kanto/oak_parcel/chapter.jsonc` 配置第二章，接续常磐市商店领取包裹、
回研究所交付、图鉴及五球奖励。`OakParcelStore` 锁定角色/账号，`StoryInventory` 锁定该角色主背包，
物品与检查点在同一事务内保存；提交后更新在线进度、`0x40` 背包、`0x0A` 真实图鉴及个人 NPC 投影。
交付为 `oak_lab_status=5`，领奖为 6，包裹状态使用既有 `oak_parcel_status`；完成玩家不重领。
包裹 `349` 由 `ItemManager.isStoryBound` 保护，禁止普通消耗/转移，奖励为 `5004 × 5`。
本章登录重读字段，读库失败暂停；未完成且丢失的包裹可补领，已交付不再要求包裹存在。
复用原生对白与共享交互清理，不新增图鉴菜单解锁位或客户端 UI，详见 [`OAK_PARCEL_STORY.md`](OAK_PARCEL_STORY.md)。

`resource/story/kanto/viridian_city/catch_tutorial/chapter.jsonc` 配置第三章。
完成大木章节后，常磐市 `npc_3` 播放原生捕获说明并启动固定 5 级独角虫的普通 `WildBattle`。
成功捕获由既有 `BattleCaptureService`/`PokemonCaptureService` 结算，随后
`StoryService.onBattleFinished` 在 `ViridianCatchStore` 中按角色/账号锁定并设置
关都 `story_line_flag[1]` bit 4；结束回执播放完成对白。逃跑、击败和失败不完成，
已完成角色不重复启动；老人自动捕获的逐帧原版演出尚未验证，详见 [`VIRIDIAN_CATCH_TUTORIAL.md`](VIRIDIAN_CATCH_TUTORIAL.md)。
早期关都主线由 `resource/story/kanto/early_route_to_vermilion/chapter.jsonc`
驱动，使用徽章 bit 0..2 和故事位 bit 5..14 记录尼比至枯叶的节点，
训练家战斗使用通用 `START_TRAINER_BATTLE` 动作。

`server.game` 的主要链路如下：

```text
GameProtocol 注册 opcode
  -> c2s Packet.decode
  -> Packet.handle(Session)
  -> CharacterManager / domain service / compatibility facade
  -> 数据库更新或游戏状态更新
  -> s2c Packet + Codec
```

登录游戏世界时，`SelectCharacterPacket` 从数据库读取角色和 PARTY 宝可梦，填充 `CharacterManager.partyPokemons`，之后 `CharacterManager.handleLoadGameWorldContext()` 发送容器、背包、图鉴、地图和玩家状态。角色选中后由 `CharacterManager` 开始在线计时，并在线期间每分钟将完整分钟原子累加到 `character.online_minutes`；重连或断线时再结算剩余完整分钟。角色编码器按客户端协议把存储的分钟转换为秒；初始化脚本不会给新角色预置两小时。

邮箱列表请求由 C2S `0x99` 携带页码和收件/发件标志；`RequestEmailPacket` 通过
`MailService` 查询 `mail_message`，按每页 10 条由 S2C `0x97` 返回真实收件箱或发件箱条目。客户端
点击条目后发送 C2S `0x96`，`ReadEmailPacket` 查询并在必要时标记已读，再由 S2C `0x99`
返回邮件详情。
世界加载时的 S2C `0x98` 返回收件、未读和发件计数。发送邮件使用 C2S `0x95`，服务端将道具
Object ID 校验为 `owned_item.item_id`，在一个事务中写入邮件及附件、转移道具/精灵到收件人的
`mail` 容器并扣除发送者金钱；成功后刷新发送者资产和双方在线邮箱计数。详情读取由 C2S `0x96`
触发并通过 S2C `0x99` 返回；收件人通过 C2S `0x98` 携带邮件 ID、附件槽位、页码和客户端领取操作模式领取单个附件，附件类型由详情槽位决定，事务成功后刷新金钱、背包、PARTY/PC、邮件计数、当前列表页和详情中的 `claimed` 位。C2S `0x97` 用于删除邮件，已领取附件仍保留记录，只有没有未领取附件的邮件才允许删除。
字段顺序和边界见 [`EMAIL_PACKET.md`](EMAIL_PACKET.md)。

玩家移动由 `MovePacket` 校验并更新在线 `PlayerEntity`；`CharacterManager` 按独立地图 Session 池向可见同频道玩家广播移动动画，位置纠正和跨地图时的实体移除/加载则使用对应封包。新玩家进入地图后，等客户端通过 C2S `0x05` 确认地图加载，再同步双方的完整玩家实体，避免地图初始化清理过早到达的附近玩家封包。协议字段和验证边界见 [`PLAYER_MOVEMENT_PACKET.md`](PLAYER_MOVEMENT_PACKET.md)。

`//hide` 由 `HideCommand` 转给 `PlayerVisibilityService`，在线状态存于角色独立的
`PlayerVisibilityState`。隐身普通观察者收不到角色加载/移动/外观等地图包，自己和 GM 及以上仍可见。
切换与发送共享该角色的状态监视器，进图双向各自校验；隐藏发送清除跟随与实体移除，
恢复发送完整实体与交通状态。附近交易、单挑和观战校验目标可见性；
不改数据库、NPC、好友在线状态或普通聊天，完整协议见 [`CHAT_HIDE_COMMAND.md`](CHAT_HIDE_COMMAND.md)。

`//heal` 由 `HealCommand` 转给 `PokemonHealingService`。服务按 `trainer_id` 锁定当前 PARTY，
按在线宝可梦的等级、IV、EV、性格和 PP Up 计算最大 HP/PP，在同一事务中更新数据库；成功后
同步在线 `PokemonData` 并发送既有 S2C `0x16` 的 HP/招式 PP 增量，不修改状态异常或其他属性。
权限、队伍一致性和忙碌状态边界见 [`CHAT_HEAL_COMMAND.md`](CHAT_HEAL_COMMAND.md)。

同一条成功移动在剧情/坐标事件未触发且不处于战斗或交互状态时，会检查目标格的草丛、地下地图可行走地面或冲浪水面。`WildEncounterManager` 扫描 `resource/map/**/*.json` 与 `resource/encounter/unmapped/*.jsonc`，这些文件仍保持上游遭遇结构，并使用其中的槽位权重、物种和等级范围生成临时野生队伍后进入既有 `WildBattle` 流程。运行时支持 Kanto、Hoenn 和 Sinnoh 地区。碎岩和钓鱼表及钓鱼竿分组已加载，等待相应客户端交互封包接入。详见 [`WILD_ENCOUNTERS.md`](WILD_ENCOUNTERS.md)。
野生战斗中的球道具由 `BattlePokemonCommandPacket` 解析为敌方当前宝可梦；服务端按 `Item.bin` 的 `itemBallType` 识别球类（非球编码为 `255/-1`），并由 `Gen5CaptureCalculator` 组合 `CaptureSpecies.jsonc` 的种族捕获率、HP、状态、球种效果、回合和图鉴临界捕获。`BattleCaptureService` 通过三次普通判定得到 `0/1/3` 次失败摇晃或结果 `4`，`PokemonCaptureService` 在事务中扣除球，成功时再将野生宝可梦转为角色 PARTY/PC 资产。所有已扣球尝试都会广播完整的 S2C `0x37` 专用捕获动画（战斗选择器、球道具 ID、结果；成功附带容器和槽位），并按剩余数量使用 S2C `0x41` 更新球堆叠或 S2C `0x43` 删除空堆叠，避免战斗中用 `0x40` 替换整个背包容器。成功后的 PARTY/PC 容器与槽位同时在动画包中提供，并以 `CATCH_POKEMON` 状态发送战斗结束封包，失败时继续战斗。详见 [`WILD_CAPTURE.md`](WILD_CAPTURE.md)、[`BATTLE_CAPTURE_PACKET.md`](BATTLE_CAPTURE_PACKET.md) 和 [`INVENTORY_ITEM_UPDATE_PACKET.md`](INVENTORY_ITEM_UPDATE_PACKET.md)。

C2S `0x32` 的行动按命令类型分流：单打 `MOVE` 可省略目标字节，服务端将目标推导为对手首个登场位；双打、多打等需要目标的战斗必须携带 `commandTargetData`。无附加字段的 `RUN = 3` 只允许野外战斗，先发送 S2C `0x34 NORMAL_SUCCESS`，再以实际胜方阵营作为 S2C `0x31` 的客户端展示阵营；NPC/训练家战斗拒绝 RUN；玩家对战使用独立的 `FORFEIT = 13`，投降方判负并发送 S2C `0x34 FORFEIT`，S2C `0x31` 仍展示实际胜方。三条路径不会互相复用结果语义，结束后均保留 `BattleManager` 直到客户端发送 C2S `0x33` 回执。详见 [`BATTLE_COMMAND_PACKET.md`](BATTLE_COMMAND_PACKET.md) 和 [`BATTLE_RUN_PACKET.md`](BATTLE_RUN_PACKET.md)。

附近玩家交易请求走独立的轻量协议链：C2S `0x51`（官方）或 `0x62`（抓包兼容）
`RequestTradePacket` 读取 UTF-16LE 玩家名称，
 由 `CharacterManager` 在当前地图会话池中校验在线、同频道、同地图、非本人和非忙碌条件，随后
 通过 S2C `0x21 SendInteractPacket` 发送 `REQUEST_TRADE` 交互及发起者名称。目标通过 C2S `0x21`
 接受或拒绝；接受后由 `TradeManager` 持有报价和状态，`TradeService` 在数据库事务中校验并交换
 金钱、主背包道具和 PARTY/PC 宝可梦；接收宝可梦优先进入 PARTY 空槽，PARTY 满后进入 PC，
 成功后刷新双方在线容器。协议和验证边界见
 [`TRADE_PACKET.md`](TRADE_PACKET.md)。

## 5. 数据边界

项目同时维护三种状态：

1. **数据库状态**：由 `org.pokemmo.gameserver.services.character`、
   `org.pokemmo.gameserver.services.pokemon`、`org.pokemmo.gameserver.services.inventory`、
   `org.pokemmo.gameserver.services.gtl`、`org.pokemmo.gameserver.services.mail`、
   `org.pokemmo.gameserver.services.friend`、`org.pokemmo.gameserver.services.world`、
   `org.pokemmo.gameserver.services.shop`、
   `org.pokemmo.gameserver.services.story`、
   `ServerService` 和 jOOQ 表模型读写；`GameServerService` 仅作为兼容门面。
2. **在线内存状态**：由 `CharacterManager`、`PokemonData`、战斗对象和地图对象维护。
3. **客户端状态**：由 `Send*Packet` 和对应 Codec 同步。

新增功能必须明确三者是否都需要更新。仅改数据库不会立即改变在线客户端；仅改内存又可能在重载或重启后丢失。

角色换装请求 `0x30` 由 `ResetCharacterClothesPacket` 校验 owned item 归属后进入 `CharacterService`：普通槽位更新对应的 skin/color 字段，自行车槽位 `BIKE = 11` 单独更新 `character.bike`；成功后同步在线 `SkinData` 并通过 `0x93` 广播给本人及同地图同频道玩家。

GTL 宝可梦上架遵循同一边界：事务写入 `gtl_listing`、扣除 `character.money` 并将
`pokemon` 移入 `auction` 容器；事务成功后更新在线角色金钱，再发送 GTL 操作结果、
金钱增量和 S2C `0x15` PC 宝可梦移除通知。该增量包直接更新已打开的 PC 容器控制器，
避免完整 `0x13` 快照替换控制器后界面仍保留旧槽位引用。

GTL 物品/时装上架同样在事务中锁定角色和 `owned_item`。普通物品使用资源可交易标志，
可穿戴时装则按 `SkinType` 的客户端 item index/addon 映射校验，以避开不完整资源元数据；
账号绑定物品仍被拒绝。完整堆叠移到 `void` 背包，部分堆叠则减少原记录并生成新的
Object ID 保存挂单数量；成功后发送 `0x9C`、金钱更新、完整 `0x40` 主背包刷新和当前
`0x9B` 页面刷新。公开物品页和“我的挂单”共用混合条目模型，按每条挂单的
`listing_type` 写入宝可梦或物品字段。物品购买由同一个 C2S `0x9C` 入口按挂单类型分派。
事务锁定挂单、买家角色和卖家
`void` 背包中的 `owned_item`；完整购买转移原记录，部分购买减少卖家记录并生成新的
买家 Object ID，随后刷新买家金钱、主背包和当前 GTL 页面。挂单全部售出后保留
`status = 1` 与 `sold_amount`，卖家通过 C2S `0x9E` 领取成交款。物品取消仍未实现。

GTL 宝可梦购买由 C2S `0x9C` 进入。事务锁定挂单、买家角色和拍卖容器中的宝可梦，
检查挂单状态、有效期、余额和 PC 容量后扣除买家金钱，将宝可梦所有权转给买家并移动
到 PC 空槽，最后把挂单标记为已售。购买失败时通过 S2C `0x9C` 发送拒绝结果，成功则通过
`0x0C` 和 `0x14` 同步金钱和新增宝可梦，避免客户端显示
“上架成功”。服务端还会按会话最近一次 `0x9B` 请求序号重发当前 GTL 页面。会话状态同时保存排序类型、宝可梦性别、最小/最大价格、最低/最高等级、孵蛋年份时间范围、最低/最高 IV 属性和值、至少匹配的满 IV 数量、隐藏仅雌性种族开关、蛋组、宝可梦性格、闪光类型、粒子 ID、已学习技能 ID、
隐藏特性、特性 ID、头目类型、隐藏百变怪开关、形态变化已解锁开关、最低/最高努力值、宝可梦图鉴 ID 和物品索引 ID 搜索集合。性别条件通过图鉴 `genderRatio` 与 `personality_value`
低 8 位执行，价格条件通过 `unit_price` 范围执行，等级条件通过 `pokemon.level_value` 范围执行，孵蛋年份条件通过 `pokemon.catch_time` 的 UTC 时间戳开区间执行，IV 条件通过 `pokemon.iv_values[index + 1]` 的范围执行，努力值规则 `17/18` 通过 `pokemon.ev_values[index + 1]` 的最低/最高范围执行，规则 `19` 通过六项 IV 中值为 `31` 的数量执行，规则 `20` 先将图鉴能力 ID 转换为 `dex_id + pokemon.ability` 槽位组合再筛选，规则 `21` 通过排除图鉴 `genderRatio = 254` 的种族集合执行，蛋组条件通过图鉴蛋组映射执行，性格和闪光条件通过 `personality_value`、
`is_shiny`、`is_secret` 执行，指定粒子条件通过 `particle_effects` 数组包含关系执行，
`0xFC` 任意粒子条件通过数组非空判断执行，已学习技能条件通过 `moves` 数组包含关系执行；这些筛选条件在页面重发时
继续使用相同的价格/时间排序以及 `pokemon.dex_id` 或 `owned_item.item_index_id` 条件；隐藏特性通过
`pokemon.has_hidden_ability` 执行（规则 `13` 的索引 `0` 为有隐藏特性），头目通过 `pokemon.is_alpha` 执行（规则 `14` 的索引 `0` 为头目），隐藏百变怪通过排除 `pokemon.dex_id = 132` 执行，形态变化已解锁规则 `24` 因缺少客户端稀有度位字段而返回空的宝可梦结果，命中注定的相遇值 `2` 在当前无明确数据字段时返回空结果；物品时装部位规则 `33` 读取 `0..9` 的 `SkinType` 槽位，按客户端 item index 普通区间及帽子扩展区间筛选 `owned_item.item_index_id`，并在总数、分页和 Session 页面刷新中复用同一条件。
避免已售条目残留或操作后丢失
当前搜索结果；卖家成交款保留在已售挂单中，直到 C2S `0x9E` 领取。

GTL 购买在扣除买家金额、转移宝可梦或道具、更新挂单的同一数据库事务中向
`gtl_trade_history` 写入成交快照。客户端切换交易行第四个页签时发送无 payload 的
C2S `0x9F`，`RequsetTradeHistoryPacket` 查询当前角色作为买家的最多 255 条记录，并通过
S2C `0x5E` 返回“发送金额 / 收到商品”的两条交易明细。该协议中的时间是 epoch 毫秒，交易
类型 `2` 表示 GTL。实现前的旧挂单没有买家及逐笔成交信息，不能根据现有数据安全回填。

GTL 成交款领取由 C2S `0x9E` 进入，payload 为一个 unsigned byte 数量和对应数量的
`long LE` 挂单 ID。事务按 ID 锁定目标挂单和卖家角色，只接受卖家本人的已售挂单；将
`unit_price * sold_amount` 汇总入 `character.money` 后，统一把挂单状态改为 `3`（已领取）。
这保留成交数量历史并杜绝重复入账。事务成功后更新在线角色金钱，通过 `0x9C`、`0x0C`
和当前 `0x9B` 页面刷新同步客户端；任一挂单校验失败或金额溢出则整体回滚。

GTL 宝可梦取消由 C2S `0x9D` 进入。事务只允许卖家本人取消活跃且未成交的挂单，锁定
挂单和 `auction` 容器中的宝可梦，将挂单标记为取消并把宝可梦返还到原 PC 槽位；原槽位
冲突时选择卖家 PC 的下一个空槽。客户端通过 `0x9C` 操作结果和 `0x14` PC 增量包同步；
服务端随后按会话最近一次 `0x9B` 的请求序号、列表类型和页码重发当前页面，使已取消
挂单立即从交易行移除。

## 6. 命令扩展边界

聊天命令从 `ChatPacket` 进入：

```text
ChatType.NORMAL + "//..."
  -> CommandDispatcher
  -> CommandParser
  -> CommandRegistry
  -> 独立 Command 类
  -> 对应领域服务持久化
  -> UpdatePokemonData / 容器刷新 / UI 状态包 / 聊天反馈
  ```

`//pc` 是一个不改数据库的 UI 命令：它刷新 PC 容器和箱子信息后，通过已注册的
`0x27` S2C 状态包让客户端打开 PC 窗口。客户端随后可能用 C2S `0x1C` 提交箱子
名称或顺序，`UpdatePcBoxInfoPacket` 负责读取和校验；`0x6D` 返回的箱子数量按角色
PC 扩容数动态计算。箱子 UI 偏好当前未接入数据库持久化。

无 payload C2S `0x27` 是场景 A 键兜底请求，不代表目标一定是电脑。
`SceneInteractionService` 依据在线地图、坐标和朝向优先识别 NPC，再匹配已确认电脑；
NPC 入口与明确实体 ID 的 C2S `0x22` 共用分派链路。`MapFile` 保留资源脚本键，
未接入新剧情模块的 NPC 只记录未处理原因，不打开 PC、不进入对话状态。未知背景目标不猜测业务。
NPC 若有包裹章待办，由 `StoryService.beforeShop` 先处理领取/交付；其余才由
`game.shop.ShopService` 按店铺文件 `npcs` 处理，不依赖旧剧情事件开关；
携带包裹或完成玩家正常购物，其他 NPC 最后转交章节普通对话。
`ShopNpcBindings` 以已加载地图文件名和 NPC 序号构造不可变索引，不修改 NPC Object ID。
省略 `npcs` 时保留旧地图 `shopId`，显式列表则覆盖本店旧绑定，空列表明确解绑。
`ShopCatalog` 从 `resource/shop` 中每店一个 JSON/JSONC 构造不可变价格版本，
S2C `0x23` 对旧客户端只读，协商版本 1 后携带报价和独立回收价。
双向 `0xDC` 处理能力协商、连续序号的买卖请求与回执，避免与旧玩家交易 opcode 冲突。
`services.shop` 按当前角色锁定金钱与主背包，在同一事务中扣款/发物品或扣物品/加钱；
提交后才更新在线状态并发送 `0x40`、`0x0C` 和交易结果。
地图可选 `interactionCounters` 只补充服务端柜台行为，使隔一格柜台的目标可被识别。
重载采用版本读写锁，NPC 绑定与价格作为同一快照发布，开店和结算使用同一解析规则；
绑定不存在或冲突时保留旧快照，进行中的交易不变价，旧窗口在发布新版本后失效；
移动、转向、换地图、断线和重连清理报价，不将商店状态持久化成剧情上下文。
真实客户端需按 [客户端方案](NPC_SHOP_CLIENT_INTEGRATION.md)适配，
完整协议见 [`NPC_SHOP_PACKET.md`](NPC_SHOP_PACKET.md)。
`PcInteractionService` 负责电脑菜单及其 PC/GTL/邮箱入口，不直接写数据库。
常磐市宝可梦中心一楼兼容坐标 `(0, 5, 4, 11, 2, 0)`、`(0, 5, 4, 11, 1, 2)` 或 `(0, 5, 4, 11, 2, 2)` 且朝上时，通过 S2C `0x21`
发送标准 1-based `MULTICHOICE` 菜单；选项分别复用 PC 初始化、默认 GTL 首页和邮箱
计数/收件箱列表，取消或非法选择清理当前交互状态。详见
[`PC_INTERACTION_PACKET.md`](PC_INTERACTION_PACKET.md)。

新命令应保持以下结构：

自定义实体命令 `//spawnnpc` 由独立 `SpawnNpcCommand` 转交 `game.entity.NpcSpawnService`：
六参数按客户端 NPC Tool 顺序校验，前方格站位校验后，由 `game.npc.CustomNpcCatalog`
和 `CustomNpcStore` 保存独立 JSONC，再更新地图 NPC 字典，通过已有 `0x12` 通知客户端。
文件位于 `resource/npc/custom`，固定序号从 100000 起，原地图数组不变。
`CustomNpcDefinition` / `CustomNpcCodec` 负责出生配置与严格字段校验。
该字典跨频道共享且新增时复制发布；
地图初始化保留已有 NPC Object ID，仅对未分配实体调用统一 ID 生成器。
不修改数据库、客户端、原地图文件或脚本偏移执行器。重启先恢复自定义实体再加载商店，
文件错误/冲突不覆盖原生实体，保存失败不发布在线 NPC。
详见 [`CHAT_SPAWNNPC_COMMAND.md`](CHAT_SPAWNNPC_COMMAND.md)。

事件工具入口 `//eventspawnnpc` 由独立命令/参数解析器校验后复用同一保存和生成链；
`NpcSpawnAppearance` 提供分类与视觉参数，不承担节日活动调度。当前仅支持无条件新建，
未接入的覆盖/条件操作会拒绝。事件 NPC 采用版本 2 独立文件保存分类/闪光/缩放，
原普通 NPC 版本 1 仍按 14 字段格式读写；`CustomNpcCodec` 严格区分两版，重启/删除不丢扩展字段。
客户端实体包复用既有视觉位和 float LE 缩放，事件分类不进入原生实体包。
详见 [`CHAT_EVENTSPAWNNPC_COMMAND.md`](CHAT_EVENTSPAWNNPC_COMMAND.md)。

`//eventdeletenpc` 走独立 `EventDeleteNpcCommand` / `NpcDeleteService`，参数是当前
NPC Object ID 而非固定序号。锁顺序为商店目录写锁、操作者交互锁、地图锁、自定义目录/文件锁；
与持有目录读锁的商店开店及成交互斥。仅对目录实际管理的实体保存停用状态，
然后使在线引用失效并复制移除字典；释放操作者锁后关闭跨角色的匹配报价，广播实体移除。
`NpcVisibilityService` 对生成与地图快照发送加地图锁，避免删除后重新发送旧 NPC；
不改变客户端封包或原地图文件，配置与序号保留以便恢复。
详见 [`CHAT_EVENTDELETENPC_COMMAND.md`](CHAT_EVENTDELETENPC_COMMAND.md)。

1. 在 `command/commands` 新建一个独立类。
2. 实现 `Command`，定义名称、权限和 usage。
3. 在 `GameCommandModule` 注册。
4. 将数据库写入放入对应领域服务，必要时通过 `GameServerService` 兼容门面调用。
5. 同步在线内存和客户端封包。
6. 在 `docs` 中记录输入格式、索引约定、边界和刷新行为。

## 7. 资源加载

`ScriptManager` 从根目录相对路径加载：

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

地图使用现有 JSON，新剧情配置使用独立 JSONC；旧 interact/event 图不再加载。宝可梦、道具、礼物、训练家和野外遭遇数据由游戏域对象解析。资源目录不是 Gradle 资源包的一部分，运行游戏服务器时工作目录必须是项目根目录。
剧情配置按章拆为真新镇开场和大木包裹/图鉴，在地图与道具就绪后加载；玩家进度不写入资源文件。
`resource/shop` 由道具路径的相邻目录加载，无需为每家店增加启动参数；`ScriptManager` 在
所有资源初始化后向 `ShopCatalog` 提供已加载地图，用于校验店铺 `npcs` 目标。
在此之前先初始化 `CustomNpcCatalog`，从地图路径的相邻 `npc/custom` 目录恢复独立 NPC。
JSONC 中的固定序号用于商店绑定，运行时 Object ID 不落盘。
`//reloadshops` 重载店铺定义及其中的 NPC 绑定，不重载地图或全局道具资源。

## 8. 当前高风险点

- `server.game/Main.java`、`server.login/Main.java` 和相关服务存在硬编码连接配置，部署前需要外置化。
- `Protocol` 对未注册 opcode 直接抛出异常，客户端发送新封包会关闭连接；新增协议必须先注册并补充 decode。
- `Session` 的普通入站处理按连接串行执行，避免连续移动封包并发处理造成位置乱序；脚本等待封包使用独立串行队列，不会阻塞移动；发送路径不再固定等待 `20ms`。
- jOOQ 文件是生成代码，不应直接手改；数据库字段变更后应重新生成。
- 在线内存、数据库和客户端刷新是三条独立路径，修改宝可梦属性时必须同时考虑。
- `resource` 路径依赖当前工作目录，IDE、Gradle 和部署脚本的工作目录要保持一致。

## 9. 开发顺序建议

新增功能时建议按以下顺序工作：

1. 先确认数据库字段和现有领域对象。
2. 找到对应 c2s/s2c opcode 和 Codec。
3. 查找现有同类功能的状态同步方式。
4. 将业务逻辑放入领域服务或独立命令类。
5. 增加数据库、内存和客户端三个层面的验证。
6. 更新模块文档和逐文件索引。
