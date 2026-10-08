# `//spawnnpc` 自定义 NPC 生成与独立持久化

## 1. 用法

```text
//spawnnpc <外观编号> <脚本偏移> <外观地区> <移动类型> <横向活动范围> <纵向活动范围>
```

这是 GM 命令，支持客户端原版 NPC Tool 的 Spawn 按钮，不需要修改客户端。
六个参数不是地图坐标；地图和生成位置取自执行命令的在线角色。
从本次持久化改造起，六参数命令默认保存到独立 `resource/npc/custom`，不是临时生成。
首次部署需自行编译并重启游戏服；之后游戏内生成的 NPC 自动保存，重启自动恢复。

例如，在地图内面对一格空地：

```text
//spawnnpc 68 0 0 0 0 0
```

表示使用外观地区 0 的 68 号模型，静止，无脚本，活动范围为 0。
生成在角色前方一格，高度沿用角色高度，初始朝向面对角色。
日志中的 `//spawnnpc 0 0 0 0 0 0` 也可通过参数校验，外观编号 0 不会被改成其他模型；
该模型的实际显示依赖客户端资源，不能仅凭全零抓包保证外观效果。

| 参数 | 范围/含义 |
| --- | --- |
| 外观编号 | `0..10000`，原版 NPC Tool 的 Sprite ID，不是 NPC 实例 ID |
| 脚本偏移 | 当前只支持 `0`；非零明确拒绝，不能把它当成 shopId 或 Java 脚本名称 |
| 外观地区 | `0,1,2,3,4,10`，原版 Sprite Region ID，独立于角色所在地区 |
| 移动类型 | 根据当前地图地区校验原版普通移动值，静止为 `0` |
| 横/纵活动范围 | 各为 `0..4`，不是生成坐标 |

当前地图支持关都、丰缘、神奥。移动类型按原始值下发，不使用服务端 `MovementType`
的枚举序号替代，不额外实现 NPC 自主寻路：

- GBA 地图：`0,1,2,3,6,8,13,14,15,16,17,18,19,20,21,22,23,24`。
- NDS 地图：`0,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20`。
- 原工具中的特殊事件 AI（如 GBA `98..100`、NDS `121/123..127`）暂不支持，明确拒绝。

服务器没有客户端全部模型的可用性目录。这里只校验数值范围，不保证任意编号都有可显示的模型。
非零 ROM 脚本偏移、事件条件、永久原地图编辑
不属于本次命令，不能宣称已支持。
事件工具的无条件新建已由独立
[`//eventspawnnpc`](CHAT_EVENTSPAWNNPC_COMMAND.md) 接入，保存事件分类和视觉参数，
不代表已支持整套节日活动或有条件覆盖编辑。
已保存的自定义 NPC 可通过独立命令
[`//eventdeletenpc <Object ID>`](CHAT_EVENTDELETENPC_COMMAND.md) 永久停用并立即移除；
该命令不能删除原生 NPC，且保留独立文件和固定序号。

## 2. 独立保存与商店绑定

- NPC 进入当前地图的在线内存，向当前已加载该地图的会话发送已有 `0x12` NPC 包。
- 当前地图数据跨频道共享，所以该 NPC 对本游戏服进程中该地图的所有频道有效，
  不是只发给 GM 或仅当前频道。连接地图中已经能看到该地图的玩家也接收通知。
- 后来进入地图、重连、离开后返回的玩家，沿用地图实体加载路径获取该 NPC。
- 每只 NPC 写到 `resource/npc/custom/<地区>/<地图名>/npc_<序号>.jsonc`，不写数据库或原地图 JSON。
- 普通生成保持版本 1；事件分类/闪光/缩放使用兼容的版本 2，已有版本 1 文件不批量迁移。
- 自定义序号从 `100000` 开始，出生坐标、外观、朝向、移动范围与启用开关一并保存。
- 反馈给出 `地图名`、固定 `NPC序号`（`entityIdx`）、运行时 `NPC编号`（Object ID）和文件路径。
- 可在店铺 JSONC 的 `npcs` 中填写反馈里的地图名和 NPC 序号，再执行 `//reloadshops`
  把它作为店员；不能填写长数字 Object ID。未绑定店铺、未配置脚本的 NPC 不会自动开店。
- 启动先加载原地图，再由 `CustomNpcCatalog` 恢复独立 NPC，最后加载商店目录；
  固定序号跨重启保留，Object ID 按连接协议需要重新分配，不写入配置。
- 字段格式、路径示例和停用方式见 [独立 NPC 配置说明](../resource/npc/custom/README.md)。
- 手工编辑、停用和删除 NPC 文件需停服后操作并重启生效；在线删除可用 `//eventdeletenpc`。
  `//reloadshops` 不重载 NPC 本身。
- 以前临时生成且未保存的 NPC 不会自动转存，不能把原生实体误判成自定义实体；新版需重新创建。

## 3. 边界与同步

拒绝未就绪/失效连接、地图加载未完成或失败、NPC 加载开关关闭、战斗/交互/交易中、
非法朝向/高度、越界或不可站立的目标格，以及同层已有 NPC/玩家的位置。
不跨地图找空位，不把 NPC 叠在原店员或玩家身上。GBA 层比较沿用 `/3` 分组。
当前每张地图最多 1024 个 NPC，包含资源 NPC；普通移动范围不绕过生成格校验。

同地图的新增检查、序号选择与插入受地图锁保护，序号选择不覆盖已有 `npc_N`。
存储按固定高序号分配，包含停用文件的序号也会保留。保存先写同目录临时文件、刷盘，
再以原子移动发布；使用目录级保存文件锁，新增不覆盖已存在的目标文件。
删除命令仅在重新核对磁盘定义后原子替换为停用配置，外部修改会拒绝覆盖。
保存失败不加入在线地图，不向客户端发生成包；若原子移动完成后通知异常，文件仍可重启恢复。
不支持原子移动的文件系统明确拒绝，不降级为可能生成半个文件的写入方式。
目录只允许安全地图名和规范路径；符号链接、越界重定向、超过 16 KiB 的单文件、
超过 10000 个文件、未知/重复字段、非法数值与路径身份不一致都拒绝。
启动时先校验整批配置，再发布自定义实体；坏文件、同序号或同层占位冲突会禁用新增并保留原生 NPC。
`MapData.addEntity` 发布复制后的字典，避免动态插入破坏其他线程的遍历；
原字典 getter/setter 类型保持兼容。

GBA/NDS 地图实体初始化改为只给尚无 Object ID 的 NPC 分配编号，并使用统一雪花生成器。
已有 NPC 的 ID 不因另一个玩家重新加载地图而被改写；进程重启后的固定身份由配置序号维护。
该修正不改变地图实体的外观字段、剧情隐藏规则或实体包格式。

发送完成表示已执行服务端通知路径，不等于收到客户端渲染确认；断线/编码错误查看现有 Session 日志。
命令不会用固定延时补发、伪造数据库持久化成功或自动重复生成。

## 4. 协议证据

用户抓包：C2S `0x08`，共 48 字节；payload 为 `NORMAL=0` 字节、
UTF-16LE 文本 `//spawnnpc 0 0 0 0 0 0` 和两个零终止字节。
本次只新增命令注册，不新增 C2S opcode。

只读 Recaf JASM 证据（工作区 `28887-renamed.jar`）：

- `f.uk_0.vl0()` 无事件分支顺序：`o5.cx0`、`Oy0`、`rI[LK0]`、
  `Ne0[JJ0]`、`L0.cx0`、`COm9.cx0`。
- `f.uk_0.<init>()` 控件标签及上下限确认 Sprite ID、Script Offset、
  Sprite Region、Movement Type 和两个 Movement Leash；`XL0()` 确认两类地图的原始移动值。
- `f.N50.Aa(byte)` 为地区 `0/1` 时返回 true，即 GBA 分支。
- S2C `0x12` 已注册 `SendAddGameEntityPacket`；客户端 `f.ho_1` 的 `case 18`
  对应 `f.b0_0`。`b0_0.Oj0/os0` 确认以下普通 NPC 字段并创建/更新原版地图 NPC。

| 顺序 | 字段 | 类型 |
| --- | --- | --- |
| 1 | NPC Object ID | long LE |
| 2 | 外观地区、外观编号 | byte、short LE |
| 3 | 默认朝向、移动类型、横范围、纵范围 | 四个 byte |
| 4 | 所在地区、地图字节对 | 三个 byte；NDS 为 mapId/bank，GBA 为 bank/mapId |
| 5 | x、y、z、当前朝向 | short LE、short LE、byte、byte |
| 6 | flags | short LE；普通可交互 NPC 为 `8`，没有可选尾部 |

普通响应 payload 长度为 26，含 opcode 为 27，不包含传输帧/压缩标志。
本次没有新增二进制字段或修改已有 NPC 编码器。

## 5. 文件与验证

新增：

- `command.commands.SpawnNpcCommand`：六参数解析、中文反馈、默认 GM 权限。
- `game.entity.NpcSpawnRequest`：参数和按地图地区的移动值校验。
- `game.entity.NpcSpawnService`：前方格生成、唯一序号、在线地图插入与会话通知。
- `game.npc.CustomNpcDefinition` / `CustomNpcCodec`：不可变出生定义和严格 JSONC 编解码。
- `game.npc.CustomNpcStore` / `CustomNpcCatalog`：独立原子保存、启动恢复、固定序号与冲突保护。
- `SpawnNpcCommandTest`、`NpcSpawnServiceTest`：命令反馈、参数、占位、并发、
  快照、NDS 重新加载 ID 稳定性及 NPC 包字段顺序测试。
- `tools/check_spawnnpc_contract.ps1`：只读静态检查。
- `tools/check_custom_npc_contract.ps1` / `CustomNpcPersistenceTest`：持久化静态检查与保存/恢复回归用例。

修改 `GameCommandModule` 注册命令；修改 `MapData`、`KantoregionMapData`、
`NdsMapData` 支持动态新增及稳定编号。模块、包职责、源码索引和架构文档同步更新。
持久化阶段修改 `ScriptManager`，让独立自定义 NPC 先于商店恢复；`//spawnnpc` 参数及 S2C 包不变。
生产 Java package 均以 `org.pokemmo.gameserver` 为前缀。

2026-09-09 验证状态：

- 已对照客户端 JASM、检查注册/参数/调用方和协议字段顺序。
- 按项目要求未编译、未运行 JUnit、未启动客户端或服务器、未连接/修改数据库。
- 已运行三个只读检查脚本；目录目前没有实际生成的 NPC 文件，不把空目录检查当作重启联调通过。
- 运行回归还需覆盖：NPC Tool Spawn、两名玩家同时观察、换图返回、
  重连、各频道及相邻地图可见性、静止/普通移动表现、保存后重启恢复、店铺重启绑定、
  写入失败不生成、停用文件保留序号、原生 NPC 不变、错误参数及占位拒绝。

只读检查入口：

```powershell
./tools/check_spawnnpc_contract.ps1
./tools/check_custom_npc_contract.ps1
./tools/check_shop_contract.ps1
./tools/check_npc_delete_contract.ps1
./tools/check_eventspawnnpc_contract.ps1
```
