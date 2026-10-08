# `server.game` 游戏服务器模块

## 1. 入口和依赖

入口是 `org.pokemmo.gameserver.Main`，创建：

1. `TlsProtocol` 和 `RootKeyLoader`；
2. `Database`、`ServerService`、`GameServerService`；
3. `SnowflakeIdGenerator`；
4. `ScriptManager`，加载九类资源目录（包含各地区野外遭遇表）；
5. `RedisUtil`；
6. `GameProtocol`；
7. `Server(7777, ...)`。

当前入口内存在数据库连接地址、用户名和密码，属于部署风险，后续应迁移到环境变量或配置文件。

## 2. GameProtocol

`GameProtocol` 是游戏协议注册中心，当前注册角色选择、地图、移动、聊天、宝可梦容器、战斗、交互、背包、图鉴、装饰、心跳和大量服务端响应封包。玩家移动由 C2S `0x06` 驱动，成功的同地图移动向可见同频道玩家发送 S2C `0x0D` 动画；`0x11` 仅用于非法移动回拉等位置纠正，进入或离开可见范围分别发送 `0x05`/`0x08`。新玩家要等 C2S `0x05` 确认地图加载后才同步附近玩家，并将自身实体反向发送给已在线玩家。详细字段和未运行验证见 [`PLAYER_MOVEMENT_PACKET.md`](PLAYER_MOVEMENT_PACKET.md)。

特别重要的注册关系：

| opcode | 方向 | 作用 |
| ---: | --- | --- |
| `0x01` | c2s/s2c | 加入游戏世界 |
| `0x02` | c2s/s2c | 角色列表/角色选择准备 |
| `0x04` | c2s/s2c | 选择角色 |
| `0x08` | c2s | 聊天命令和普通聊天 |
| `0x09` | c2s | PARTY/PC 宝可梦换位，以及交易容器进出 |
| `0x0C` | c2s | 从 PC 放生宝可梦（8 字节 Object ID） |
| `0x0E` | c2s | 修改 PARTY/PC 宝可梦昵称 |
| `0x0F` | c2s/s2c | c2s 给 PC/PARTY 宝可梦装备或卸下携带道具；s2c 广播玩家 PvP 战斗状态 |
| `0x11` | c2s | 设置或取消跟随精灵 |
| `0x13` | s2c | 宝可梦容器 |
| `0x26` | c2s | 使用背包道具；目标为 PARTY 宝可梦或角色自身 |
| `0x15` | s2c | 按 Object ID 从指定容器移除宝可梦 |
| `0x16` | s2c | 宝可梦增量更新 |
| `0x18` | c2s | 选择宝可梦当前粒子效果 |
| `0x1B` | c2s | PC/PARTY 宝可梦自动排序（当前确认等级排序位） |
| `0x1C` | c2s | 更新 PC 箱子名称或顺序 |
| `0x20` | c2s/s2c | 长心跳 |
| `0x21` | c2s/s2c | 交互状态 |
| `0x25` | c2s/s2c | c2s 玩家单挑请求；s2c 播放背景音乐 |
| `0x22` | c2s | 按明确 NPC Object ID 和哈希开始对话 |
| `0x27` | c2s/s2c | 通用场景 A 键兜底请求（无目标参数） / PC 界面开关 |
| `0x2B` | s2c | 刷新角色跟随精灵 |
| `0x30` | c2s | 请求更换单个角色服装槽位 |
| `0x31` | s2c | 广播战斗结束，并等待客户端 `0x33` 回执清理战斗上下文 |
| `0x32` | c2s | 提交战斗行动；单打 MOVE 可省略目标字节，命令类型 `3` 表示从允许逃跑的战斗中退出 |
| `0x33` | c2s | 客户端确认战斗结束并清理服务端在线战斗状态 |
| `0x34` | c2s/s2c | c2s 请求观战（8 字节目标玩家 ID）；s2c 返回野外逃跑或玩家投降结果 |
| `0x35` | c2s/s2c | c2s 退出观战（无字段）；s2c 广播战斗中的宝可梦换位 |
| `0x44` | c2s | 请求打开 PvP 统计界面 |
| `0x46` | c2s | 打开匹配赛/锦标赛界面请求 |
| `0x47` | c2s/s2c | 关闭匹配赛界面请求 / 匹配赛与锦标赛初始化数据响应 |
| `0x48` | c2s | 匹配赛排队报名 |
| `0x49` | c2s | 取消匹配赛排队 |
| `0x4A` | c2s/s2c | 请求 PvP 匹配赛排行榜 / 下发 PvP 个人信息 |
| `0x4B` | c2s | 请求匹配赛观战对局列表 |
| `0x4C` | c2s/s2c | 点击观战指定对局 / PvP 匹配赛排行榜列表响应 |
| `0x4D` | c2s/s2c | c2s 请求指定月份分级 PvP 统计 / s2c 下发赛季配置 |
| `0x4E` | s2c | 匹配赛观战对局列表响应 |
| `0x4F` | c2s/s2c | c2s 请求 PvP 宝可梦详细配置 / s2c 下发分级定位与规则 |
| `0x5D` | s2c | 下发 PvP 分级宝可梦使用率统计列表 |
| `0x60` | c2s/s2c | c2s 添加或移除好友请求 / s2c 下发 PvP 统计窗口配置 |
| `0x23` | c2s | 兼容旧入口的交易宝可梦报价 |
| `0x23` | s2c | NPC 道具商店窗口；协商后以 flag `0x80` 附加报价和独立回收价 |
| `0x24`/`0x53` | c2s | 交易道具报价 |
| `0x50` | c2s/s2c | 交易取消、锁定、最终确认和窗口开关 |
| `0x51` | s2c | 交易状态回执 |
| `0x52` | c2s/s2c | c2s 金额报价、s2c 完整宝可梦报价 |
| `0x53` | c2s/s2c | c2s 道具报价、s2c 金额报价 |
| `0x54` | s2c | 交易道具槽位更新 |
| `0x5E` | s2c | GTL 近期交易记录 |
| `0x51` / `0x62` | c2s | 向附近玩家发送交易请求（官方/兼容入口） |
| `0x60` | c2s | 添加或移除好友请求（两个 UTF-16LE 字段） |
| `0x63` | s2c | 好友列表完整刷新（模式、条目数和好友元数据） |
| `0x70` | c2s/s2c | 打开礼品商城请求 / 礼品商城物品列表响应 |
| `0x75` | c2s/s2c | 锦标赛列表请求 / 锦标赛列表数据响应 |
| `0x77` | c2s | 请求锦标赛详情 |
| `0x90` | s2c | 完整角色外观刷新 |
| `0x93` | s2c | 单个角色服装槽位刷新 |
| `0x95` | c2s | 发送邮件及附件请求；事务写入邮件并转移资产 |
| `0x96` | c2s/s2c | 读取邮件详情并标记已读 / 发送结果 |
| `0x97` | c2s/s2c | 删除邮件请求 / 收件箱发件箱分页列表响应 |
| `0x98` | c2s/s2c | 收件人领取单个邮件附件 / 收件箱计数 |
| `0x99` | c2s/s2c | 请求邮箱分页列表 / 邮件详情响应 |
| `0x9A` | c2s/s2c | C2S 创建 GTL 挂单；S2C 邮件附件领取确认 |
| `0x9B` | c2s/s2c | GTL 交易行请求和列表响应 |
| `0x9C` | c2s/s2c | 购买 GTL 挂单 / GTL 操作结果 |
| `0x9D` | c2s | 取消自己的 GTL 宝可梦挂单 |
| `0x9E` | c2s | 批量领取自己已成交 GTL 挂单的款项 |
| `0x9F` | c2s | 请求当前角色的 GTL 购买记录 |
| `0xDC` | c2s/s2c | OpenMMO 商店扩展版本 1：协商、购买、出售、关闭及回执 |

新增封包必须同时完成 class、Codec、协议注册和处理流程；否则 `Protocol.decode` 会报告未找到 opcode。

## 3. 领域分层

### 3.1 `game.character`

`PlayerVisibilityState` / `PlayerVisibilityService` 管理 `//hide` 在线地图隐身，
按观察者权限分别决定是否显示，GM 阈值为 7，自己始终可见。
状态绑定角色 ID，不写数据库；同上下文换图/重连保留，新上下文默认可见。
完整玩家加载、地图转换、相邻地图加载和角色增量广播统一经过可见性过滤，
切换与发包共享角色的可见性监视器，避免切换后迟到的加载包重新显示角色。
隐藏先清除观察者的跟随显示再移除角色，恢复发送完整实体和交通状态；本人实体不移除。
详细行为和测试边界见 [`CHAT_HIDE_COMMAND.md`](CHAT_HIDE_COMMAND.md)。

`CharacterData` 保存角色持久化数据映射，`CharacterManager` 是稳定的在线角色入口，运行时字段由 `CharacterManagerState` 持有。世界初始同步由 `CharacterWorldLoader`、在线分钟持久化由 `OnlineTimeService`、地图会话可见性由 `MapVisibilityService`、移动与寻路由 `CharacterMovementService`、交互和事件由 `CharacterEventService`、野外遭遇由 `WildEncounterService` 分别负责；这些组件经 `CharacterManagerComponent` 读取当前 Session 和地图状态，避免重连后持有过期引用。成功移动后先执行剧情事件，未触发事件时才检查草丛或冲浪水面；隐身管理员不会触发野生遭遇，隐身期间会清空遭遇累计状态；野生宝可梦只存放在非持久化 `EVENT` 容器并交给既有 `WildBattle`，不会提前写入 PARTY/PC。`character.online_minutes` 按分钟持久化；角色选择后开始计时，在线期间每分钟写回完整分钟，重连或断线时再结算剩余完整分钟并按角色 ID 写回数据库，`CharacterCodec` 发送给客户端时转换为协议所需的秒数。新角色初始化在线时长为 `0`，不会预置固定的两小时。

### 3.2 `game.pokemon`

包含 `PokemonData`、图鉴数据、属性、技能、特性、性格、容器、生成和宝可梦管理。宝可梦修改必须同步：

```text
pokemon 表
  -> PokemonData 内存对象
  -> SendUpdatePokemonDataPacket 或 SendPokemonContainerPacket
```

`PokemonData.maxHp` 是由图鉴基础 HP、等级、IV 和 EV 派生的运行时值，不是独立数据库字段。
等级、IV 或 EV 变化时必须同步重算；战斗初始化使用重算后的最大 HP，避免在线对象继续携带旧等级的 HP 上限。

`personalityValue` 同时影响性别和性格，改动时必须使用统一的边界公式。

道具使用由 `UseItemPacket`（C2S `0x26`）进入 `GameServerService.useItem`，具体事务和效果
位于 `org.pokemmo.gameserver.services.item.ItemUseService`。`Item.bin` 提供官方基础字段，
`resource/item/ItemUse.jsonc` 只覆盖自行车和其他无法由基础字段表达的特殊行为。普通消耗品会先锁定
当前角色主背包道具和 PARTY 宝可梦，再保存效果、扣除数量、更新在线队伍并发送 `0x16`/`0x40`；
质子道具虽然在 `Item.bin` 中标记为 `NOT_USABLE`，仍会按其 `itemParticleEffectType` 追加到
`pokemon.particle_effects`，重复质子会拒绝且不会扣除道具；
自行车等角色系统道具只锁定角色并切换 `transportation`，不要求 `owned_item`，也不扣除道具，成功后
发送交通状态更新。任何目标归属、数量、容器、地区或效果不适用情况都会拒绝且不扣除普通消耗品。

附近玩家交易请求由 `RequestTradePacket` 接收 C2S `0x51`（官方）或 `0x62`（抓包兼容），读取 UTF-16LE 的目标玩家名称。
 `CharacterManager` 只在目标在线、同频道、同地图且双方不在对话或战斗状态时，通过 S2C
 `0x21 SendInteractPacket` 发送 `REQUEST_TRADE` 确认框，并在交互头后附带发起者名称。目标
 通过 C2S `0x21` 接受或拒绝后，`TradeManager` 建立在线交易会话，处理 `0x50` 锁定/确认、
 `0x09`/`0x23` 宝可梦、`0x24`/`0x53` 道具和 `0x52` 金钱报价。官方 `0x09` 将 PARTY/PC 槽位移动到
交易容器或撤回；最终由 `TradeService` 在事务中交换
 金钱、主背包道具和宝可梦所有权，并刷新双方 PARTY、PC、背包和玩家金钱。字段、边界及未验证
 的客户端展示语义见 [`TRADE_PACKET.md`](TRADE_PACKET.md)。

玩家单挑请求由 `BattleRequestPacket` 接收 C2S `0x25`，按 `f.Bs0` 的 UTF-16LE 名称、战斗格式、flags
和可选字段顺序严格解析。当前只接受单打格式 `0`；目标必须在线、同频道、同地图且双方有存活 PARTY
宝可梦，服务端通过 S2C `0x21 REQUEST_BATTLE` 显示确认框。目标以 C2S `0x21` 的 `type = 0/1` 拒绝或接受，
接受后 `BattleRequestManager` 创建共享 `PlayerBattle` 并分别发送 S2C `0x30` 初始化。请求 30 秒超时、
断线、拒绝或状态失效时清理；字段和抓包示例见 [`BATTLE_REQUEST_PACKET.md`](BATTLE_REQUEST_PACKET.md)。

玩家观战的正式入口是客户端右键菜单：目标状态为战斗中时，客户端将原生“挑战”项替换为观战回调，
发送 C2S `0x34` 和目标玩家 Object ID 的 8 字节 little-endian `long`。服务端的
`BattleSpectatingService` 只允许同频道、同地区、同地图且双方仍为 `IN_BATTLE` 的 `PlayerBattle`，观战者不能处于战斗、
交易或交互状态，每场战斗最多 16 名观战者。`BattleManager` 将观战者加入只读广播集合，初始化包使用
`playerFactionIndex = -1` 和 `selfTeamInDebutIndex = -1`，以只读视角编码双方队伍；攻击、状态、回合和宝可梦换位广播会
同步给观战者，观战者提交的战斗行动会被拒绝。客户端关闭窗口发送 C2S `0x35` 无字段包退出观战，断线、重连和战斗结束
同步清理。`//spectate <player_name>` 仅保留为兼容调试入口。字段和限制见 [`BATTLE_SPECTATING_PACKET.md`](BATTLE_SPECTATING_PACKET.md)。

### 3.3 `game.battle`

伤害计算由 `BattleDamageCalculator` 统一处理，基于第五世代公式，包含性格修正、物理/特殊有效攻防、属性相性、天气、暴击、随机数、STAB、固定伤害和多目标修正；命中失败不会扣除 HP。实现细节与当前未覆盖的招式专属效果见 [`BATTLE_DAMAGE.md`](BATTLE_DAMAGE.md)。

战斗包围 `BattleContext`、阵营、队伍、行动、天气、状态效果和 Codec。`BattleContext` 保持兼容入口，`BattleContextState` 只保存共享战斗状态；`BattleMoveResolver`、`BattleStatCalculator`、`BattleOutcomeResolver`、`BattleRoundSettlement`、`BattleSwitchService`、`BattleCaptureService` 和 `BattleBroadcastService` 分别处理技能、属性、胜负、回合、宝可梦换人（主动轮换与阵亡替补）、野生捕获和客户端广播，并通过 `BattleContextComponent` 访问共享状态。`BattlePokemonData` 是战斗态包装，不能直接当作持久化 `PokemonData` 使用。C2S `0x32` 的 `RUN = 3` 只允许 `WildBattle`：服务端发送 S2C `0x34 NORMAL_SUCCESS` 后，以逃跑玩家阵营作为 S2C `0x31` 的客户端展示阵营，避免把野生宝可梦显示为胜者；NPC/训练家战斗拒绝 RUN。`PlayerBattle` 使用独立的 `FORFEIT = 13`，发送 S2C `0x34 FORFEIT`，投降方为 `DEFEAT`、对方为 `VICTORY`。三种战斗最终都等待客户端 C2S `0x33` 清理在线战斗上下文。字段和边界见 [`BATTLE_RUN_PACKET.md`](BATTLE_RUN_PACKET.md)。野生战斗中，球道具的 `itemTargetId = 0` 解释为敌方当前宝可梦；球类通过 `Item.bin` 的 `itemBallType` 识别（非球编码为 `255/-1`），`Gen5CaptureCalculator` 组合 `CaptureSpecies.jsonc` 的种族捕获率、HP、状态、球种效果和临界捕获。普通捕获三次判定，正常失败结果严格为 `0/1/3`，成功为 `4`。捕获事务会扣除一个球；成功时优先写入 PARTY 空槽，否则写入 PC，并以 `CATCH_POKEMON` 结束战斗；失败时继续战斗。所有已扣球尝试使用 S2C `0x37` 播放专用捕获动画，再以 S2C `0x41` 更新剩余球数量或 S2C `0x43` 删除空堆叠，避免战斗内用 S2C `0x40` 替换完整背包；普通战斗道具动作的 S2C `0x39` 不用于捕获。

战斗行动 C2S `0x32` 的字段和边界见 [`BATTLE_COMMAND_PACKET.md`](BATTLE_COMMAND_PACKET.md)。单打 MOVE 可省略目标字节，服务端按战斗形式推导对手首个登场位；双打、多打等需要显式目标的战斗会拒绝缺少目标的请求。当宝可梦在战斗中阵亡且队伍中有其他存活宝可梦时，服务端向受击方发送 S2C `0x36`（`SendPokemonDiedPacket`）触发客户端弹出替补选人界面，向对手发送 S2C `0x3B`（`SendBattleWaitForPlayerActionPacket`）显示等待对方行动；NPC 阵营自动替补后，或受击方通过 C2S `0x32 SWAP` 选择替补后，服务端广播 S2C `0x35`（`SendSwapPokemonPacket`），等待客户端完成登场呈现后再推进至下一回合，广播 S2C `0xC4` 并向存活宝可梦发送 S2C `0x32`（`canAction=true`）；常规换人亦由 `BattleSwitchService` 统一维护，避免换人后登场槽位错误或提前结算。回合行动结算遵循严格的客户端呈现顺序：先通过 S2C `0x33` 广播技能释放、伤害及濒死倒下动作，随后由 `BattleRoundSettlement.settleFaintedPokemonsExp()` 向击败方先发送 S2C `0x16` 等级经验刷新包（供客户端在对战事件队列构建 LM 事件），再发送 S2C `0x79` 经验明细包（匹配 LM 并填充经验构成），最后由 `checkAndHandleBattleFinish()` 发送 S2C `0x31` 结束包，确保客户端 UI 队列先播放技能/受击动画，后弹出经验获得和升级文本。

### 3.4 `game.map`、`game.region`、`game.rom`

`//spawnnpc` 通过 `game.entity.NpcSpawnService` 在角色前方一格新增自定义 NPC，
先经 `game.npc.CustomNpcCatalog` / `CustomNpcStore` 原子保存到独立 `resource/npc/custom`，
再复用已有 S2C `0x12`；不写数据库或原地图文件。`CustomNpcDefinition` / `CustomNpcCodec`
负责稳定高序号的出生配置与严格 JSONC；`ScriptManager` 在原地图之后、商店之前恢复，
冲突/错误时保留原生 NPC 并禁止新增。`MapData.addEntity` 发布复制后的 NPC
字典，供并发地图加载和交互遍历；GBA/NDS 的 `loadArroundEntity` 只给尚无编号的 NPC
调用统一雪花生成器，避免新玩家加载地图时替换已有 NPC 的 Object ID。
当前地图跨频道共享，新增通知覆盖所有已加载该地图的会话，后来进入者沿用原加载路径。
六参数含义及限制见 [`CHAT_SPAWNNPC_COMMAND.md`](CHAT_SPAWNNPC_COMMAND.md)。

`//eventspawnnpc` 使用独立参数模型 `EventNpcSpawnRequest`，严格解析原客户端的
14 个基础参数和条件对数量。当前仅接入无条件手动新建，拒绝尚未实现的覆盖、
标志/附加条件和忽略重复。`NpcSpawnAppearance` 保留事件分类并将闪光/缩放投影到既有
S2C `0x12` 字段；分类不会自动启用节日活动。
`CustomNpcDefinition/CustomNpcCodec` 支持版本 1（普通）和版本 2（附加分类/视觉字段），
保存、重启与停用都保留这些字段。协议和边界见
[`CHAT_EVENTSPAWNNPC_COMMAND.md`](CHAT_EVENTSPAWNNPC_COMMAND.md)。

`//eventdeletenpc` 由 `game.entity.NpcDeleteService` 编排，只允许当前主地图中
`CustomNpcCatalog` 实际管理的自定义实体。商店目录写锁排除并发开店/成交，
文件内容比较后原子更新 `enabled=false`，在线标志失效并复制移除字典后释放锁，
再关闭该 NPC 的报价并广播已有 S2C `0x08`。固定序号和配置文件保留，原生 NPC 不变。
`NpcVisibilityService` 共用生成和地图快照发送并与删除在地图锁上串行化，
`CharacterWorldLoader` / `CharacterMovementService` 委托它发送 NPC，避免迟到快照重新显示目标。
参数、文件保护与未验证项见 [`CHAT_EVENTDELETENPC_COMMAND.md`](CHAT_EVENTDELETENPC_COMMAND.md)。

地图包负责地图数据、连接地图、NPC、坐标、玩家会话池和各地区野外遭遇表查询；区域包负责地区映射；ROM 包负责客户端模型或地图编号等协议字段。地图 JSON 文件名会写入 `MapData.mapKey`，供 `WildEncounterManager` 与上游地图键规范化匹配。地图资源来自 `resource/map`，按地图拆分的 GBA 遭遇文件位于各地图目录，神奥 NDS 遭遇表由 OpenMMO-DS 生成结果导入，未匹配的旧 FireRed 文件暂存于 `resource/encounter/unmapped`；数据来源和校验规则见 [`WILD_ENCOUNTERS.md`](WILD_ENCOUNTERS.md)。

地图读取使用 `openmmo老外` 风格的 `blockData + behaviorData` Base64 地图定义格式；
Sinnoh 使用 `OpenMMO-DS` 的 NDS `matrix + terrain chunks` 格式。旧的
`JSONC + 坐标 BIN` 格式不再是运行时输入。GBA 地图将连接、传送点和 NPC 放在同一张
地图定义中；Sinnoh 使用地区号 `3` 的 NDS 地图包，客户端从自己的 DS 数据绘制地形。
Hoenn 使用 Emerald 遭遇表，Sinnoh 使用 Platinum/OpenMMO-DS 的陆地和水面固定遭遇表；
移动触发目前支持草地、地下可行走地面和冲浪水面，鱼竿、雷达、群聚及时间变体数据暂不通过普通移动触发。
NDS 地图包的地图头字段按客户端协议写入 `mapId`、`bankId` 两个字节；
服务端命令和地图索引仍使用 `(bank << 8) | mapId`，两者不能直接按同一顺序写入封包。
地图事件脚本
默认关闭，NPC 默认开启，可通过 JVM 参数或环境变量切换：

```text
-Dopenmmo.map.events.enabled=true
-Dopenmmo.map.npcs.enabled=false
OPENMMO_MAP_EVENTS_ENABLED=true
OPENMMO_MAP_NPCS_ENABLED=false
```

地图 JSON 还会保存 `onTransitionScript`、`onFrameScripts`、`coordScripts` 和
`bgEvents`。旧事件开关仅影响这些元数据的注册，不会阻止地图传送；
新真新镇剧情由独立配置 `enabled` 管理，不靠打开这个开关来解释全部原版脚本。

### 3.5 `game.script`、`game.interact`

旧剧情 JSONC、`InteractManager.runScript` 和地图/角色内的旧开场分支已移除。
`InteractManager` 只保留菜单/请求共用的交互状态，旧 Script 数据类型作为清理调用兼容保留，
不再加载和执行旧图。通用 `InteractScript` 仍是原生对话/PC/交易请求的协议数据对象。

新模块 `game.story` 由 `StoryCatalog`、`StoryRuntime` 和 `StoryActionExecutor`
统一加载/解释章节内容，并负责检查点路由、原生表现、独立 NPC 投影、宝可梦构建、
队伍同步和首次战斗衔接；`services.story.PalletStoryStore` 保存资产及进度。
开场资源位于 `resource/story/kanto/pallet_town`，妈妈治疗可重复，完成玩家不重播一次性节点。
新增 `resource/story/kanto/oak_parcel/chapter.jsonc` 接续常磐市包裹、研究所交付、图鉴与五球奖励。
再由 `resource/story/kanto/viridian_city/catch_tutorial/chapter.jsonc` 接入常磐市捕获教学。
护士配置位于 `resource/story/kanto/pokemon_center_nurse/chapter.jsonc`。
`resource/story/kanto/early_route_to_vermilion/chapter.jsonc` 接入尼比至枯叶的早期主线，
包括道馆、月见山、华蓝、比尔、圣安奴号和马志士。
`StoryService` 统一路由五个通用章节；旧 `*Story` 类只保留资源、事务和战斗兼容适配，
不再作为生产流程编排入口。`OakParcelStore` 与 `StoryInventory` 将主背包变更和阶段 5/6 原子保存，
每次事务提交后才更新内存、背包、图鉴和个人 NPC 显示。`ViridianCatchStore` 按角色/账号锁定
关都 `story_line_flag` bit 4，仅在捕获战斗结果为 `CATCH_POKEMON` 时完成。
通用 JSONC 节点中的道具、扣除道具、奖励宝可梦、徽章和故事位由 `StoryActionTransaction`
与 `services.story.StoryActionStore` 在同一事务中提交，并用 `character_story_action`
记录节点幂等键；事务成功后才刷新在线对象和客户端。
任务包裹 `349` 不能交易/丢弃/普通使用，奖励固定为 `5004 × 5`；一次性节点完成后不重复领取。
捕获教学复用现有野生捕获协议；老人自动捕获演出仍未验证，尼比至枯叶主线已由
`early_route_to_vermilion` 章节接入。
宝可梦中心护士由通用 NPC 触发器匹配地图 key 以 `PokemonCenter_1F` 结尾、
脚本以 `EventScript_Nurse` 结尾的 NPC，复用原生 `0x21` 对话回执和客户端文本；
确认后调用带角色/训练家条件的 `PokemonHealingService` 恢复 PARTY 的 HP、PP 和异常状态，
提交后重读 PARTY 并发送完整容器刷新，不新增 opcode 或剧情存档字段。
见 [`PALLET_TOWN_STORY.md`](PALLET_TOWN_STORY.md)、[`OAK_PARCEL_STORY.md`](OAK_PARCEL_STORY.md)
、[`VIRIDIAN_CATCH_TUTORIAL.md`](VIRIDIAN_CATCH_TUTORIAL.md) 和
[`POKEMON_CENTER_NURSE.md`](POKEMON_CENTER_NURSE.md)。

`PalletStoryLifecycle` 负责登录/重连从数据库重新读取已提交的剧情进度，复用角色上下文也不沿用旧缓存；
战斗中保留在线状态，查询失败仅暂停剧情。`PalletOpeningService` 在阶段 0 的地图确认和移动入口
共用触发格判定，中文日志输出实际加载阶段、初始选择、地图坐标和忙碌状态。
`PalletStoryProgress.validateOpeningCheckpoint` 检查阶段 0..2 必须对应未选择值 `3`；
地图确认、移动与 NPC 开场先校验后进入 STORY，事务内再检查数据库值。
部分重置不自动纠正，错误直接列出字段，妈妈治疗不受此项校验阻止。

`SceneInteractionService` 处理 C2S `0x27`：根据在线地图、坐标和朝向先识别 NPC，再匹配
已确认的电脑；未知或未实现的背景目标只记录中性日志，不能按电脑拒绝。NPC 分支与 C2S `0x22`
共用入口，检查忙碌、每玩家投影后的可见性/位置及目标可交互状态；`MapFile` 保留资源脚本键。
有待办的包裹剧情先处理，其余进入商店，再转交普通章节对话。携带包裹和完成玩家仍可正常购物。
未实现的 NPC 不进入对话状态，结束/异常清理状态。
`PcInteractionService` 独立持有电脑菜单和 PC/GTL/邮箱入口，不在 packet 中实现业务。
`game.shop.ShopService` 在剧情事件开关之前按当前配置快照解析 NPC 对应店铺，
校验地图、忙碌状态、朝向和相邻/隔一格柜台的距离。常磐市和尼比市商店的 `(3,3)`
由 `interactionCounters` 加载为 `MB_COUNTER`，不改变碰撞位及客户端地图编码。
`ShopCatalog` 在地图和 `Item.bin` 就绪后扫描相邻 `resource/shop` 的独立 JSON/JSONC，
`ShopNpcBinding`/`ShopNpcBindings` 校验店铺 `npcs: [{map, entityIdx}]` 并构造不可变绑定索引。
开店和买卖共用解析：显式列表优先，省略列表才兼容原地图 `shopId`，空列表解除本店绑定。
地图不存在、NPC 序号不存在或跨店冲突时拒绝重载，价格与绑定一起发布；不回写在线 NPC。
商品使用配置买入/回收价，不修改全局道具价格。旧客户端仍只读；协商扩展后使用
SHOP 报价状态、连续请求序号和 `services.shop` 买卖事务。原 C2S `0x23/0x24`
交易兼容入口保留，扩展买卖使用单独的 `0xDC`。
字段、限制和运行回归见 [`NPC_SHOP_PACKET.md`](NPC_SHOP_PACKET.md)。
两种 NPC 入口及菜单回执沿用脚本长操作队列；方向映射、有限柜台/PC 数据和未验证范围见
[`PC_INTERACTION_PACKET.md`](PC_INTERACTION_PACKET.md)。

### 3.6 `game.item`、`game.move`、`game.trainer`

这些包把资源文件转换为内存数据，并向物品、技能和训练家战斗流程提供查询。`Item.bin` 提供球类型，`CaptureSpecies.jsonc` 提供第五世代种族捕获率和基础亲密度，`CaptureBall.jsonc` 提供可选的通用球种倍率覆盖；条件球效果与第五世代 `1/4096` 定点捕获公式由 `Gen5CaptureCalculator` 统一计算。`0x37` 捕获动画封包使用客户端服务端方向所需的“战斗选择器 + 球道具 ID + 结果”，成功时附带容器/槽位。

## 4. 命令系统

命令入口是 `protocol.packets.c2s.ChatPacket`。仅 `ChatType.NORMAL` 且内容以 `//` 开头的消息进入 `CommandDispatcher`，不会广播到普通聊天。

现有命令包括：

```text
setalpha
setshiny
setsecretshiny
setgender
setnature
setot
setgift
addparticle
setballtype
setribbons
addmonster
createitem
setivs
setha
setability
setevs
setmove
sethappiness
pc
reloadshops
spawnnpc
hide
eventdeletenpc
eventspawnnpc
winbattle
spectate
heal
```

命令类位于 `command/commands`，注册位于 `GameCommandModule`。数据库访问按领域分布在
`org.pokemmo.gameserver.services.character`、`org.pokemmo.gameserver.services.pokemon`、
`org.pokemmo.gameserver.services.inventory`、`org.pokemmo.gameserver.services.gtl`、
`org.pokemmo.gameserver.services.mail`、`org.pokemmo.gameserver.services.friend` 和
`org.pokemmo.gameserver.services.world`；
`GameServerService` 只负责组装这些服务并兼容旧调用方。

## 5. 服务层

服务层按明确领域拆分：`CharacterService` 负责角色和外观，`PokemonService` 作为兼容门面转发到
`PokemonAttributeService`、`PokemonItemService`、`PokemonContainerService`、`PokemonCaptureService`，
`PokemonReleaseService`，
`PokemonHealingService` 负责按训练家锁定 PARTY、恢复 HP/PP 并返回提交后的在线快照，
`InventoryService` 负责背包道具，GTL 由 `GtlListingService`、`GtlQueryService`、`GtlFilterConditionBuilder`、
`GtlPurchaseService`、`GtlClaimService` 和 `GtlCancellationService` 分域处理，其中宝可梦、物品、本人挂单和历史记录查询各有专用查询类，`GtlService` 仅保留兼容转发；邮件由
`MailSendService`、`MailQueryService`、`MailClaimService`、`MailDeleteService` 分域处理，
附件资产转移、邮箱计数和收件箱/发件箱分页，`FriendService` 负责好友关系切换和列表查询，
`WorldService` 负责节点、容器、事件、图鉴和实例数据。
`services.shop.ShopTransactions` 按角色、owner 和主背包条件锁定数据，
由 `ShopPurchase`/`ShopSale` 在同一事务中改变金钱和道具。成功后由 `ShopService`
更新在线金钱并发送背包、金钱和结果；失败回滚，提交状态不明时关闭报价而不自动重试。
`//reloadshops` 校验全部文件和 NPC 绑定后切换配置版本，关闭旧报价，失败保留旧配置；
移动、转向、换图、断线和重连统一清理商店状态。
常见写入采用“按主键 + trainer/owner 条件”的方式，避免把队伍位置误当作数据库 ID。

好友操作由 `FriendActionPacket` 接收 C2S `0x60`。目标已有关系时，`FriendService` 删除单向关系并通过
`SendFriendListPacket` 发送 S2C `0x63` 模式 `0` 的完整列表；目标没有关系时，服务端只向在线目标发送
S2C `0x21 REQUEST_FRIEND` 请求 UI，不提前落库。目标通过 C2S `0x21` 接受后，`FriendService` 在事务中
写入双方关系，再分别刷新 A、B 的 `0x63` 列表；拒绝、超时和断线不会产生数据库记录。角色世界加载和
重连也会从数据库读取好友列表。好友名称和添加时间来自数据库，在线标记实时查询 `GameSessionPool`，
客户端未确认的元数据字段暂以 `0` 填充，详见 [`FRIEND_PACKET.md`](FRIEND_PACKET.md)。

`MailService.sendMail` 在一个数据库事务中锁定发送者、收件人和所有附件。道具完整堆叠直接转入
收件人的 `mail` inventory，部分堆叠生成新的 Object ID；精灵从 PARTY/PC 移入 `mail` 容器；金钱
写入 `mail_money_attachment` 并从发送者 `character.money` 扣除。失败时整体回滚。`0x99` 请求通过
`MailService.getMailList` 返回每页 10 条，`0x96` 只允许邮件双方读取详情并在收件人打开时标记已读，
随后通过 `SendEmailDetailPacket` 返回正文和收件箱附件；收件人通过 `0x98` 按详情槽位和附件类型在事务中领取单个附件，道具进入主背包并尽量合并普通堆叠，选中的道具/精灵/金钱附件标记 `claimed = true`，然后刷新金钱、背包、PARTY/PC、邮箱计数、列表页和详情。`0x97` 删除请求只有在没有未领取附件时才允许执行。发件箱详情在公共字段后结束，收件箱详情追加附件数量和附件内容。

角色换装由 `ResetCharacterClothesPacket` 校验槽位和 owned item 归属，`ownedItemId = 0` 作为客户端卸下当前槽位的保留值，调用 `CharacterService`（经 `GameServerService` 兼容门面）持久化具体外观字段，再更新在线 `SkinData`，最后通过 `SendCharacterSkinPacket` 向本人及同地图、同频道玩家发送 `0x93`。普通服装同时写入 skin/color 字段；自行车槽位 `BIKE = 11` 只写 `character.bike`，并使用无颜色保留值同步客户端。字段和位打包规则见 [`CHARACTER_CLOTHES_PACKET.md`](CHARACTER_CLOTHES_PACKET.md)。

精灵跟随由 `ChangeFllowPokemonPacket` 校验队伍位置或宝可梦 Object ID，拒绝非当前队伍宝可梦和蛋，持久化角色 follower 字段并更新在线 `PlayerEntity`，最后通过 `SendSetFollowPokemonPacket` 向本人及可见地图内同频道玩家发送 `0x2B`。字段和 rarity 位规则见 [`FOLLOW_POKEMON_PACKET.md`](FOLLOW_POKEMON_PACKET.md)。

精灵面板的粒子选择由 `SelectPokemonParticleEffectPacket` 接收 C2S `0x18`，校验 9 字节 payload、角色所属的 PARTY/PC 宝可梦和已拥有的粒子索引。`0xFD` 随机选择会持久化为客户端的 `-3` 随机模式；普通容器和 `0x16` 增量编码时，`PokemonData` 只临时投影一个已拥有的正粒子 ID，避免客户端普通显示路径清除粒子，数据库和在线对象仍保留 `-3`。客户端战斗粒子播放器不会处理 `0xFD`，因此战斗队伍、出场和换位 Codec 会从已拥有的可渲染粒子中解析一个正 ID；候选排除 `0..3` 保留类型，并兼容旧数据中的质子道具索引；同一只宝可梦在一场战斗中首次编码后复用该 ID，退出战斗时清除缓存，在线对象和数据库仍保留 `-3`。字段和 `0xFF` 清除选择兼容规则见 [`POKEMON_PARTICLE_SELECTION_PACKET.md`](POKEMON_PARTICLE_SELECTION_PACKET.md)。

宝可梦携带道具由 `UpdatePokemonItemPacket` 接收 C2S `0x0F`，固定读取宝可梦 Object ID、
携带道具索引和 PC/PARTY 容器类型。服务端按 `trainer_id + pokemon.id + container_id` 锁定目标，
验证 `Item.bin` 的 `itemCanGiveToPokemon`、主背包归属和数量，在一个事务中扣除新道具、
返还旧携带道具并更新 `pokemon.item`。成功后通过 S2C `0x16` bit `256` 和完整 `0x40`
主背包刷新客户端；未携带道具在数据库中统一保存为 `-1`，读取历史 `0` 时会先规范化；交易期间、已报价宝可梦、容器不匹配或数量不足时拒绝请求。字段和抓包
示例见 [`POKEMON_ITEM_PACKET.md`](POKEMON_ITEM_PACKET.md)。

宝可梦重命名由 `RenamePokemonPacket` 接收 C2S `0x0E`，读取宝可梦 Object ID 和以
UTF-16LE 终止符结尾的昵称。服务端限制昵称为客户端允许的最多 16 个字符，按
`pokemon.id + trainer_id` 更新数据库；成功后更新在线 PARTY 对象，并发送完整 S2C `0x14`
使 PARTY 或 PC 客户端容器按 Object ID 刷新。空昵称用于恢复默认种族名称。交易期间、已报价
宝可梦、未知宝可梦和数据库失败均只拒绝请求，不发送伪造成功包；业务异常会保留 Session。非法字符或截断字符串属于协议解码错误，按统一解码器策略处理。字段和
抓包示例见 [`POKEMON_RENAME_PACKET.md`](POKEMON_RENAME_PACKET.md)。

宝可梦放生由 `ReleasePokemonPacket` 接收 C2S `0x0C`，固定读取 8 字节 little-endian
Object ID。服务端只允许当前角色所属的 PC 宝可梦，拒绝交易中或已报价目标；事务成功后将
记录移到 `deleted` 容器，并通过已有 S2C `0x15` 按 Object ID 移除 PC 客户端条目。非法长度、
非正 Object ID、非 PC 容器、未知目标和数据库失败均拒绝；协议解码错误按 `Session` 的统一错误
路径处理，业务拒绝会保留 Session。字段与抓包示例见 [`POKEMON_RELEASE_PACKET.md`](POKEMON_RELEASE_PACKET.md)。

GTL 上架由 `CreateGTLListingPacket` 接收 C2S `0x9A`。宝可梦分支在同一事务中锁定角色
和目标宝可梦，写入 `gtl_listing`、扣除上架费并把宝可梦从 PC 移入 `auction` 容器；
随后 S2C `0x9C` 返回结果、`0x0C` 刷新金钱，并通过 `SendRemovePokemonPacket` 的
`0x15` 在已打开的 PC 控件中按 Object ID 删除对应精灵。物品分支校验主背包归属、数量、
账号绑定状态和可交易性；普通物品使用 `ItemManager.isTradeableForExchange` 的客户端兼容判定，
不直接依赖 `ItemData.isTradeAble`（该字段是 `Item.bin` 打包字节的一部分），可穿戴时装使用
`SkinType` 确认的 item index 到 addon ID 映射，以兼容不完整的物品元数据。完整堆叠移入 `void`，
部分堆叠生成新的 Object ID；成功后用 `0x40 SendInventoryPacket` 完整刷新主背包。
`0x9B`
支持分页返回公开宝可梦挂单，以及当前角色自己的活跃和已售挂单。
`PurchaseGTLListingPacket` 接收 C2S `0x9C`，按挂单类型分派宝可梦或物品购买。宝可梦
分支在事务中校验挂单、余额和 PC 容量，扣除买家金钱、把宝可梦转入买家 PC 并将挂单
标记为已售，随后通过 `0x0C` 和 `0x14` 同步买家客户端。购买成功不发送 S2C `0x9C`，
因为客户端会把其成功码显示为“上架成功”；失败时仍发送 `0x9C` 拒绝结果。物品分支锁定卖家 `void`
背包中的 `owned_item`，支持完整或部分数量购买，扣除 `unit_price * amount`，转移或拆分
道具记录，全部售出后将挂单标记为已售，并通过 `0x0C`、`0x40` 和可用时的 `0x9B` 刷新
买家客户端。`CancelGTLListingPacket` 接收 C2S `0x9D`，只允许卖家本人取消活跃且未成交
的宝可梦挂单，将宝可梦从 `auction` 容器返还到原 PC 槽位（冲突时选择空槽），并通过
`0x9C` 操作结果和 `0x14` PC 增量包刷新客户端。每次 `0x9B` 查询会在 Session 中保存
最近的请求序号、列表类型、排序类型、页码、宝可梦性别、最小/最大价格、最低/最高等级、孵蛋年份时间范围、最低/最高 IV、最低/最高努力值、至少匹配的满 IV 数量、特性 ID、隐藏仅雌性种族开关、隐藏百变怪开关、形态变化已解锁开关、蛋组、粒子、已学习技能、隐藏特性、头目类型、宝可梦图鉴 ID 和物品索引 ID 搜索集合；
`NEWEST`、`EARLIEST`、`LOWEST_PRICE` 和 `HIGHEST_PRICE` 会转换为稳定的 SQL 排序，
操作后的主动页面刷新也会沿用原排序。规则 `0` 会按数量读取全部索引，并按列表类型在
宝可梦或拥有道具关联查询中应用相同筛选。
规则 `1` 将客户端的 `0/1/255` 分别映射为雄性、雌性和无性别，并结合图鉴
`genderRatio` 与 `personality_value` 低 8 位生成 SQL 条件；规则 `9/10` 将非负价格分别
转换为 `unit_price >= minPrice` 与 `unit_price <= maxPrice`（同时存在时校验范围）；规则 `2` 将客户端的 `0..24`
性格索引转换为 `personality_value` 的 floor-mod SQL 条件；规则 `3/4` 按 `level_value` 分别应用最低/最高等级（`1..100`，包含边界）；规则 `5/6` 按客户端属性索引和 `0..31` 的 IV 值读取 `iv_values[index + 1]`，分别应用最低/最高 IV；规则 `7` 按客户端蛋组索引匹配
图鉴蛋组；规则 `8` 将 `0/1/2/3` 分别映射为
任意闪光、秘密闪光、普通闪光和不闪光，并使用 `pokemon.is_shiny`/`pokemon.is_secret` 组合
条件；规则 `12` 的 `0..38` 按 `particle_effects` 数组包含关系筛选指定粒子，`0xFC` 则筛选粒子数组非空的宝可梦；规则 `13` 的 `0/1`（有隐藏特性/无隐藏特性）按 `has_hidden_ability` 筛选；规则 `14` 的 `0/1`（头目/普通宝可梦）按 `is_alpha` 筛选，`2`（命中注定的相遇）在当前无明确数据字段时返回空结果；规则 `16` 按 `moves` 数组包含关系筛选当前已学习技能；规则 `19` 按六项 IV 中值为 `31` 的数量筛选；规则 `20` 按图鉴能力 ID 到 `pokemon.ability` 槽位的映射筛选；规则 `21` 排除图鉴 `genderRatio = 254` 的雌性限定种族；规则 `22` 排除图鉴 ID `132` 的百变怪；规则 `24` 因缺少客户端稀有度解锁位的数据库字段而返回空的宝可梦结果，混合列表只保留物品；规则 `33` 读取 `0..9` 的 `SkinType` 时装部位，按 `owned_item.item_index_id` 的普通区间及帽子扩展区间筛选物品。性别、价格、等级、IV、蛋组、性格、闪光、粒子、已学习技能、隐藏特性、头目、规则 `19`、规则 `20`、规则 `21`、规则 `22`、规则 `24` 和规则 `33` 都会保存在 Session 请求状态中，
供上架、购买、取消、领取后的当前页面刷新复用。`0x9B` 已支持公开物品页和宝可梦/物品混合的
“我的挂单”，物品条目写入索引 ID 与颜色字段。物品取消和过期回收仍未实现，详见
[`GTL_PACKET.md`](GTL_PACKET.md)。

`RequsetTradeHistoryPacket` 接收无 payload 的 C2S `0x9F`。它要求角色已经加载，通过
`GtlPurchaseHistoryQuery` 查询当前角色的 `gtl_trade_history` 购买记录，并发送 S2C `0x5E`
`SendGtlTradeHistoryPacket`。每个 GTL 成交编码为一组：发送成交金额、收到宝可梦或物品；
空结果仍发送合法的空组列表。购买事务将历史行与扣款、资产转移和挂单更新置于同一事务，
避免客户端记录与数据库资产状态脱节。

`ClaimGTLListingPacket` 接收 C2S `0x9E`，按 `unsigned byte count + count * long LE`
读取待领取挂单 ID。`GtlClaimService` 在一个事务中锁定卖家和全部指定挂单，要求每条挂单归
当前卖家、状态为已售且成交数量有效；累加 `unit_price * sold_amount` 后更新角色金钱，并将
挂单标为 `status = 3` 已领取以防重复结算。成功通过 `0x9C`、`0x0C` 和可用时的当前
`0x9B` 页面刷新同步客户端；任一 ID 无效、重复、非本人、未售、已领取或金额溢出时整体拒绝。

PC 界面命令 `//pc` 由 `PcCommand` 处理。服务端先发送 PC 容器和箱子信息，再通过
`SendPcStatePacket` 的 `0x27 01` 通知客户端调用 `BU.Nc0(true)` 开窗；`0x27` 的客户端
方向仍保留原有 `EmptyInteractPacket` 注册。字段、客户端映射和验证方式见
[`CHAT_PC_PACKET.md`](CHAT_PC_PACKET.md)。常磐市宝可梦中心一楼坐标
`(0, 5, 4, 11, 2, 0)`、`(0, 5, 4, 11, 1, 2)` 或 `(0, 5, 4, 11, 2, 2)` 朝上的电脑位置，在通用 C2S `0x27` 分派到电脑后打开 `MULTICHOICE` 菜单，
并接入 PC、交易行和邮箱入口，详见 [`PC_INTERACTION_PACKET.md`](PC_INTERACTION_PACKET.md)。

客户端通过 `UpdatePcBoxInfoPacket` 的 C2S `0x1C` 提交箱子名称或顺序。服务端会按
`11 + pcBoxExpansionNumber` 校验箱子总数、编号范围和重复编号；`SendBoxInfoPacket`
也使用相同的动态箱子数量，避免扩容角色收到固定 11 个箱子的旧数据。当前 schema
没有箱子偏好字段，因此名称和顺序尚不跨登录持久化。

宝可梦换位由 `ChangePokemonPosPacket` 接收 `0x09` 的一个或多个源/目标槽位，`PokemonService` 在事务内同时计算源宝可梦到目标槽位的映射，并始终使用 `trainer_id` 限定更新。只有原本存在 PARTY 宝可梦且操作会把 PARTY 清空时才拒绝请求，空 PARTY 不会阻止 PC 内部换位。事务成功后重建在线 PARTY 数组，并为每只实际换位的宝可梦发送 `0x16` bit `64` 位置增量。已打开的 PC 不再接收会替换其槽位控制器的 `0x13`；请求失步且涉及 PC 时，服务端先关闭窗口，发送完整 PARTY/PC 快照，再重开窗口。详见 [`POKEMON_POSITION_PACKET.md`](POKEMON_POSITION_PACKET.md)。

PC 自动排序由 `SortPokemonPacket` 接收 C2S `0x1B`。客户端 `f.Sx0` 的请求包含容器类型、箱子编号数组和小端排序位；当前仅确认 `0x0001` 为等级排序。服务端拒绝未知排序位、交易期间或包含报价宝可梦的请求，在 `PokemonContainerService` 事务中按 `trainer_id` 锁定目标箱子，按等级升序重新分配实际存在的宝可梦，再通过 `0x16` 位置增量刷新客户端。字段和未验证范围见 [`POKEMON_SORT_PACKET.md`](POKEMON_SORT_PACKET.md)。

战斗内调试命令 `//winbattle` 由 `WinBattleCommand` 调用 `BattleContext.forceVictory`，将
当前会话所在阵营设为胜利、对方阵营设为失败，并复用现有 `0x31` 战斗结束广播。
`BattleManager` 不会被命令提前清除，仍由客户端的 `0x33 BattleFinishSuccessPacket`
回执完成清理。该命令不模拟敌方宝可梦倒下，不补发经验或额外奖励。详见
[`CHAT_WINBATTLE_COMMAND.md`](CHAT_WINBATTLE_COMMAND.md)。

`setot` 按 PARTY 槽位修改 `pokemon.ot_name`，并允许把 `{STRING_5684}`、`{STRING_5685}` 等客户端本地化 token 原样持久化。`setgift` 是单向、幂等的 Gift Ribbon 写入，将 `normal_ribbon[2]` 设为 `true`；两者都使用宝可梦 ID 与训练家 ID 双重条件更新数据库，再同步在线内存、`0x16` 增量封包和完整 PARTY 容器。详见 [`CHAT_SETOT_PACKET.md`](CHAT_SETOT_PACKET.md) 和 [`CHAT_SETGIFT_PACKET.md`](CHAT_SETGIFT_PACKET.md)。

`addparticle` 追加 `pokemon.particle_effects`，`setballtype` 修改 `pokemon.ball_type`，`setribbons` 将客户端 64 位 mask 拆分并原子更新 `contest_ribbon` 与 `normal_ribbon`。在线目标会同步内存、对应 `0x16` flag 和完整 PARTY 容器；`setballtype` 也支持离线目标持久化。详见 [`CHAT_ADDPARTICLE_PACKET.md`](CHAT_ADDPARTICLE_PACKET.md)、[`CHAT_SETBALLTYPE_PACKET.md`](CHAT_SETBALLTYPE_PACKET.md) 和 [`CHAT_SETRIBBONS_PACKET.md`](CHAT_SETRIBBONS_PACKET.md)。

`setform` 支持按队伍槽位修改与查看宝可梦形态（`form_type` 0..255），并提供 `//setform list` 子命令查看队伍形态概览及已知形态变化分支。落库 `pokemon.form_type`，下发 `0x16`（修复 short LE 图鉴编号）与 `0x14` 容器刷新，若处于跟随状态还会重新计算跟随稀有度标识并向视野广播。详见 [`CHAT_SETFORM_COMMAND.md`](CHAT_SETFORM_COMMAND.md)。

`unlockdex` 由管理员权限执行，支持自身或指定玩家点亮全图鉴（含前五代及 Gen 6~9 自定义宝可梦），持久化 `pokemon_dex` 的 4 层位图并下发 `SendLoadDexPacket` 刷新客户端。详见 [`CHAT_UNLOCKDEX_COMMAND.md`](CHAT_UNLOCKDEX_COMMAND.md)。

## 6. 客户端封包层

训练家初始化 `0x30` 的队伍头必须消费类型、容量、地区、训练家引用 short LE 和道具次数 byte。
客户端原 `f.iv_1.LPt4` 少读最后一字节已按 JASM 修复，服务端 `BattleTeamInfoCodec` 不改线格式。
`PalletStoryBattle` 在发布战斗引用前检查编码，初始化抛异常时清理引用并保留领取进度；
详见 [`BATTLE_INIT_PACKET.md`](BATTLE_INIT_PACKET.md)。

`codecs` 将领域对象编码为客户端协议。重点对象：

- `PokemonCodec`：完整宝可梦容器记录；
- `UpdatePokemonDataCodec`：按 bit flag 增量更新等级、能力、EV、性格、特性、稀有度等；
- `PokemonRibbonMask`：在数据库勋章数组与客户端 64 位 ribbon mask 之间转换；
- Battle 系列 Codec：战斗预览、出场、队伍和回合结果；
- `CharacterCodec`、`SkinCodec`：角色和外观；
- `ItemCodec`：拥有道具。

同一属性如果同时影响多个客户端视图，优先发送增量包，并在必要时发送完整容器或角色数据刷新。

客户端 `f.OM` 会在每次 `0x16` 更新后无条件写回包对象中的 OT，而该值在 bit `32768` 缺失时默认为空字符串。为保护原训练家显示，`UpdatePokemonDataCodec` 对所有 `0x16` 封包强制附带 bit `32768`、四个可回忆技能槽和当前 OT；该客户端行为已通过 Recaf JASM 验证。

位置更新使用同一 `0x16` 封包的 bit `64`，字段顺序为 `containerType: byte`、`containerPosition: short LE`。目标槽位已有宝可梦时，移动者和被交换者都必须分别收到一次位置增量。

## 7. 开发注意事项

- 队伍位置使用服务端数组的 `0..5` 下标。
- `container_position` 是持久化位置，在线数组可能因重连或移动暂时失步。
- `GameSessionPool` 只适合查找在线目标；离线目标必须从数据库读取。
- `CommandDispatcher` 当前会把运行时异常转成简单失败反馈，调试时需要同时查看服务端日志。
- 资源路径依赖项目根目录作为工作目录。
- 服务端停机广播：`Main` 注册了 JVM Shutdown Hook，关服时通过 `GameSessionPool.broadcastShutdown()` 向所有在线客户端广播 `SendKickInGamePacket(KickType.SERVER_SHUTDOWN)`（Opcode `0xFF`），确保客户端收到维护关服提示并返回登录界面。
