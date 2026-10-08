# OpenMMO 新剧情开发手册

本文档是 OpenMMO 添加新剧情的统一入口，供开发者和其他 AI agent 使用。
它同时记录当前已经存在的真新镇开场/大木包裹实现、必须遵守的代码边界，
以及后续将剧情做成数据驱动时的推荐流程。

## 0. 当前地区和章节状态

以下状态以 **2026-09-15** 工作区源码和 `resource/story` 实际文件为准。
“已实现”只表示服务端代码、配置和静态检查已经存在；如果没有明确写“实机已验证”，
不能把它理解为客户端流程已经完全通过。

### 0.1 总览

| 地区 | 章节 | 配置 | 服务端实现 | 持久化状态 | 当前完成状态 | 主要参考 |
| --- | --- | --- | --- | --- | --- | --- |
| 关都 Kanto | 真新镇开场 | `resource/story/kanto/pallet_town/opening.jsonc` | `StoryRuntime`、`StoryActionExecutor`、开场领域适配器 | `oak_lab_status=0..4`、`first_partner_status[1]` | 已实现/静态验证；编译/实机未执行 | `docs/PALLET_TOWN_STORY.md`、FireRed 地图/NPC、客户端原生对白 |
| 关都 Kanto | 大木包裹、图鉴和 5 个精灵球 | `resource/story/kanto/oak_parcel/chapter.jsonc` | `StoryRuntime`、`PARCEL_OPERATION`、`OakParcelStore` | `oak_lab_status=4..6`、`oak_parcel_status` | 已实现/静态验证；编译/实机未执行 | `docs/OAK_PARCEL_STORY.md`、客户端中文文本和背包/图鉴协议 |
| 关都 Kanto | 常磐市捕获教学 | `resource/story/kanto/viridian_city/catch_tutorial/chapter.jsonc` | `StoryRuntime`、`START_BATTLE`、`ViridianCatchStore` | `story_line_flag[1]` bit 4 | 已实现/静态验证；编译/实机未执行；老人自动捕获演出未验证 | `docs/VIRIDIAN_CATCH_TUTORIAL.md`、FireRed 地图/NPC、客户端原生文本和野生捕获协议 |
| 通用关都宝可梦中心 | 护士治疗 | `resource/story/kanto/pokemon_center_nurse/chapter.jsonc` | `StoryRuntime`、`PokemonHealingService`、通用 `HEAL_PARTY` 动作 | 无剧情存档字段 | 已实现/静态验证；编译/实机未执行 | `docs/POKEMON_CENTER_NURSE.md`、FireRed 宝可梦中心脚本、客户端原生文本和 PARTY 刷新协议 |
| 关都 Kanto | 尼比至枯叶早期主线 | `resource/story/kanto/early_route_to_vermilion/chapter.jsonc` | `StoryRuntime`、训练家战斗/徽章/故事位动作、`StoryProgressStore` | Kanto `story_line_flag` bit 5..14、徽章 bit 0..2 | 已实现/静态验证；编译/实机未执行 | 当前地图 NPC/坐标、FireRed 原版文本和训练家队伍参数 |
| 关都 Kanto | 枯叶市之后至冠军主线 | `resource/story/kanto/mainline_to_champion/chapter.jsonc` | `StoryRuntime`、`StoryActionTransaction`、原版训练家目录、道具拾取目录、徽章、冠军状态、扩展故事位、Safari 状态和地图触发器 | Kanto `story_line_flag` bit 0..4、bit 15、扩展槽 bit 0..1、`character_story_event_flag`、`character_story_action`、徽章 bit 3..7、champion_flag[0] | 已实现/静态验证；编译/实机未验证；地图机关演出仍按现有地图碰撞/事件能力运行 | FireRed 原版地图脚本、客户端原生文本、当前地图 NPC/坐标 |
| 丰缘 Hoenn | 全部章节 | 无 | 无 | 无 | 未开始；地图已加载，剧情未接入 | `resource/map/hoenn`、原版 Emerald 事件、客户端对应协议 |
| 神奥 Sinnoh | 全部章节 | 无 | 无 | 无 | 未开始；地图已加载，剧情未接入 | `resource/map/sinnoh`、Platinum 事件、NDS 地图/脚本协议 |
| 其他地区 | 全部章节 | 无 | 无 | 无 | 未开始；不要假设已有剧情运行时 | 先确认地区资源、客户端协议和 `ScriptManager` 加载范围 |

### 0.2 “完成”是什么意思

剧情状态必须分成四种，不能只写“完成”：

| 状态 | 含义 |
| --- | --- |
| `未开始` | 没有章节配置、运行时代码或持久化接入 |
| `设计中` | 已有流程设计或文本整理，但尚未接入可运行代码 |
| `已实现/静态验证` | 配置、源码、测试源码和静态检查存在；没有编译或实机证明 |
| `已实机验证` | 用户实际编译、启动并走完流程，日志、数据库和客户端显示均已核对 |

当前六个关都章节最高只能标记为**已实现/静态验证**。
已有的 `PalletStoryTest`、`OakParcelStoryTest`、`ViridianCatchStoryTest` 和 PowerShell 合同检查没有被执行时，
也不能把它们写成 JUnit 或并发行为“已通过”。

### 0.3 当前章节的完成节点

真新镇开场：

```text
0  尚未触发大木拦路
1  已遇见大木，等待护送/研究所
2  等待选择初始宝可梦
3  已领取初始宝可梦，等待劲敌战斗
4  首次劲敌战斗结束，开场完成
```

大木包裹：

```text
阶段4 + 包裹状态0/3  -> 常磐市商店领取包裹
阶段4 + 包裹状态1    -> 携带包裹，回研究所交付
阶段5 或包裹状态2    -> 领取图鉴和5个精灵球
阶段6..9              -> 该章节不再重复领奖
```

常磐市捕获教学：

```text
大木章节阶段<6       -> 不触发捕获教学
阶段>=6 + bit4为0    -> 与 ViridianCity/npc_3 交互，进入5级独角虫教学战斗
捕获成功             -> story_line_flag[1] bit4设为1，章节完成
逃跑/击败/失败       -> 不完成，下次可重试
```

当前 `resource/story` 目录有六个通用章节 JSONC。新增章节后必须在本节增加一行，
说明配置路径、运行时入口、存档字段、完成节点、验证状态和参考来源。

### 0.4 当前运行时限制

- 五个现有剧情章节已经由 `StoryRuntime` 统一解释通用 JSONC；
- `StoryService` 仍是统一路由入口，但生产剧情不再按章节直接分派流程；
- 数据库奖励、战斗和捕获仍通过有限动作适配器调用领域服务；
- ROM 脚本自动提取和转换器尚未接入，当前仍需手工提供通用 JSONC；
- 不要把丰缘、神奥或其他地区的地图加载误认为剧情已经完成；
- 不要把原版 ROM 中存在的剧情误认为服务端已经实现；
- 不要把客户端存在某个 UI 误认为服务端已经拥有对应的协议和持久化。

## 1. 先记住这件事

**不要为每一句对白、每一个触发格、每一个 NPC 事件创建一个 Java 类。**

剧情应按“章节”组织，而不是按“事件”组织：

```text
一个章节
  -> 一个配置目录和一个章节定义
  -> 通用触发器、条件、动作和节点执行器
  -> 少量领域服务处理真正的业务副作用
```

当前保留的 `PalletOpeningService`、`OakParcelStory` 和 `ViridianCatchStory`
只提供数据库、战斗和资源兼容适配；生产入口由 `StoryRuntime` 统一编排。

后续新增剧情时，优先复用已有的原生场景执行器和领域服务。
如果现有执行器缺少某种动作，应新增一个可复用动作类型或策略；
不要把新动作直接硬编码成某个 NPC 的专用分支。

> 当前仓库已经实现通用加载器和节点执行器，六个现有章节已经迁移到该格式。
> ROM 指令到通用 JSONC 的自动转换仍属于后续工具工作，不能假设所有 ROM 指令
> 已经有动作适配器。

## 2. 开发前必须做的事

### 2.1 阅读规范和项目文档

开始任何修改前，必须读取：

1. 根目录 `AGENTS.md`；
2. `docs/README.md`；
3. `docs/PROJECT_ARCHITECTURE.md`；
4. `docs/PACKAGE_INDEX.md`；
5. `docs/PACKAGE_PURPOSES.md`；
6. 本文档；
7. 与目标地图、协议、战斗、道具、命令或数据库相关的专用文档；
8. 现有剧情源码和对应测试。

如果任务涉及客户端协议，还要读取：

```text
C:\Users\z3407\Desktop\28887-obf-project\AGENTS.md
```

客户端项目只能按它自己的规则读取和修改。服务端 agent 不应为了验证而擅自修改客户端。

### 2.2.1 剧情参考来源顺序

新增剧情时按以下优先级确认事实：

1. 当前服务端源码、配置、测试和本手册；
2. 当前 `resource/map` 的实际地图 JSON、NPC、传送、碰撞和地图 key；
3. 当前 `resource/item`、`resource/pokemon`、`resource/move`、`resource/trainer` 的资源；
4. 客户端 `data/strings` 中的实际本地化文本；
5. 客户端源码或 JASM 中已确认的封包、UI 回调和字段顺序；
6. 原版 FireRed/Emerald/Platinum 事件资料或地图生成源，用于确认叙事、坐标和脚本意图；
7. 抓包日志，用于补充尚未确认的网络字段。

参考来源的用途要分开：

| 来源 | 可以确认 | 不能直接推断 |
| --- | --- | --- |
| 服务端源码 | 当前实际调用链、存档和刷新行为 | 原版未实现的全部剧情 |
| 地图 JSON | 地图标识、NPC 序号、坐标、碰撞、传送 | 客户端对话文本和隐藏脚本语义 |
| 客户端字符串 | 文本内容和文本 ID | 文本对应的完整奖励逻辑 |
| 客户端 JASM/源码 | 客户端字段消费、UI 入口、回执行为 | 服务端数据库业务 |
| 原版 ROM/事件资料 | 原剧情意图、事件顺序、角色和奖励 | OpenMMO 当前客户端是否支持该表现 |
| 抓包 | 方向、长度、字节顺序和实际交互 | 未抓到字段的业务含义 |

地图生成文档中提到的 `pret/pokefirered`、`pret/pokeemerald`、
`pret/pokeplatinum` 和 `AKCore/OpenMMO-DS` 是资源来源或参考解析来源；
它们不一定存在于当前工作区。缺少原始资料时，必须在文档中标注“未验证”，
不能用猜测代替 JASM、抓包或源码证据。

### 2.2 检查工作区

先执行：

```powershell
git status --short --branch
```

必须保留用户已有改动。不要使用以下命令清理或覆盖工作区：

```text
git reset --hard
git checkout --
```

默认按照仓库规范：

- 不编译；
- 不启动游戏服或客户端；
- 不连接或修改真实数据库；
- 不执行会改变运行环境的迁移；
- 只做源码、配置、文档和静态检查。

如果用户明确要求编译、启动或数据库操作，必须把这些操作单独列出，并记录实际执行结果。

## 3. 先定义剧情边界

写代码前先把需求写成一页设计，至少回答以下问题：

| 问题 | 必须明确的内容 |
| --- | --- |
| 章节 ID | 例如 `kanto/oak_parcel`，全局唯一、稳定、不随文件名随意变化 |
| 适用地区 | Kanto、Hoenn、Sinnoh 或其他地区 |
| 开始条件 | 地图、坐标、NPC、道具、任务阶段、角色属性或战斗结果 |
| 结束条件 | 哪个节点表示完成，完成后是否还能重复交互 |
| 中断点 | 每段对白、地图切换、战斗、奖励之前是否可以断线恢复 |
| 持久化数据 | 节点 ID、布尔旗标、计数、选择值、奖励领取状态 |
| 副作用 | 金钱、道具、宝可梦、图鉴、地图 NPC、传送、战斗或外观 |
| 重复行为 | 重复交互、重复回执、重复奖励、重复登录分别如何处理 |
| 多玩家隔离 | NPC 位置、隐藏状态和剧情表现是否必须按玩家独立 |
| 客户端能力 | 是否能使用现有原生对白、菜单、战斗和容器刷新 |

如果无法回答结束条件和重复行为，剧情设计还没有完成，不能直接开始写 handler。

## 4. 现有剧情架构

### 4.1 运行时总链路

当前服务端的剧情入口是：

```text
客户端移动/交互/地图确认/对白回执
  -> c2s Packet
  -> CharacterManager 或 SceneInteractionService
  -> StoryService
  -> 章节编排
  -> PalletStoryScene 原生场景执行器
  -> 领域服务/数据库事务
  -> 在线内存更新
  -> Send*Packet 客户端刷新
```

当前具体入口：

| 事件 | 当前入口 | 剧情用途 |
| --- | --- | --- |
| 角色进入游戏世界 | `CharacterWorldLoader` -> `StoryService.onLogin` | 读取章节进度、清理旧的临时等待 |
| 客户端确认地图加载 | `RequestPlayerPacket` -> `StoryService.onMapReady` | 在真实地图就绪后自动启动或续播 |
| 玩家成功移动 | `CharacterEventService` -> `StoryService.onStep` | 坐标触发、出口触发、战斗触发 |
| A 键识别 NPC | `SceneInteractionService` -> `StoryService.beforeShop/onNpc` | 任务 NPC、商店和普通 NPC 分流 |
| 明确 NPC 对话 | `StartTalkPacket` -> `SceneInteractionService` | 仍然经过统一 NPC 检查 |
| 原生对白回执 | `InteractPacket` -> `StoryService.onReply` | 校验序号并继续节点 |
| 连接断开 | `GameProtocol` -> `StoryService.onDisconnect` | 清理临时场景，不删除数据库进度 |

重要事实：

- `0x27` 无 payload 只表示场景 A 键请求，不表示目标一定是电脑；
- `SceneInteractionService` 必须先识别 NPC，再识别电脑，再处理未知背景目标；
- 商店任务需要在普通商店打开之前拦截，但没有待办任务时必须让玩家正常购物；
- 新章节需要把入口加到 `StoryService`，不能从 packet 直接调用章节类；
- 当前 `StoryService.onStep` 主要转交开场。新章节若需要坐标触发，必须扩展路由，而不是绕过路由；
- 当前 `StoryService.onReply` 通过共享的 `PalletStoryScene` 处理回执，新增章节不能另造一套序号状态。

### 4.2 Package 放置

| Package | 应承载的内容 | 不应承载的内容 |
| --- | --- | --- |
| `org.pokemmo.gameserver.game.story` | 章节配置、触发判定、节点流程、NPC 表现、原生场景编排 | SQL、直接写数据库、通用 Netty 逻辑 |
| `org.pokemmo.gameserver.services.story` | 角色归属条件、行锁、检查点、奖励和资产原子事务 | 对话 UI、定时器、Session 发包 |
| `org.pokemmo.gameserver.game.interact` | A 键目标识别、NPC/电脑/商店分流和交互类型 | 剧情奖励、章节 SQL |
| `org.pokemmo.gameserver.protocol.packets.c2s` | 客户端请求 decode 和转交 | 章节业务流程 |
| `org.pokemmo.gameserver.protocol.packets.s2c` | 服务端响应 encode | 数据库写入和剧情判断 |
| `org.pokemmo.gameserver.game.npc` | 独立自定义 NPC 文件、固定序号、恢复和停用 | 每玩家剧情状态、奖励 |
| `org.pokemmo.gameserver.game.map` | 地图、坐标、碰撞、传送和地图资源 | 角色专属剧情阶段 |

新增 package 时必须同步：

```text
docs/SOURCE_FILE_INDEX.md
docs/PACKAGE_INDEX.md
docs/PACKAGE_PURPOSES.md
docs/MODULE_SERVER_GAME.md
```

不要把新剧情放入 `org.server`、`mmo` 或含义不明确的 `common/misc/manager2`。

## 5. 当前可复用的运行时组件

### 5.1 `PalletStoryScene`

这是当前原生剧情表现的共享执行器，负责：

- 设置 `InteractType.STORY`；
- 发送 `SendHasEventPacket(true/false)`；
- 发送原生 `SendInteractPacket`；
- 保存期望的交互序号；
- 校验重复或过期回执；
- 保存一次性 continuation；
- 120 秒超时清理；
- 延迟动作和行走动画；
- 结束时关闭对白、停止音乐、清理实体和恢复 `InteractType.NONE`。

新增剧情必须复用这些能力，不能使用：

```java
Thread.sleep(...);
```

也不能把一个 `CompletableFuture` 或静态变量当成所有玩家共用的对白状态。

### 5.2 `InteractScript`

原生对白使用：

```java
new InteractScript(
    "Story",
    GameInteractionType.MSG_NOCLOSE,
    textOffset,
    0,
    0
)
```

是/否框使用 `GameInteractionType.MSG_YESNO`。
`GameInteractionType.getProtocolType()` 负责把资源类型映射成客户端 wire type，
不要在新代码中手写一套数字映射。

普通对白的客户端回执通常是：

```text
sequence + choice
```

服务端必须：

1. 保存发送给客户端的 sequence；
2. 只接受当前等待的 sequence；
3. 普通继续只接受合法的继续值；
4. 是/否只接受 `0` 或 `1`；
5. 先清除 continuation，再调用下一步，防止重复回执二次执行。

### 5.3 地图移动和 NPC 表现

玩家或剧情 NPC 移动必须：

- 使用现有地图碰撞检查；
- 检查起点和终点可通行；
- 限制路径长度，避免超过协议字段范围；
- 先发送行走动画；
- 根据 `SportType.getActionTimeConsuming(...)` 异步推进；
- 动画完成后更新服务端位置；
- 必要时发送位置纠正或广播；
- 地图切换使用 `handleReLoadMap`；
- 等待客户端实际 C2S `0x05` 地图确认，不使用固定延时假设地图已经完成。

剧情 NPC 不应直接修改共享 `MapData` 中原 NPC 的位置。当前开场使用
`PalletStoryNpcs.project` 克隆实体，按角色维护独立的坐标、隐藏和显示结果。

## 6. 新章节的推荐数据模型

### 6.1 章节目录

推荐目录：

```text
resource/story/
  kanto/
    pallet_town/
      opening.jsonc
    oak_parcel/
      chapter.jsonc
    viridian_city/
      catch_tutorial/
        chapter.jsonc
    viridian_city/
      chapter.jsonc
  hoenn/
    littleroot_town/
      chapter.jsonc
```

一个章节一个目录，一个章节配置文件，不要把全部地区和所有剧情塞进一个超大的 JSONC。

### 6.2 通用 JSONC 结构

下面是当前通用剧情执行器支持的结构示例：

```jsonc
{
  "id": "kanto.viridian_city.rival_intro",
  "version": 1,
  "enabled": true,
  "triggers": [
    {
      "type": "NPC",
      "map": "ViridianCity",
      "entityIdx": 4,
      "startNode": "greeting"
    }
  ],
  "actors": {
    "rival": {
      "map": "ViridianCity",
      "entityIdx": 4
    }
  },
  "startNode": "greeting",
  "nodes": {
    "greeting": {
      "actions": [
        {
          "type": "DIALOGUE",
          "actor": "rival",
          "text": "rivalGreeting"
        }
      ],
      "next": "question"
    },
    "question": {
      "actions": [
        {
          "type": "YES_NO",
          "actor": "rival",
          "text": "acceptQuestion",
          "yes": "accept",
          "no": "decline"
        }
      ]
    },
    "accept": {
      "actions": [
        {
          "type": "NOTIFY",
          "message": "已接受剧情选择。"
        }
      ],
      "next": "finish"
    },
    "decline": {
      "next": "finish"
    },
    "finish": {
      "actions": [
        {
          "type": "CLOSE_SCENE"
        }
      ]
    }
  },
  "text": {
    "rivalGreeting": 1234567,
    "acceptQuestion": 1234568
  }
}
```

这是当前 `StoryCatalog` 和 `StoryRuntime` 使用的契约。
新增或扩展字段必须先实现并验证：

1. JSONC DTO；
2. 字段白名单；
3. 数量、ID、长度和枚举校验；
4. 节点引用存在性校验；
5. 文本引用存在性校验；
6. 触发器地图和 NPC 存在性校验；
7. 动作参数校验；
8. 章节 ID 和版本校验；
9. 配置不可变快照；
10. 启动失败时不发布半加载章节。

### 6.3 条件和动作不要混成字符串

推荐使用有限枚举：

**条件**

```text
STORY_STAGE_AT_LEAST
STORY_STAGE_EQUALS
FLAG_EQUALS
HAS_ITEM
HAS_POKEMON
HAS_BADGE
MAP_EQUALS
COORDINATE_EQUALS
PARTY_HAS_ALIVE_POKEMON
NOT_IN_BATTLE
NOT_IN_TRADE
```

**动作**

```text
DIALOGUE
YES_NO
BRANCH
GOTO
MOVE_PLAYER
MOVE_NPC
GUIDE
PLAY_MUSIC
CHANGE_MAP
START_BATTLE
SET_OPENING_STAGE
SELECT_STARTER
PARCEL_OPERATION
HEAL_PARTY
REMOVE_ITEM
SET_CHAMPION
SET_ELITE_STAGE
CLOSE_DIALOG
NOTIFY
CLOSE_SCENE
```

不要允许 JSONC 直接执行任意 Java 方法名、反射方法或 SQL。
当前动作由 `StoryActionExecutor` 固定分派；需要新业务时增加一个有边界的动作
处理器，并在代码、配置校验和文档中同时登记。

## 7. 持久化设计

### 7.1 三层状态必须分开

剧情同时涉及三种状态：

```text
数据库状态
  -> 在线内存状态
  -> 客户端显示状态
```

只改数据库，在线玩家不会立即看到正确状态；
只改在线内存，重登或重启会丢失；
只发客户端包，重登后会被数据库覆盖。

### 7.2 当前字段

当前真新镇章节复用了已有字段：

| 字段 | 当前含义 |
| --- | --- |
| `character.oak_lab_status` | 真新镇开场阶段；阶段 4 表示开场完成，阶段 5/6 被包裹章继续使用 |
| `character.first_partner_status[1]` | 关都初始选择；数据库下标 1 对应 Java 数组下标 0；`0/1/2` 为已选择，`3` 为未选择 |
| `character.oak_parcel_status` | 包裹状态；`0/3` 待领取，`1` 携带中，`2` 已交付 |
| `character_story_event_flag` | 地图道具/普通训练家个人事件标志，以及四天王阶段 `kanto/elite_four/stage_0..4` |
| `character_story_action` | 通用剧情节点幂等键；`character_id + action_key` 防止断线重放重复领取奖励 |

只有字段语义完全一致时才能复用旧字段。
不能为了省一个字段，把道馆进度、火箭队进度或另一地区剧情塞入 `oak_lab_status`。

### 7.3 后续通用剧情推荐存档

当剧情数量增加时，推荐增加通用数据库表，而不是继续在 `character` 表添加大量专用列：

```text
story_progress
  character_id       BIGINT  NOT NULL
  story_id           VARCHAR NOT NULL
  story_version      INTEGER NOT NULL
  node_id            VARCHAR NOT NULL
  flags              JSONB   NOT NULL
  counters            JSONB   NOT NULL
  choices             JSONB   NOT NULL
  completed          BOOLEAN NOT NULL
  updated_at         TIMESTAMP NOT NULL
  PRIMARY KEY (character_id, story_id)
```

实际变更必须：

1. 修改 `db/schemas`；
2. 设计索引和唯一约束；
3. 重新生成 jOOQ；
4. 不直接编辑 `db/src/.../jooq` 生成文件；
5. 将数据库读写集中到 `org.pokemmo.gameserver.services.story`；
6. 在文档中记录迁移、默认值和旧版本兼容；
7. 明确版本升级和回滚策略。

### 7.4 检查点提交规则

凡是不可逆副作用，都必须和检查点在同一事务中提交：

```text
校验当前节点和角色归属
  -> 锁定角色
  -> 锁定相关资产
  -> 执行奖励/扣除/战斗结算
  -> 写入下一节点或完成标记
  -> 提交事务
  -> 更新在线对象
  -> 发送客户端刷新包
```

事务层必须：

- 使用 `character_id + account_id` 限制角色；
- 使用 `trainer_id` 限制宝可梦；
- 使用 `owner_id + inventory_id` 限制道具；
- 使用 `character_id + action_key` 防止通用剧情节点重复执行；
- 对当前节点使用乐观条件，例如 `WHERE node_id = oldNode`；
- 重复请求返回幂等结果，不能重复发奖；
- 失败时回滚资产和检查点；
- 不持有 Session，不等待玩家对白，不发送网络包。

不要出现这种顺序：

```text
先发奖励包 -> 再写数据库
```

也不要在两个独立事务中分别保存奖励和剧情阶段。

### 7.5 在线同步顺序

事务成功后再执行：

```text
数据库成功
  -> 更新 CharacterData / PokemonData / 在线剧情状态
  -> 更新 PARTY、PC、背包或图鉴容器
  -> 发送 Send*Packet
  -> 刷新个人 NPC 投影
  -> 返回中文日志或系统反馈
```

涉及背包时通常需要 `SendInventoryPacket`；
涉及图鉴时使用现有 `SendLoadDexPacket`；
涉及新宝可梦时使用现有完整宝可梦或容器封包；
涉及金钱时使用现有角色刷新封包。

## 8. 章节执行器的推荐设计

### 8.1 一次运行一个玩家场景

每个玩家应该拥有独立的短生命周期场景：

```text
StorySession
  storyId
  nodeId
  expectedReply
  continuation
  timer
  actorOverrides
  generation
```

它只能存在于内存中；数据库保存的是可恢复的检查点，不是 Java continuation。

不要把以下内容放入静态全局变量：

- 当前玩家；
- 当前等待的 sequence；
- 当前 NPC 坐标；
- 当前奖励是否领取；
- 当前对白 continuation。

### 8.2 节点推进

推荐执行逻辑：

```text
触发器命中
  -> 读取玩家当前进度
  -> 检查章节 enabled
  -> 检查地图、坐标、战斗、交易和交互状态
  -> 读取节点
  -> 执行无副作用表现动作
  -> 等待客户端回执
  -> 重新验证玩家 Session 和场景 generation
  -> 执行下一个节点
  -> 遇到副作用动作时进入事务
  -> 提交后推进检查点
```

所有异步回调必须检查：

```text
Session 仍然 active
CharacterManager 仍然绑定该 Session
scene generation 没有变化
InteractType 仍然是 STORY
角色仍在正确地图
```

断线、换图、重新登录和超时都必须使旧 continuation 失效。

### 8.3 何时需要 Java 领域服务

以下情况可以增加一个领域服务，但应按领域复用，不按事件复制：

| 需求 | 推荐服务 |
| --- | --- |
| 发放道具、扣道具、检查背包容量 | 复用 `services.inventory` 或新增共享剧情资产服务 |
| 发放宝可梦、写队伍和 PC | 复用 `services.pokemon` |
| 战斗启动和结束 | 复用 `game.battle`，剧情只编排 |
| 传送和地图加载 | 复用角色地图服务 |
| 图鉴更新 | 复用 `services.world` 的图鉴访问 |
| 特殊 NPC 个人表现 | 复用 `game.story` 的投影机制 |
| 新的领域资产 | 在 `services.story` 下增加可复用事务组件 |

只有无法由通用动作表达、且包含真实领域规则时，才增加新的 Java 类型。

## 9. 现有章节作为参考

### 9.1 真新镇开场

配置：

```text
resource/story/kanto/pallet_town/opening.jsonc
```

主要源码和通用适配器：

```text
game/story/StoryCatalog.java
game/story/StoryProgram.java
game/story/StoryRuntime.java
game/story/StoryActionExecutor.java
game/story/PalletStoryCatalog.java
game/story/PalletStoryProgress.java
game/story/PalletStoryState.java
game/story/PalletStoryLifecycle.java
game/story/PalletStoryScene.java
game/story/PalletStoryNpcs.java
game/story/PalletOpeningService.java
game/story/PalletStoryBattle.java
services/story/PalletStoryStore.java
```

阶段：

```text
0  真新镇北出口尚未触发
1  已遇见大木，等待护送/研究所
2  等待选择初始宝可梦
3  已领取初始宝可梦，等待劲敌战斗
4  开场完成
```

关键规则：

- 初始宝可梦插入、初始选择、图鉴和阶段变更必须带角色归属；
- 首次战斗的胜负由服务端战斗状态决定；
- 客户端不能用结束回执伪造战斗胜利；
- 战斗初始化封包必须先编码检查，再发布 BattleManager；
- 进入研究所要等真实地图确认；
- 角色独立 NPC 不能修改共享地图。

### 9.2 大木包裹章节

配置：

```text
resource/story/kanto/oak_parcel/chapter.jsonc
```

主要源码和通用适配器：

```text
game/story/StoryCatalog.java
game/story/StoryProgram.java
game/story/StoryRuntime.java
game/story/StoryActionExecutor.java
game/story/OakParcelCatalog.java
game/story/OakParcelProgress.java
game/story/OakParcelStory.java
services/story/OakParcelStore.java
services/story/StoryInventory.java
```

流程：

```text
开场阶段4
  -> 常磐市商店领取物品349
  -> 回研究所交付包裹
  -> 领取图鉴叙事和5004号精灵球 x5
  -> 保存阶段6并结束
```

关键规则：

- 待办剧情在商店之前拦截；
- 已经携带包裹或已经完成章节的玩家仍可以正常购物；
- 包裹 `349` 不能交易、邮件、GTL、丢弃、装备或普通使用；
- 包裹、精灵球和检查点在同一事务中处理；
- 重复回执不能重复领奖；
- 原生客户端已有图鉴界面，不擅自伪造“图鉴解锁位”。

### 9.3 常磐市捕获教学

配置：

```text
resource/story/kanto/viridian_city/catch_tutorial/chapter.jsonc
```

主要源码和通用适配器：

```text
game/story/StoryCatalog.java
game/story/StoryProgram.java
game/story/StoryRuntime.java
game/story/StoryActionExecutor.java
game/story/ViridianCatchCatalog.java
game/story/ViridianCatchStory.java
services/story/ViridianCatchStore.java
```

流程：

```text
大木章节阶段6以上
  -> ViridianCity 的 npc_3 教学老人
  -> 原生捕获说明
  -> 固定5级独角虫 WildBattle
  -> 玩家使用真实精灵球捕获
  -> 既有捕获事务更新 PARTY/PC 和图鉴
  -> 设置 story_line_flag[1] bit 4
  -> 结束战斗回执后播放完成对白
```

关键规则：

- 只允许在 `ViridianCity/npc_3` 触发，其他 NPC 不会启动本章；
- 捕获球不足时只提示，不启动教学战斗；
- 只有战斗结果为 `CATCH_POKEMON` 才保存完成 bit；
- 逃跑、击败或失败不会完成，下次可以重试；
- 完成 bit 使用 `story_line_flag` 的独立第 5 位，保留其他地区和其他 bit；
- 复用既有捕获协议，不新增 opcode；
- 老人自动投球的原版逐帧演出尚未确认，当前实现是玩家操作固定独角虫战斗。

### 9.4 宝可梦中心护士

配置：

```text
resource/story/kanto/pokemon_center_nurse/chapter.jsonc
```

流程由通用 NPC 触发器、`YES_NO`、`DIALOGUE` 和 `HEAL_PARTY` 动作组成，
`PokemonHealingService` 仍负责带角色归属的事务和 PARTY 刷新。

## 10. 添加新剧情的实际步骤

以下步骤是其他 AI agent 应执行的顺序。

### 第一步：调查现有能力

使用 `rg` 搜索：

```powershell
rg -n "StoryService|PalletStoryScene|InteractScript|SendInteractPacket" server.game/src/main
rg -n "get.*Service|Send.*Packet|transactionResult|forUpdate" server.game/src/main/java/org/pokemmo/gameserver
rg -n "mapKey|entityIdx|npc_" resource/map server.game/src/main/java/org/pokemmo/gameserver/game
```

先确认已有动作、封包和数据库服务，避免重复造轮子。

### 第二步：确定触发方式

选择一种或多种：

```text
坐标触发：成功移动后触发
地图进入：C2S 0x05 确认地图后触发
NPC 交互：A 键或明确 NPC Object ID
战斗结束：服务端判定战斗结果后触发
道具/菜单：由已有客户端请求触发
```

触发判定必须放在章节路由或通用触发器中。
不要把剧情条件塞进 `MovePacket`、`InteractPacket` 或 `StartTalkPacket`。

### 第三步：确认地图和 NPC

先从地图 JSON 和运行时 `MapData` 确认：

- 地区号；
- 地图组/地图号；
- 地图资源 key；
- NPC 的固定 `entityIdx` 或稳定自定义 NPC 序号；
- NPC 的实际坐标和 Z 层；
- NPC 是否可交互；
- 路径起点和终点是否可走。

固有 NPC 使用固定 `npc_0`、`npc_3` 等资源序号时，必须确认它来自目标地图。
运行时 Object ID 可能在启动或地图加载时变化，不能持久化 Object ID 作为剧情绑定。

### 第四步：创建章节配置

当前章节配置至少要包含：

- `enabled`；
- 所有文本引用；
- 章节需要的角色、道具、宝可梦、训练家和招式 ID；
- 章节版本；
- 默认阶段和旧存档兼容规则。

加载器必须在启动时拒绝：

- 缺字段；
- 未知字段；
- 文本 ID 小于等于 0；
- 道具或宝可梦不存在；
- 数量超出堆叠或协议范围；
- 节点引用不存在；
- 地图/NPC 不存在；
- 重复章节 ID；
- 重复触发器；
- 非法阶段转换。

### 第五步：选择“配置驱动”还是“领域扩展”

**优先配置驱动**：

- 只是对白顺序变化；
- 只是 NPC、地图、坐标变化；
- 只是条件分支；
- 只是播放音乐、移动、传送；
- 只是发放普通道具；
- 只是设置一个通用剧情旗标。

**需要领域扩展**：

- 需要交易、战斗、捕获、队伍或 PC 的复杂事务；
- 需要特殊资源归属；
- 需要跨角色或跨服务状态；
- 需要新的客户端协议字段；
- 需要不同于普通道具的原子奖励；
- 需要服务器确认的不可伪造结果。

领域扩展应增加一个可复用服务或动作处理器，不创建事件专用 handler。

### 第六步：实现加载和路由

如果是当前架构下的章节：

1. 在 `game.story` 添加章节 catalog；
2. 在 `ScriptManager` 资源就绪后加载 catalog；
3. 在 `StoryService` 统一注册登录、地图、移动、NPC、回执和断线路由；
4. 在 `GameServerService` 组装需要的持久化服务；
5. 不从 packet 直接 new 章节服务；
6. 不让章节服务持有静态 Session。

如果通用执行器已经存在：

1. 只添加 JSONC；
2. 注册章节 ID 和触发器；
3. 为特殊副作用注册已有动作处理器；
4. 只有遇到真正的新领域动作时才改 Java。

### 第七步：实现持久化

先画状态转换图：

```text
节点A --条件满足--> 节点B
节点B --玩家确认--> 节点C
节点C --奖励事务成功--> 节点D
```

每一条不可逆边都要定义：

- 旧节点；
- 新节点；
- 条件；
- 锁定对象；
- 资产变更；
- 重复请求结果；
- 失败反馈；
- 重登后的恢复节点。

数据库操作集中到 `services.story`，并带角色/账号/训练家条件。

### 第八步：同步在线状态和客户端

提交成功后按领域刷新：

- 角色阶段 -> `CharacterData` / 章节状态；
- 队伍宝可梦 -> `partyPokemons` 和对应容器封包；
- 背包 -> `SendInventoryPacket`；
- 图鉴 -> `SendLoadDexPacket`；
- 金钱 -> 角色信息刷新；
- NPC -> `PalletStoryNpcs` 或通用个人投影刷新；
- 战斗 -> 现有 BattleManager 和结束回执链。

不要把数据库更新成功和网络发送成功混为一谈。
如果数据库已经提交但客户端刷新失败，应记录“数据已保存、显示刷新失败”，
不能回滚已提交的假象，也不能再次发放奖励。

### 第九步：处理断线和重登

至少测试这些节点：

```text
第一句对白前
对白等待中
是/否选择框打开时
移动动画播放中
地图切换前
地图已切换但未收到0x05
奖励事务前
奖励事务提交后、最后一句对白前
战斗结束后、0x33回执前
```

重登只能从数据库检查点恢复，不能恢复 Java continuation。
过期 Session 和旧 generation 的异步回调必须无效。

### 第十步：补测试和文档

至少增加：

- 配置字段和资源引用测试；
- 阶段转换测试；
- 条件分支测试；
- 重复回执测试；
- 重复奖励测试；
- 角色归属测试；
- 断线/重登恢复测试；
- 多玩家 NPC 隔离测试；
- 背包容量和非法 ID 测试；
- 战斗结果不能由客户端伪造测试；
- 失败事务不会更新在线状态测试。

文档必须包含：

- 章节目的；
- 配置文件；
- 地图和 NPC；
- 阶段表；
- 触发入口；
- 事务边界；
- 客户端封包；
- 断线恢复；
- 重置方式；
- 已验证和未验证项目。

## 11. 常见错误排查

### 11.1 “剧情存档与当前步骤不一致”

这通常不是对白本身的问题，而是数据库当前节点和代码期望节点不同。
先记录：

```text
章节 ID
角色 ID
账号 ID
期望节点
实际节点
当前地图
当前交互类型
当前是否战斗/交易
```

当前开场最典型的冲突是：

```text
oak_lab_status=0
first_partner_status[1]=0/1/2
```

阶段 0..2 要求初始选择为 `3`。这表示测试重置只改了阶段，没有同步初始选择。
不能自动清空宝可梦或猜测玩家历史。

### 11.2 “角色正忙”

先查看：

```text
InteractType
BattleManager
TradeManager
当前 Session
剧情 continuation
```

剧情结束、断线、换图和超时都应清理：

- `InteractType.STORY`；
- 当前脚本；
- 最后交互实体；
- continuation；
- timer；
- 个人剧情 actor 覆盖。

不要直接把 `InteractType` 强行改成 `NONE` 来掩盖仍然存在的战斗或交易。

### 11.3 NPC 对话不触发

按顺序检查：

1. 章节是否启用；
2. `StoryService` 是否收到 NPC 入口；
3. 当前地图 key 是否一致；
4. NPC 固定序号是否正确；
5. 玩家看到的投影实体是否可见；
6. NPC 是否可交互；
7. 玩家是否在同一 Z 层；
8. 是否在战斗、交易、商店或其他交互中；
9. 当前阶段是否允许该 NPC；
10. 是否误把运行时 Object ID 当成固定序号。

### 11.4 地图切换后剧情卡住

不要用固定 `Thread.sleep`。
确认：

```text
角色坐标已更新
目标 MapData 存在
handleReLoadMap 已调用
awaitingMap 已设置
客户端是否发送 C2S 0x05
RequestPlayerPacket 是否转交 StoryService.onMapReady
```

### 11.5 奖励重复发放

检查奖励动作和检查点是否在同一个事务。
重复请求必须在锁内重新读取数据库，而不是相信在线内存阶段。
数据库更新条件必须包含旧节点或旧版本：

```text
WHERE character_id = ?
  AND story_id = ?
  AND node_id = old_node
```

### 11.6 玩家之间互相看到剧情 NPC

不要修改共享 `MapData` 或原始 NPC 对象。
必须复制实体，按每个 `CharacterManager` 维护 actor 覆盖和可见性。
地图快照发送也要经过剧情投影，否则首次进图会重新显示被隐藏的实体。

## 12. 验证清单

### 12.1 静态检查

当前仓库可以使用：

```powershell
./tools/check_story_contract.ps1
./tools/check_oak_parcel_contract.ps1
python tools/story/validate_story.py
git diff --check
```

同时根据改动范围运行商店、NPC、战斗、隐身、治疗等相关静态合同脚本。

静态检查至少确认：

- package 和 import 正确；
- 新 packet 方向正确并已注册；
- JSONC 字段与加载器一致；
- 文本、地图、NPC、道具和宝可梦资源存在；
- 所有数据库更新带归属条件；
- 奖励和检查点在同一事务；
- 重复回执和重复奖励有保护；
- Java 文件不超过 500 行；
- 没有 wildcard import；
- 文档链接有效；
- 源码索引和 package 索引已同步。
- 通用剧情 JSONC 的节点、动作、地图和 NPC 引用通过离线校验；
- 当前章节不能退回 `legacy` 配置格式。

### 12.2 编译和实机

仓库默认不由 AI 编译或启动。
如果用户自行验证，建议按以下顺序：

1. 编译服务端；
2. 启动数据库、登录服、聊天服和游戏服；
3. 查看中文章节加载日志；
4. 使用测试角色从每个阶段开始；
5. 在每个不可逆操作前后断线；
6. 核对数据库、在线日志和客户端显示；
7. 测试第二个玩家，确认个人 NPC 和进度隔离；
8. 测试重复回执和重复登录；
9. 测试失败事务和容量边界。

未运行的内容必须写成“未执行”，不能写成“已通过”。

## 13. 重置和迁移

### 13.1 测试角色重置原则

重置前：

1. 让角色完整退出；
2. 结束战斗、交易和剧情交互；
3. 备份目标角色；
4. 确认角色 ID、账号 ID和数据库环境；
5. 只对测试角色执行。

当前开场全部重玩需要同时处理：

```text
oak_lab_status = 0
first_partner_status[1] = 3
oak_parcel_status = 3
```

只把 `oak_lab_status` 改为 `0` 而保留第一项 `0/1/2`，会触发存档冲突保护。

重置剧情不会自动回收已经发放的宝可梦、道具、图鉴或金钱。
如果要“资产也重置”，必须另外设计明确的、带角色归属的资产清理事务，
不能在剧情加载时偷偷删除。

### 13.2 版本迁移

章节配置修改时要区分：

- 只改对白引用；
- 改节点顺序；
- 改奖励；
- 改阶段语义；
- 改数据库结构。

改节点顺序或奖励时必须提高 `story_version` 或提供迁移策略。
不能让旧玩家拿着旧节点 ID 直接执行新节点的奖励。

## 14. 给其他 AI 的实施回报模板

完成新剧情后，必须用中文说明：

```text
### 剧情名称

### 触发方式
- 地图/坐标/NPC/战斗/道具：

### 配置文件
- 路径：
- enabled：
- 版本：

### 代码位置
- game.story：
- services.story：
- 其他领域服务：

### 持久化
- 表和字段：
- 旧节点：
- 新节点：
- 事务内资产：
- 重复请求行为：

### 客户端
- 是否新增 packet：
- opcode：
- 是否复用原生 UI：
- 未验证字段：

### 测试
- 静态检查：
- JUnit 源码：
- 编译：未执行/已执行
- 运行：未执行/已执行
- 数据库：未连接/已连接

### 文档
- 本文档是否更新：
- 专用章节文档：
- SOURCE_FILE_INDEX：
- PACKAGE_INDEX/PACKAGE_PURPOSES：
- MODULE_SERVER_GAME：
```

## 15. 完成标准

只有同时满足以下条件，才能说“新剧情已完成”：

1. 配置可以被严格加载；
2. 触发入口经过统一 `StoryService`；
3. 原生对白回执有序号和超时保护；
4. 异步回调有 Session、generation 和地图保护；
5. 阶段转换有数据库持久化；
6. 奖励和检查点在同一事务；
7. 数据库、在线内存、客户端三层状态一致；
8. 重复请求不会重复奖励；
9. 断线和重登可以从检查点恢复；
10. 多玩家剧情 NPC 不互相污染；
11. 所有相关 Java 文件不超过 500 行；
12. 测试和静态检查已记录；
13. 文档和索引已同步；
14. 未验证的协议、客户端行为和演出效果被明确标记。

这套标准的目标是让剧情规模随着 JSONC 和通用动作增长，而不是随着专用 Java 类数量失控。
