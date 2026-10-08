# OpenMMO Java Package 职责说明

本文档为每个 Java package 记录职责和主要类型。当前生产源码静态扫描覆盖 **95 个 package、812 个 Java 源文件**，另有 18 个 JUnit 测试文件；本次同步宝可梦中心护士原生治疗流程。文件数量与完整文件路径见 [`SOURCE_FILE_INDEX.md`](SOURCE_FILE_INDEX.md)；模块级架构见各 `MODULE_*.md`。生成代码包只记录使用边界，不建议直接修改。

## server

| Package | 文件数 | 主要类型 | 作用 |
| --- | ---: | --- | --- |
| `org.server` | 10 | SessionInitializer, Session, ServerType, Server, Protocol, OutgoingPacket, Packet, IncomingPacket 等 | 通用服务端抽象：Server、Session、Protocol、Packet、DataFlow 和连接生命周期。 |
| `org.server.bytes` | 2 | ByteBufEx, ByteBufDelegator | ByteBuf 包装、字节序、字符串和数组读写辅助。 |
| `org.server.context` | 1 | AccountContext | 跨服务共享的账号上下文数据模型。 |
| `org.server.handlers` | 4 | PacketCodec, FrameCodec, EncryptionHandler, CompressionHandler | Netty pipeline 的帧、封包、日志、压缩和加密处理器。 |
| `org.server.node` | 2 | ServerNode, JoinableSeverData | 可连接节点和节点地址的数据模型。 |
| `org.server.protocol.tls` | 3 | TlsProtocol, TlsInfo, RootKeyLoader | TLS 协议、握手信息和根证书加载。 |
| `org.server.protocol.tls.hash` | 4 | NoHash, HmacSha256, Hash, Crc16 | TLS 握手使用的 CRC、HMAC 和无哈希策略。 |
| `org.server.protocol.tls.packets.c2s.incoming` | 2 | ClientReadyPacket, ClientHelloPacket | 服务端接收客户端 TLS 握手数据：校验时间戳、解析客户端公钥，并在握手完成后启用加密。 |
| `org.server.protocol.tls.packets.c2s.outgoing` | 4 | SendUpdateClientECPublicKeyPacket, SendTestConnectPacket, ClientReadyOutPacket, ClientHelloOutPacket | 客户端方向的 TLS 握手发送包：发送随机数/时间戳探测、公钥和公钥更新数据。包名沿用协议目录的方向约定。 |
| `org.server.protocol.tls.packets.s2c.incoming` | 1 | ServerHelloInPacket | 客户端侧接收服务端 TLS Hello：验证服务端签名、回送客户端公钥并启用加密。包名沿用协议目录的方向约定。 |
| `org.server.protocol.tls.packets.s2c.outgoing` | 1 | ServerHelloPacket | 服务端发送 TLS Hello：携带临时椭圆曲线公钥、根密钥签名和哈希长度，供客户端完成握手。 |
| `org.server.redis` | 2 | RedisUtil, RedisConfig | Redis 连接配置和消息队列工具。 |
| `org.server.services` | 1 | ServerService | 登录/游戏/聊天共用的节点、token 和账号上下文数据库服务。 |
| `org.server.union.chat` | 2 | ChatType, ChatMessage | 跨服务聊天类型和聊天消息模型。 |
| `org.server.union.language` | 1 | LanguageType | 聊天和本地化使用的语言枚举。 |
| `org.server.util` | 4 | UuidUtils, TransportMethod, IpUtil, ECUtil | IP、UUID、椭圆曲线和传输方式等通用工具。 |

## patcher

| Package | 文件数 | 主要类型 | 作用 |
| --- | ---: | --- | --- |
| `org.patcher` | 4 | StringTransformer, StringMethodVisitor, StringClassVisitor, PatcherAgent | Java Agent 和 ASM 字节码访问器，用于替换客户端证书字符串。 |

## server.chat

| Package | 文件数 | 主要类型 | 作用 |
| --- | ---: | --- | --- |
| `org.pokemmo.chatserver` | 1 | Main | 聊天服务器入口。 |
| `org.pokemmo.chatserver.chat` | 2 | ChatManager, ChatBroadcastListener | 聊天连接管理、订阅和广播监听。 |
| `org.pokemmo.chatserver.protocol` | 1 | ChatProtocol | 聊天协议 opcode 注册。 |
| `org.pokemmo.chatserver.protocol.c2s` | 3 | JoinChatServerPacket, ConnectChatServerPacket, ClientChatServerHello | 客户端/游戏节点连接聊天服务器和加入频道的请求。 |
| `org.pokemmo.chatserver.protocol.s2c` | 3 | SendSpeakerChatPacket, SendServerChatServerHello, SendChatServerConnectResultPacket | 聊天握手结果、连接结果和说话广播响应。 |
| `org.pokemmo.chatserver.services` | 1 | ChatServerService | 聊天节点和聊天连接相关服务。 |

## db

| Package | 文件数 | 主要类型 | 作用 |
| --- | ---: | --- | --- |
| `org.pokemmo.db` | 1 | Database | PostgreSQL HikariCP 连接池和 jOOQ DSLContext 封装。 |
| `org.pokemmo.db.jooq` | 5 | Tables, Routines, DefaultCatalog, Keys, Public | jOOQ catalog、table 汇总、key 和数据库 routine 入口。 |
| `org.pokemmo.db.jooq.routines` | 35 | PgpSymEncryptBytea2, PgpSymEncryptBytea1, PgpSymEncrypt2, PgpSymEncrypt1, PgpSymDecryptBytea2, PgpSymDecryptBytea1, PgpSymDecrypt2, PgpSymDecrypt1 等 | PostgreSQL 加密、摘要、随机值和 PGP 函数的生成包装类。 |
| `org.pokemmo.db.jooq.tables` | 15 | ServerToken, Character, BlackList, AccountContext, Account, OnlineChatNodeServer, Inventory, GameNode 等 | jOOQ 生成的表元数据和字段定义。 |
| `org.pokemmo.db.jooq.tables.records` | 15 | ServerTokenRecord, PokemonRecord, PokemonDexRecord, PgpArmorHeadersRecord, OwnedItemRecord, OnlineGameNodeServerRecord, OnlineChatNodeServerRecord, InventoryRecord 等 | jOOQ 生成的表记录 POJO，用于查询结果和更新/插入。 |

## server.game

剧情模块边界：`game.story` 允许依赖角色、地图、战斗/宝可梦、道具和已有封包、`services.story`，
负责不可变内容、在线会话及玩家独立表现，不直接执行 SQL，也不修改共享 NPC 出生数据。
`services.story` 允许依赖数据库/jOOQ、剧情检查点、道具与宝可梦数据，负责带归属条件的原子事务，
不承担对话 UI、定时器或客户端发包。`StoryRuntime` 解释通用 JSONC，`StoryActionExecutor`
只分派有限动作；`OakParcelStory` 等旧类型保留为领域兼容适配，不再作为生产剧情路由入口。
`OakParcelStore` 与事务内 `StoryInventory` 将物品变更和检查点一起提交。
主要类型及检查点语义见 [`PALLET_TOWN_STORY.md`](PALLET_TOWN_STORY.md) 和 [`OAK_PARCEL_STORY.md`](OAK_PARCEL_STORY.md)。
`PalletStoryProgress` 也提供开场检查点一致性校验，供移动/地图/NPC 入口与存档事务共同使用；
冲突仅报告实际字段，不自动重置存档或推断资产领取历史。
常磐市捕获教学新增章节配置、编排和完成标记事务，具体边界见 [`VIRIDIAN_CATCH_TUTORIAL.md`](VIRIDIAN_CATCH_TUTORIAL.md)。
`game.story.PalletStoryBattle` 也负责首次战斗的发送前编码检查和初始化异常引用清理；
不改变客户端协议格式、不跳过未完成存档，证据见 [`BATTLE_INIT_PACKET.md`](BATTLE_INIT_PACKET.md)。

商店改造的新增边界：

- `org.pokemmo.gameserver.game.character.PlayerVisibilityState`、`PlayerVisibilityService`：
  在线角色隐身状态、GM 可见例外、成对可见性规则和受保护的玩家实体发送。
  允许依赖在线角色、地图、权限和既有 s2c，不修改数据库、角色权限或 NPC 可见性。
  `command.commands.HideCommand` 只负责无参数命令入口与中文反馈。
- `org.pokemmo.gameserver.game.entity.NpcSpawnRequest`、`NpcSpawnService`：
  客户端 NPC Tool 六参数校验、前方格 NPC 创建和可见地图会话通知。
  允许依赖地图、角色状态、game.npc 保存组件、ID 生成器和已有 S2C 实体包；不写数据库/原地图资源，
  不执行 ROM 脚本偏移或实现商店结算。命令入口保持在 `command.commands`。
  `NpcDeleteService` 编排自定义 NPC 永久停用、地图移除和商店报价清理；
  `NpcVisibilityService` 共用地图可见会话查询与实体快照通知，避免删除后旧快照重新显示 NPC。
  `EventNpcSpawnRequest` 严格解析 NPC Tool 事件生成参数并拒绝尚未接入的事件条件/覆盖操作；
  `NpcSpawnAppearance` 保存事件分类、闪光与缩放并投影到现有实体字段，不实现节日调度。
- `org.pokemmo.gameserver.game.npc`：独立自定义 NPC 定义、JSONC 编解码、原子文件保存和启动恢复。
  `CustomNpcDefinition` 保存稳定序号和出生配置；`CustomNpcCodec` 校验文件字段；
  `CustomNpcStore` 只访问 `resource/npc/custom`；`CustomNpcCatalog` 校验已加载地图、
  分配自定义序号并在保存后恢复/注册实体。允许依赖 game.entity、game.map 和 JDK/Gson，
  不承载聊天协议、客户端 UI、数据库、商店结算或原生地图编辑。
  停用只接受目录实际管理的实体，磁盘定义比较一致后原子保存 `enabled=false`，不释放固定序号。
- `org.pokemmo.gameserver.game.shop`：店铺定义、独立 JSON/JSONC 加载与版本快照、
  NPC 访问校验、报价会话、重放防护、窗口生命周期和买卖流程编排。允许依赖角色、
  地图、道具元数据、s2c 和 `services.shop`；禁止直接执行 SQL 或修改全局道具价格。
  `ShopNpcBinding` 表达店铺 JSONC 中的地图名与 NPC 序号；
  `ShopNpcBindings` 用已加载地图校验目标并构造不可变绑定索引，处理冲突及旧地图绑定兼容。
  绑定与价格一起发布，不回写地图文件、NPC 对象或运行时 Object ID。
- `org.pokemmo.gameserver.services.shop`：购买、出售、角色和主背包行锁、带 owner 条件的
  资产写入及事务结果。允许依赖 jOOQ、数据库、商店不可变模型和道具规则；不发送封包，
  不解析 JSON，也不维护在线窗口状态。

| Package | 文件数 | 主要类型 | 作用 |
| --- | ---: | --- | --- |
| `mmo` | 1 | Util | 游戏服务器使用的轻量级通用工具类；属于游戏模块的历史兼容包。 |
| `org.pokemmo.gameserver` | 2 | ServerBaseData, Main | 游戏服务器入口、基础启动数据和运行配置。 |
| `org.pokemmo.gameserver.codecs` | 24 | BattlePreviewPokemonCodec, BattleFactionFieldCodec, BattleDebutPokemonCodec, BattleBassisInfoCodec, BattleStringCodec, BattleRoundResultCodec, BattlePublicFieldCodec, BattleTeamPokemonCodec 等 | 游戏对象、战斗对象、角色、宝可梦和消息的协议序列化。 |
| `org.pokemmo.gameserver.command` | 7 | GameCommandModule, CommandRegistry, CommandParser, CommandFeedbackService, CommandDispatcher, CommandContext, Command | 聊天命令接口、解析、权限、注册和反馈。 |
| `org.pokemmo.gameserver.command.commands` | 33 | SetMoveCommand, SetFormCommand, UnlockDexCommand, HideCommand, HealCommand, EventSpawnNpcCommand, EventDeleteNpcCommand, SpawnNpcCommand, ReloadShopsCommand, WinBattleCommand, SpectateCommand, MoveCloseCommand, MoveToCommand, MoveToNdsCommand, AddParticleCommand, SetBallTypeCommand, SetRibbonsCommand, SetShinyCommand, SetSecretShinyCommand, SetPokemonRarityCommand, SetIvsCommand, SetGenderCommand, SetNatureCommand, SetOtCommand, SetGiftCommand, PcCommand 等 | 各个独立 GM/聊天命令的业务入口；技能更换、管理员隐身、管理员队伍 HP/PP 恢复、管理员全图鉴点亮、宝可梦形态修改、普通/无条件事件 NPC 保存与生成、删除、地图调试和店铺配置重载。 |
| `org.pokemmo.gameserver.game.account` | 1 | AccountData | 在线账号数据模型。 |
| `org.pokemmo.gameserver.game.backgroundmusic` | 1 | BgType | 背景音乐类型定义。 |
| `org.pokemmo.gameserver.game.badge` | 1 | BadgeLevelTable | 徽章数量到等级上限的映射。 |
| `org.pokemmo.gameserver.game.battle` | 58 | BattleContext, BattleContextState, BattleContextComponent, BattleSwitchService, BattleMoveResolver, BattleDamageCalculator, BattleStatCalculator, BattleOutcomeResolver, BattleRoundSettlement, BattleCaptureService, Gen5CaptureCalculator, BattleBroadcastService, BattleRequestManager, BattleSpectatingService, WildTeam, TrainerTeam 等 | 战斗核心及职责组件：稳定入口与状态桥接、技能解析、第五世代伤害公式和固定伤害、属性计算、胜负判定、常规主动换人与阵亡濒死替补换人、野生捕获、回合结算、广播、玩家单挑请求、只读观战、阵营、队伍、天气和 PvP 数据。 |
| `org.pokemmo.gameserver.game.battle.effect` | 13 | CooperativeTeam, BattleWeatherChangeEffect, BattlePokemonStatChangeEffect, BattlePokemonRemainHpEffect, BattlePokemonMultiStatChangeEffect, BattlePokemonLeechSeedEffect, BattlePokemonLeechedSeedEffect, BattlePokemonForeWarnEffect 等 | 战斗中的天气、HP、能力变化、特性触发、寄生和消息效果。 |
| `org.pokemmo.gameserver.game.bin` | 1 | BinFileReader | 二进制资源文件读取。 |
| `org.pokemmo.gameserver.game.building` | 1 | BulidingType | 秘密基地、房屋等建筑类型。 |
| `org.pokemmo.gameserver.game.character` | 15 | PlayerVisibilityState, PlayerVisibilityService, CharacterManager, CharacterManagerState, CharacterManagerComponent, CharacterWorldLoader, OnlineTimeService, MapVisibilityService, CharacterMovementService, WildEncounterService, CharacterEventService, CharacterData 等 | 角色持久化模型、稳定入口与运行时状态桥接，世界加载、在线计时、管理员地图隐身与定向发送、地图可见性、移动、野外遭遇和事件职责组件。 |
| `org.pokemmo.gameserver.game.container` | 1 | PokemonContainerType | 宝可梦容器类型、容量和是否持久化等规则。 |
| `org.pokemmo.gameserver.game.entity` | 12 | EventNpcSpawnRequest, NpcSpawnAppearance, NpcDeleteService, NpcVisibilityService, NpcSpawnRequest, NpcSpawnService, SportType, PlayerEntity, NpcEntity, MovementType, EntityNameplateType, Entity | 玩家、NPC、实体位置、朝向、移动和头顶状态；普通/事件生成参数与视觉投影、自定义 NPC 保存后生成/停用后移除、可见会话与受保护的地图快照发送；不直接访问数据库/文件、执行商店结算或节日调度。 |
| `org.pokemmo.gameserver.game.events` | 5 | ServerEventType, GameEventType, GameEvent, EventRegionType, EventKindType | 地区事件、服务器事件、事件种类和角色事件标志。 |
| `org.pokemmo.gameserver.game.frame` | 1 | FrameType | 客户端游戏画面/帧类型。 |
| `org.pokemmo.gameserver.game.friend` | 1 | FriendManager | 在线好友请求的待处理状态、超时/断线清理和接受后的双方刷新；不直接访问数据库。 |
| `org.pokemmo.gameserver.game.giftshop` | 4 | GiftShopCategory, GiftShopItem, GiftShopManager, JsonGiftShopConfig | 礼品商城分类枚举、商品数据模型、JSONC 配置反序列化与物品管理器。 |
| `org.pokemmo.gameserver.game.gtl` | 13 | GtlActionResult, GtlListingEntry, GtlItemListing, GtlPokemonListing, GtlPurchaseHistoryEntry, GtlListingPage, GtlListingType, GtlRequestState, GtlRuleType, GtlListType, GtlFilterType, GtlGenderFilterType, GtlShinyFilterType | GTL 市场排序、性别/性格/等级/孵蛋年份/IV/努力值/满 IV 匹配数量/特性/蛋组/闪光/粒子/已学习技能/隐藏特性/头目类型/隐藏仅雌性种族/隐藏百变怪/形态变化已解锁/价格筛选、挂单类型、操作结果、宝可梦/物品混合列表页、购买历史模型和会话刷新状态。 |
| `org.pokemmo.gameserver.game.holiday` | 1 | HolidayType | 节日类型定义。 |
| `org.pokemmo.gameserver.game.instance` | 1 | GameInstance | 副本次数和下次可用时间的数据模型。 |
| `org.pokemmo.gameserver.game.interact` | 4 | SceneInteractionService, PcInteractionService, InteractType, GameInteractionType | 场景 A 键目标分派、NPC 脚本启动与清理、电脑菜单及 PC/GTL/邮箱入口、交互类型与客户端值映射。商店目标转交 game.shop；不直接访问数据库或执行商店结算。 |
| `org.pokemmo.gameserver.game.item` | 20 | CaptureBallRateManager, JsonItemInfoConfigs, JsonItemInfoConfig, ItemType, ItemRarityType, ItemManager, ItemInfo, ItemEffectType, ItemDataReader, ItemUseConfig, ItemUseRule, ItemUseManager, ItemUseHandlerType 等 | 道具基础资源解析、模型、效果、稀有度、限制、球种捕获概率和服务端使用规则；不再承载店铺定义或独立商店价格。 |
| `org.pokemmo.gameserver.game.kick` | 1 | KickType | 游戏内踢出原因和踢出类型。 |
| `org.pokemmo.gameserver.game.map` | 30 | MapFile, OpenMmoMapConfig, OpenMmoMapDataReader, MapLoadingOptions, NdsMapData, NdsTerrainPlane, SinnohMapConfig, SinnohMapData, WildEncounterManager, WarpEvent, Tile2D, PathNode, MapZoneType, MapWeatherType, MapSeasonType, MapLightingType, MapHashUtils, MapProtocolIds 等 | Kanto/Hoenn GBA 与 Sinnoh NDS 地图资源解析、矩阵地形、地图连接、坐标、传送、天气、季节、NPC 加载开关及脚本键保留、可选柜台坐标元数据校验、客户端地图 ID 字节序和各地区野外遭遇表查询。 |
| `org.pokemmo.gameserver.game.move` | 11 | PokemonMoveData, MoveTargetType, MoveManager, MoveLearnConditionType, MoveEffectType, MoveDataReader, MoveDamageType, MoveCategory 等 | 技能资源解析、技能模型、目标、类别、伤害、效果和附加属性。 |
| `org.pokemmo.gameserver.game.particleEffectType` | 1 | ParticleEffectType | 宝可梦粒子效果类型。 |
| `org.pokemmo.gameserver.game.npc` | 4 | CustomNpcDefinition, CustomNpcCodec, CustomNpcStore, CustomNpcCatalog | 自定义 NPC 固定序号与出生配置、版本 1/2 严格 JSONC、事件分类/闪光/缩放保存及恢复、独立文件原子保存、地图/占位校验；仅写 resource/npc/custom，不修改原生地图或承担商店/节日活动业务。 |
| `org.pokemmo.gameserver.game.permission` | 1 | PermissionType | 角色和命令权限等级。 |
| `org.pokemmo.gameserver.game.platform` | 3 | PlatformType, CpuBitType, CpuArchitectureType | 客户端平台、CPU 架构和位数信息。 |
| `org.pokemmo.gameserver.game.player` | 2 | ServerHeartBeatThread, InteractManager | 角色心跳线程和玩家交互管理。 |
| `org.pokemmo.gameserver.game.pokemon` | 46 | PokemonData, PokemonFormCatalog, CaptureSpeciesDataManager, PokemonRibbonMask, UpdatePokemonData, PokemonYieldConfig, PokemonYield, PokemonType, PokemonStatusType, PokemonStatType, PokemonStatConfig 等 | 宝可梦完整领域模型：图鉴、第五世代捕获率/基础亲密度、形态变化目录、属性、性格、性别、技能、特性、勋章位掩码、进化、生成、礼物和更新；`PokemonData` 负责在等级、IV、EV变化后重算派生最大 HP。 |
| `org.pokemmo.gameserver.game.pool` | 1 | GameSessionPool | 在线游戏 Session 池。 |
| `org.pokemmo.gameserver.game.region` | 2 | RegionType, RegionData | 地区类型和地区运行时数据。 |
| `org.pokemmo.gameserver.game.rom` | 2 | RomType, RomInfo | ROM 类型和客户端模型/ROM 信息。 |
| `org.pokemmo.gameserver.game.script` | 43 | InteractScript, LocalFormatStringScript, Script 及旧动作模型等 | 原生菜单/请求共用数据及旧脚本模型兼容；旧图已停止加载执行，新剧情运行时位于 game.story。 |
| `org.pokemmo.gameserver.game.shop` | 11 | ShopItem, ShopDefinition, ShopNpcBinding, ShopNpcBindings, ShopConfigLoader, ShopCatalog, ShopAccess, ShopRequest, ShopSession, ShopSessions, ShopService | 独立店铺 JSON/JSONC、NPC 绑定目标校验与冲突检测、旧地图绑定兼容、绑定与价格版本、NPC 访问、连续序号报价、窗口生命周期、重载和提交后的在线同步；不执行 SQL、不修改全局道具价格或在线 NPC 绑定字段。 |
| `org.pokemmo.gameserver.game.skin` | 2 | SkinType, SkinData | 角色外观数据和服装类型。 |
| `org.pokemmo.gameserver.game.string` | 7 | LocalStringFormatType, LoacalStringType, GameMassageString, GameLocalFormatString, FormatStringType, BattleStringType, BattleString | 游戏本地化字符串、格式参数和战斗文本。 |
| `org.pokemmo.gameserver.game.trainer` | 9 | TrainerTeamManager, TrainerTeamData, TrainerPokemonData, TrainerLevelType, TrainerBattleTeam, JsonTrainerTeamConfigs, JsonTrainerTeamConfig, JsonTrainerPokemonConfig 等 | 训练家队伍 JSON 配置、训练家宝可梦和训练家战斗队伍。 |
| `org.pokemmo.gameserver.game.trade` | 12 | TradeState, TradeSession, TradeSessionRegistry, TradeManager, TradeRequestRegistry, TradeOfferService, TradeMoneyOfferService, TradePokemonOfferService, TradeSettlementCoordinator, TradeStatusType 等 | 在线玩家交易状态、会话与请求注册、道具/金钱/宝可梦报价、结算协调、来源槽位、状态回执和锁定/确认生命周期；不直接执行数据库写入。 |
| `org.pokemmo.gameserver.protocol` | 1 | GameProtocol | 游戏 opcode 注册、Guice 注入和断线清理。 |
| `org.pokemmo.gameserver.protocol.packets.c2s` | 75 | ShopControlPacket, EmptyInteractPacket, StartTalkPacket, OpenMatchmakingFramePacket, CloseMatchmakingFramePacket, CancelPvpQueuePacket, RequestMatchmakingSpectateListPacket, MatchmakingSpectateBattlePacket, RequestPvpLeaderboardPacket, RequestTournamentListPacket, RequestTournamentDetailPacket, PvpStatisticsPacket, RequestPvpTierStatisticsPacket, RequestPvpPokemonDetailPacket, InteractPacket, FriendActionPacket, BattleRequestPacket, BattleSpectateRequestPacket, BattleSpectatingReturnPacket, ClaimEmailPacket, ClaimGTLListingPacket, CancelGTLListingPacket, PurchaseGTLListingPacket, CreateGTLListingPacket, RequestGTLPacket, RenamePokemonPacket, ReleasePokemonPacket, SortPokemonPacket, GtlRequestDecoder, GtlDecodedRequest 等 | 客户端到游戏服务器的请求封包；packet 负责生命周期，独立 decoder 负责字段解析和校验；`ShopControlPacket` 校验 0xDC 协商/报价买卖/关闭并转交商店组件。通用场景空包/明确 NPC 目标由 `EmptyInteractPacket`/`StartTalkPacket` 转交 `SceneInteractionService`，`InteractPacket` 将电脑选项转交 `PcInteractionService`；玩家单挑由 `BattleRequestPacket` 解析并交给 `BattleRequestManager`，正式观战由 `BattleSpectateRequestPacket` 严格解析 8 字节目标玩家 ID 并交给 `BattleSpectatingService`，C2S `0x35` 退出观战，PC 放生由 `ReleasePokemonPacket` 处理，PC/PARTY 自动排序由 `SortPokemonPacket` 处理，匹配赛/锦标赛界面由 `OpenMatchmakingFramePacket`/`CloseMatchmakingFramePacket` 处理，PvP 排行榜由 `RequestPvpLeaderboardPacket` 处理，匹配赛观战对局列表由 `RequestMatchmakingSpectateListPacket` 处理，匹配赛观战指定对局由 `MatchmakingSpectateBattlePacket` 处理，锦标赛列表与详情由 `RequestTournamentListPacket`/`RequestTournamentDetailPacket` 处理，PvP 统计界面及分级使用率请求由 `PvpStatisticsPacket`/`RequestPvpTierStatisticsPacket`/`RequestPvpPokemonDetailPacket` 处理。 |
| `org.pokemmo.gameserver.protocol.packets.s2c` | 105 | SendShopControlPacket, SendItemShopPacket, SendPvpStatisticsPacket, SendPvpTierStatisticsPacket, SendMatchmakingFramePacket, SendMatchmakingSpectateListPacket, SendTournamentListPacket, SendPvpLeaderboardPacket, SendGiftShopPacket, SendAddPokemonPacket, SendRemovePokemonPacket, SendBattleCapturePacket, SendInventoryItemAmountPacket, SendRemoveInventoryItemPacket, SendGtlActionResultPacket, SendGtlTradeHistoryPacket, SendCharacterSkinPacket, SendEmailClaimResultPacket, SendEmailDetailPacket, SendEmailListPacket, SendEmailResultPacket, SendGTLPacket, SendTradeWindowPacket, SendTradeStatusPacket, SendTradeItemPacket, SendTradeMoneyPacket, SendTradePokemonPacket, SendPcStatePacket, SendInteractPacket 等 | 游戏服务器到客户端的响应封包；负责字段编码和发送，包括 NPC 商店窗口、独立回收报价、协商/买卖回执和关闭、礼品商城列表、匹配赛界面初始化数据与各分级段位信息、PvP 排行榜、匹配赛可观战对局列表、锦标赛列表数据、PvP 统计窗口配置与分级使用率数据、邮件详情、列表、领取确认、野生捕获动画、背包道具数量/删除增量、GTL 购买记录、交易本侧的临时容器投影，以及 `MULTICHOICE` 的客户端菜单表选择器。 |
| `org.pokemmo.gameserver.protocol.packets.scriptsession` | 1 | InteractorEntityIdUtils | 实体 ID 兼容辅助；全注释的旧 ScriptSession 已删除。 |
| `org.pokemmo.gameserver.game.story` | 26 | StoryService, StoryCatalog, StoryProgram, StoryRuntime, StoryActionExecutor, StoryActionTransaction, StoryBattleStarter, StoryTrainerFactory, OakParcelStory, OakParcelProgress, OakParcelCatalog, ViridianCatchStory, ViridianCatchCatalog, PokemonCenterNurseStory, PalletOpeningService, PalletStoryLifecycle, PalletStoryCatalog, PalletStoryProgress, PalletStoryState, PalletStoryScene, PalletStoryNpcs, PalletStarterFactory, PalletStoryParty, PalletStoryBattle, StoryPokemonFactory | 通用剧情章节加载、触发器/节点/动作解释、原生开场/包裹/捕获教学/护士/尼比至枯叶主线、节点奖励事务编排、内联训练家战斗、登录/重连进度重读、存档投影、异步表现和战斗衔接。数据库奖励与领域副作用仍由 `services.story` 或已有领域服务承担。 |
| `org.pokemmo.gameserver.services.story` | 7 | PalletStoryStore, OakParcelStore, StoryInventory, ViridianCatchStore, StoryProgressStore, StoryEventFlagStore, StoryActionStore | 角色/账号行锁、阶段比较更新、初始领取及图鉴写入、首次战斗结算、包裹领取/交付及五球奖励、捕获教学完成 bit、早期徽章/故事位原子更新、地图事件标志、通用剧情动作幂等奖励事务；不发包、不清空旧存档。 |
| `org.pokemmo.gameserver.script` | 1 | ScriptManager | 资源加载编排；初始化独立 NPC、商店和开场/包裹剧情目录，不再加载旧 interact/event 图。 |
| `org.pokemmo.gameserver.services` | 1 | GameServerService | 领域服务组装与兼容门面；保留旧调用方 API，不承载具体数据库业务。 |
| `org.pokemmo.gameserver.services.character` | 1 | CharacterService | 角色查询、角色资产、跟随宝可梦和角色外观的数据库读写。 |
| `org.pokemmo.gameserver.services.friend` | 1 | FriendService | 好友请求接受后的关系持久化、好友删除和好友列表查询。 |
| `org.pokemmo.gameserver.services.gtl` | 19 | GtlService, GtlListingService, GtlQueryService, GtlPokemonListingQuery, GtlItemListingQuery, GtlOwnListingQuery, GtlPurchaseHistoryQuery, GtlFilterConditionBuilder, GtlPurchaseService, GtlClaimService, GtlCancellationService, GtlSearchRequest, GtlListingMapper, GtlPokemonSlotAllocator, GtlSchema 和操作结果类型 | GTL 上架、分类型查询、筛选构建、购买、取消、成交款领取、历史记录、映射和槽位分配；GtlService 仅作短期兼容门面。 |
| `org.pokemmo.gameserver.services.inventory` | 1 | InventoryService | 背包、拥有道具查询以及道具增删和数量变更。 |
| `org.pokemmo.gameserver.services.item` | 1 | ItemUseService | 道具使用效果、归属校验、事务扣除和宝可梦持久化。 |
| `org.pokemmo.gameserver.services.mail` | 6 | MailService, MailServiceStore, MailSendService, MailQueryService, MailClaimService, MailDeleteService | 邮件门面与分域操作：发送、查询、附件领取、删除；存储实现保留事务边界。 |
| `org.pokemmo.gameserver.services.pokemon` | 8 | PokemonService, PokemonServiceStore, PokemonAttributeService, PokemonItemService, PokemonContainerService, PokemonCaptureService, PokemonReleaseService, PokemonHealingService | 宝可梦属性、携带道具、野生捕获、PC 放生、PARTY HP/PP 恢复持久化、容器查询/换位、等级排序和空槽计算；PokemonService 仅作兼容转发。 |
| `org.pokemmo.gameserver.services.shop` | 6 | ShopTransactions, ShopPurchase, ShopSale, ShopItemPolicy, ShopTransactionResult, ShopRejectedException | 角色和主背包行锁、购买堆叠/回收扣除、金钱原子更新、owner 条件和提交结果；不解析配置或维护客户端窗口。 |
| `org.pokemmo.gameserver.services.trade` | 5 | TradeService, TradeValidationService, TradeAssetTransfer, TradeResultMapper, TradeRejectedException | 玩家交易事务及其请求校验、资产转移、拒绝原因和结果映射职责。 |
| `org.pokemmo.gameserver.services.world` | 1 | WorldService | 账号上下文、服务器节点、容器、事件、图鉴和实例信息。 |
| `org.pokemmo.gameserver.util` | 5 | SnowflakeIdGenerator, JsonUtil, Compression, BinFileReader, ArrayUtil | 游戏资源、数组、压缩、二进制、JSON 和雪花 ID 工具。 |

## server.login

| Package | 文件数 | 主要类型 | 作用 |
| --- | ---: | --- | --- |
| `org.pokemmo.loginserver` | 2 | Main, LoginState | 登录服务器入口和登录状态。 |
| `org.pokemmo.loginserver.login` | 2 | LoginKickType, KickReason | 登录拒绝和踢出原因。 |
| `org.pokemmo.loginserver.protocol` | 1 | LoginProtocol | 登录协议 opcode 注册。 |
| `org.pokemmo.loginserver.protocol.packets.c2s` | 3 | RequestGameNodeListPacket, LoginPacket, JoinGameServerPacket | 客户端到登录服务器的登录、节点列表和加入游戏请求。 |
| `org.pokemmo.loginserver.protocol.packets.s2c` | 7 | SendUpdateLoginCredentialsKey, SendReconnectPacket, SendMfaChangePacket, SendLoginKickPacket, SendGameNodeServerListPacket, SendGameNodeListPacket, LoginResultPacket | 登录结果、节点列表、重连、踢出和凭证更新响应。 |
| `org.pokemmo.loginserver.service` | 1 | LoginService | 账号、角色、节点、token 和登录上下文数据库业务。 |

## 阅读和修改顺序

新增功能通常先看 `server.game.protocol`、对应 `packets` 和 `codecs`，再看 `game` 领域包与 `services`。涉及账号/节点先看 `server.login` 和 `server.services`；涉及跨服聊天先看 `server.chat`、`server.redis` 和 `union.chat`；涉及字段变更先看 `db` schema 与生成 records。
