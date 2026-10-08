# 常磐市捕获教学

## 当前范围

这是接在“大木包裹、图鉴和 5 个精灵球”之后的下一章。
玩家完成大木章节后进入 `ViridianCity`，地图加载确认后自动触发教学老人对白，
并进入一场固定的 5 级独角虫野生战斗。NPC 交互仍作为手动重试入口。

服务端复用现有 `WildBattle`、捕获概率、捕获动画、精灵球扣除、
PARTY/PC 入库和图鉴更新，不新增捕获协议。

当前没有确认原版“老人自动操作、自动投球、自动捕获”的完整客户端演出，
所以本实现由服务端自动提交普通精灵球动作，玩家不需要打开战斗菜单或手动抛球。
成功捕获独角虫后才完成章节；
逃跑、击败或失败不会完成，玩家可以再次与老人交互。

## 配置

配置文件：

```text
resource/story/kanto/viridian_city/catch_tutorial/chapter.jsonc
```

```jsonc
{
  "enabled": true,
  "wildSpecies": 13,
  "wildLevel": 5,
  "text": {
    "introduction": 1563982,
    "completed": 1564315
  }
}
```

字段说明：

| 字段 | 当前值/要求 | 说明 |
| --- | --- | --- |
| `enabled` | `true/false` | 只控制本章 |
| `wildSpecies` | `13` | 独角虫，必须存在于服务端图鉴 |
| `wildLevel` | `5` | 教学战斗等级，范围 `1..100` |
| 自动捕获球 | `5004` | 普通精灵球；背包没有该道具时不自动开始教学 |
| `text.introduction` | `1563982` | 原生“我应该教你怎么做”对白 |
| `text.completed` | `1564315` | 原生“是不是受益匪浅”对白 |

文本编号来自客户端 `data/strings/strings_zh_dump.xml`。
修改文本前必须重新核对客户端文本，不要把文本 ID 当作数据库阶段。

## 地图和 NPC

| 项目 | 值 |
| --- | --- |
| 地图 key | `ViridianCity` |
| 地区/地图组/地图 | `(0,3,1)` |
| 教学 NPC | `npc_3` |
| 地图资源脚本 | `ViridianCity_EventScript_TutorialOldMan` |
| 地图坐标 | `(21,6)` |
| NPC Z/地图层 | 原地图资源中的 `elevation=2` |

运行时 Object ID 会在地图初始化时生成或恢复，不能写入剧情配置或数据库。
NPC 判定使用当前地图和固定资源序号 `npc_3`，不使用 Object ID。

## 阶段和完成状态

本章不新增表，使用 `character.story_line_flag[1]` 的第 5 位：

```text
VIRIDIAN_CATCH_MASK = 1 << 4
```

PostgreSQL 数组下标 `[1]` 是关都，Java 数组下标为 `0`。
该 bit 没有占用当前四个已登记关都主线 bit：

| 状态 | 条件 | 行为 |
| --- | --- | --- |
| 未完成 | bit 4 为 `0`，且 `oak_lab_status < 6` | 不触发捕获教学 |
| 待教学 | bit 4 为 `0`，且 `oak_lab_status >= 6` | 进入地图后自动开始；与 `ViridianCity/npc_3` 交互可手动重试 |
| 教学完成 | bit 4 为 `1` | 不再开始战斗，只播放完成对白 |

`oak_lab_status` 和 `oak_parcel_status` 仍由前两章管理。
本章不会重置开场、包裹、初始宝可梦、队伍或图鉴数据。

## 运行流程

```text
登录
  -> 读取大木章节和捕获教学 bit
  -> 完成大木章节后，进入常磐市
  -> 地图加载确认后自动触发（npc_3）
  -> 播放原生介绍对白
  -> 生成 5 级独角虫 EVENT 宝可梦
  -> 启动普通 WildBattle
  -> 玩家使用真实精灵球捕获
  -> 现有 PokemonCaptureService 扣球、写 PARTY/PC、更新图鉴
  -> 战斗结果为 CATCH_POKEMON
  -> ViridianCatchStore 原子写入 story_line_flag bit 4
  -> 客户端发送战斗结束回执
  -> 播放完成对白并清理剧情状态
```

自动抛球使用现有战斗 `handlePlayerUseItem` 和 `BattleCaptureService`，
客户端仍播放原生捕获动画；服务端不会伪造捕获事务结果。
如果玩家没有普通精灵球 `5004`，服务端不自动启动教学战斗；
手动与老人交互时会返回中文提示。

## 代码位置

### `org.pokemmo.gameserver.game.story`

- `ViridianCatchCatalog`：读取并校验本章 JSONC、文本、独角虫和精灵球资源。
- `ViridianCatchStory`：NPC 触发、原生对白、教学战斗启动、战斗完成和重连状态。
- `StoryService`：统一路由本章登录、NPC 和战斗回执。
- `PalletStoryState`：保存本章临时完成状态和教学战斗引用。
- `PalletStoryScene`：复用原生对白、序号、超时和场景清理。

### `org.pokemmo.gameserver.services.story`

- `ViridianCatchStore`：按角色 ID 与账号 ID 读取/锁定 `story_line_flag`，
  原子设置 bit 4，并查询主背包中是否有捕获球。

### 既有战斗入口

- `BattleGenerator.generatorWildBattle`：创建野生战斗。
- `BattleCaptureService`：使用球、捕获判定和捕获动画。
- `PokemonCaptureService`：捕获事务、PARTY/PC 槽位和图鉴。
- `BattleOutcomeResolver`：把战斗结束分发给 `StoryService`。
- `BattleFinishSuccessPacket`：把客户端结束回执分发给剧情路由。

不新增 opcode，不修改 `GameProtocol` 的协议编号。

## 持久化和归属

完成标记保存事务：

```text
锁定 character(id + account_id)
  -> 读取 story_line_flag
  -> 保留其他地区和其他 bit
  -> 设置关都 bit 4
  -> 使用 id + account_id 更新
  -> 提交
  -> 更新在线 CharacterData
```

捕获本身由既有 `PokemonCaptureService` 在另一笔捕获事务中完成，
它已经负责球扣除、宝可梦归属和图鉴更新。本章不会伪造捕获成功，
也不会在战斗尚未变为 `CATCH_POKEMON` 时写完成标记。

如果完成标记事务失败，服务端记录中文错误并不发送“教学完成”的成功逻辑；
捕获战斗本身仍遵循既有捕获事务结果。需要重连后重新读取标记并继续维护。

## 断线和重复请求

- 对白等待期间断线：清理 continuation、timer 和交互状态，不写完成标记。
- 教学战斗期间断线：保留现有 BattleManager，重连复用战斗 Session。
- 捕获成功、战斗结束但尚未收到 `0x33`：完成标记已经按服务端战斗结果处理，
  结束回执只播放一次完成对白并清理战斗引用。
- 逃跑、击败或失败：不设置完成 bit，下次进入地图或与老人交互可以重新开始。
- 已完成玩家：不重复生成独角虫，不重复消耗精灵球，只播放完成对白。
- 其他 NPC 和其他玩家不受本章影响。

## 验证

2026-09-14 已完成源码和资源静态检查：
`check_viridian_catch_contract.ps1` 39 项、`check_story_contract.ps1` 117 项、
`check_oak_parcel_contract.ps1` 86 项。未编译、未运行 JUnit、未启动服务端或客户端、
未连接或修改实际数据库，因此不能宣称客户端实机流程已通过。

静态检查应覆盖：

```powershell
./tools/check_viridian_catch_contract.ps1
./tools/check_story_contract.ps1
./tools/check_oak_parcel_contract.ps1
git diff --check
```

需要实机核对：

1. 阶段 6、bit 4 为 0 且有 `5004` 的角色进入地图后自动触发；
2. 介绍对白之后出现 5 级独角虫野生战斗；
3. 服务端自动提交精灵球，玩家无需点击战斗菜单；
4. 捕获成功后球数量、PARTY/PC 和图鉴正确更新；
5. 捕获成功后重登不再次触发；
6. 逃跑/失败后可以重试；
7. 没有 `5004` 时不自动启动教学，手动交互显示中文提示；
8. 两名玩家的教学战斗和进度互不影响；
9. 对白、战斗结果和完成回执前后分别断线。

按仓库规范，本章实现阶段默认不编译、不启动服务、不连接真实数据库；
未实机验证的项目必须标记为“未执行”。

## 重置测试角色

只对测试角色操作，并先完整退出和备份。
若只重置本章，清除关都 `story_line_flag` 的 bit 4，保留其他 bit：

```text
story_line_flag[1] = story_line_flag[1] & ~(1 << 4)
```

不要把整个数组设为零，否则会清除其他关都故事线。
本章重置不会自动移除已经捕获的独角虫或其他奖励。
