# `//eventspawnnpc` 手动生成事件分类 NPC

## 1. 当前支持范围

此命令接入客户端 NPC Tool 的事件模式，独立注册，默认 GM 权限，不是 `spawnnpc` 的别名。
本次支持**手动生成、无附加条件的新 NPC**，保存事件分类、闪光和缩放。
不实现完整的官方节日活动、活动阶段调度或按真实日期自动显隐。
事件编号 `0` 对应万圣节分类，绝不是“没有事件”。普通 NPC 的分类是 `-1`，
使用原来的 `//spawnnpc`；`eventspawnnpc` 要求事件分类为 `0..6`。

用户捕获的命令可直接由该入口解析：

```text
//eventspawnnpc 0 248 10 0 0 0 0 -1 -1 false false false 1.0 0
```

含义是：万圣节分类、Custom 外观来源 `10`、外观 `248`、静止、无脚本/标志/附加条件、
不覆盖已有 NPC、不忽略重复、不闪光、原尺寸。
生成位置和基础限制复用 `//spawnnpc`：当前角色前方一格、高度相同，空地且不与玩家/NPC 重叠。
**不会因执行该命令而开启万圣节活动**，成功消息也明确提示仅保存事件分类。
外观实际存在与显示效果仍取决于客户端资源，不能仅凭数字或静态检查保证画面。

## 2. 参数顺序

```text
//eventspawnnpc <eventId> <spriteId> <spriteRegion> <movement>
               <leashX> <leashY> <scriptOffset> <flagId> <flagValue>
               <updateExisting> <sparkles> <ignoreDuplicates> <spriteScale> <conditionCount>
               [conditionType conditionValue ...]
```

上面仅为换行排版，聊天输入应在一行完成。

| 位置（1 起） | 参数 | 当前规则 |
| --- | --- | --- |
| 1 | eventId | `0..6`：万圣节、圣诞、春节、愚人节、中秋、十周年、周年 |
| 2 | spriteId | `0..10000` |
| 3 | spriteRegion | `0/1/2/3/4/10`，不是 NPC 所在地图地区 |
| 4 | movement | 按当前地图地区校验原客户端普通移动值，静止 `0` |
| 5、6 | leashX、leashY | 各 `0..4` |
| 7 | scriptOffset | 当前只支持 `0` |
| 8、9 | flagId、flagValue | 当前必须均为 `-1`，标志条件未接入 |
| 10 | updateExisting | 当前必须 `false`，不猜测覆盖目标、不改原生 NPC |
| 11 | sparkles | 严格 `true/false`，控制原版 NPC 视觉效果位 |
| 12 | ignoreDuplicates | 当前必须 `false`，保持占位和重复检查 |
| 13 | spriteScale | 有限浮点值 `0.25..4.0`；这是服务端保护范围，`1.0` 为原尺寸 |
| 14 | conditionCount | 当前必须为 `0`；解析时先检查 `0..64` 和精确的 `14 + 2*count` 长度 |

布尔值不允许 `yes/0/1` 被静默转换。NaN、Infinity、浮点溢出、越界、参数缺失或尾部多余数据均拒绝。
条件数非零、标志条件、更新已有实体、忽略重复或非零脚本会返回具体中文原因，
**不会忽略这些要求后降级成无条件生成**。不支持的请求不分配 NPC、不写文件。

## 3. 独立保存与兼容

文件仍位于 `resource/npc/custom/<地区>/<地图>/npc_<固定序号>.jsonc`，
不会写入原地图或改客户端文件。`enabled` 控制是否加载，
`eventId` 只是保存的编辑器分类，未接入自动日历条件。

- 普通 NPC 保持 `version=1` 的 14 字段格式。
- 事件 NPC 使用 `version=2`，在相同基础字段后增加：

```json
"eventId": 0,
"sparkles": false,
"spriteScale": 1.0
```

- 版本 1 不允许夹带扩展字段，版本 2 必须完整填写这三个字段。
- 已有版本 1 文件不自动迁移或改写；读取时默认普通分类、无闪光、原尺寸。
- 保存前将视觉参数投影到尚未发布的 NPC，并校验在线属性与持久化定义一致，再原子保存。
- 重启恢复闪光、缩放和固定序号；运行时 Object ID 仍不落盘。
- `//eventdeletenpc` 停用版本 2 文件时保留事件分类和全部视觉参数，不降级到版本 1。
- 商店绑定仍使用地图名与固定 `entityIdx`，不使用事件编号或运行时 Object ID。

## 4. 客户端证据与协议

用户抓包是 C2S `0x08` NORMAL 聊天：128 字节，包含类型字节 `0`、
UTF-16LE 命令文本和两字节零终止。不增加 C2S opcode。

只读核对 Recaf 工作区 `28887-renamed.jar`：

- `f.uk_0.vl0()` 事件分支依次追加 `Yy.OE0`、`o5.cx0`、`rI[LK0]`、
  `Ne0[JJ0]`、`L0.cx0`、`COm9.cx0`、`Oy0`、`l0.cx0`、`aD0.cx0`、
  `xB`、`ln0`、`qY`、`JB0.X4`、条件数量和条件对。
- `f.tu_0.<clinit>()` 将 `-1` 命名为 NONE，`0` 命名为 HALLOWEEN，
  `1..6` 对应其余节日分类，与服务端已有 `HolidayType` 一致。
- 现有 S2C `0x12 SendAddGameEntityPacket` 已支持位 `2048`（`NpcEntity.unk6`）
  和位 `4096`（缩放尾部 float LE），无需修改编码器或 opcode 注册。
- `f.b0_0.Oj0()` 解析这些位；视觉字段进入 `MO.I80/coM6`。
  `f.z2_0.aE0()` 在 I80 开启时绘制 Custom 298 动画覆盖层，并使用 coM6 缩放。

普通 payload 26 字节，缩放不为 `1.0` 时追加 4 字节，共 30；
闪光位本身不增加 payload。事件分类不进入原生实体包，也不会伪造活动状态包。

## 5. 修改与验证

新增 `command.commands.EventSpawnNpcCommand`、`game.entity.EventNpcSpawnRequest`、
`game.entity.NpcSpawnAppearance`，均在 `org.pokemmo.gameserver` 下。
修改 `GameCommandModule` 注册入口，`NpcSpawnService` 复用生成链，
`CustomNpcDefinition/CustomNpcCodec/CustomNpcCatalog` 提供版本 2 保存、恢复及停用兼容。
存储目录、固定序号分配、文件锁、商店买卖和删除协议不变。

测试源码：

- `EventNpcSpawnRequestTest`：原始抓包参数、分类、边界、未支持功能明确拒绝、原生视觉位/尾部。
- `EventSpawnNpcCommandTest`：独立注册、GM 权限、合法请求进入生成链与非法请求前置拒绝。
- `EventNpcPersistenceTest`：版本 1 保持旧结构，版本 2 保存/重启/删除保留全部视觉字段。

2026-09-09：按约定未编译、未执行 JUnit、未启动服务器或客户端，
未执行抓包中的生成命令、未改写现有 NPC 文件。实际画面、混合版本启动和联机删除仍需运行验证。

只读静态检查：

```powershell
./tools/check_eventspawnnpc_contract.ps1
./tools/check_spawnnpc_contract.ps1
./tools/check_custom_npc_contract.ps1
./tools/check_npc_delete_contract.ps1
./tools/check_shop_contract.ps1
```
