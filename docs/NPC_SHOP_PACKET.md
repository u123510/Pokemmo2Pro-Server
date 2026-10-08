# NPC 商店配置、买卖与协议

## 1. 当前状态

服务端已实现独立店铺 JSON/JSONC、自定义买入/回收价、店铺文件直接指定 NPC、买卖事务、
报价与重复请求控制，以及 GM 配置重载。**客户端需要按
[修改方案](NPC_SHOP_CLIENT_INTEGRATION.md)完成适配，才能实际使用买卖。**
未协商扩展协议的旧客户端仍收到原生只读商品窗口，不会被直接启用交易按钮。

旧的 `resource/item/NpcShops.jsonc`、`NpcShopCatalog`、`ItemShopEntry` 和
`ShopInteractionService` 已迁移，不再作为运行时输入或业务入口。

## 2. 店铺配置

```text
resource/shop/
  kanto/viridian_city/mart_clerk.jsonc
  kanto/pewter_city/mart_clerk.jsonc
```

一个文件对应一家店铺。`ScriptManager` 加载 `Item.bin` 后扫描相邻的 `resource/shop`
目录；文件路径负责组织，`shopId` 负责唯一标识，可被多个 NPC 共享。例：

```jsonc
{
  "shopId": "kanto.viridian_city.mart",
  "npcs": [
    { "map": "ViridianCity_Mart", "entityIdx": 0 }
  ],
  "buyEnabled": true,
  "sellEnabled": true,
  "items": [
    { "itemId": 5004, "buyPrice": 200, "sellPrice": 100 },
    { "itemId": 5017, "buyPrice": 200, "sellPrice": 100 },
    { "itemId": 5022, "buyPrice": null, "sellPrice": 150 }
  ]
}
```

- `buyPrice` 为玩家购买单价，`sellPrice` 为玩家卖给店铺的回收价，全部使用角色金钱。
- 价格必须是正的 32 位整数，null/未填写表示该方向不可用；两种价格不能同时为空。
- `buyEnabled`、`sellEnabled` 必须显式填写布尔值；全关代表暂停营业。
- 每店 1..1024 件商品，ID 为 1..65535，不能重复，必须存在且有有效堆叠上限。
- 禁止账号绑定、关键和已知不可交易道具；`Item.bin` 中部分商城道具的 `itemType=0`
  会映射为 null，但只要道具存在且堆叠上限有效，仍允许作为商店商品；
  具体拥有道具的地区/PvP 限制在出售事务中再校验。
- 单文件最大 1 MiB；未知字段、非整数/溢出价格、非对象条目和多余 JSON 内容拒绝加载。
- `shopId` 使用 1..128 位小写字母、数字、点、下划线和连字符，首字符必须是字母或数字。
- 配置直接决定成交价格，不回退或写入全局 `Item.bin` 价格。跨店套利是否允许由运营定价决定。

### 2.1 只编辑店铺文件指定 NPC（推荐）

`npcs` 与 `items` 同级，放在本店 JSONC 内，不必修改地图里的 `shopId` 或 `script`：

```jsonc
"npcs": [
  { "map": "CeruleanCity_Mart", "entityIdx": 1 }
]
```

- `map`：地图文件名去掉 `.json`，大小写完全一致，不是城市中文名、目录路径或运行时地图编号。
- `entityIdx`：该地图 `npcs` 中的原始序号，对应日志 `NPC名称=npc_1` 的 `1`，
  不是数组位置、坐标、外观 `graphicsId` 或长数字 NPC Object ID。
- 以上例子把华蓝市商店地图的女性 NPC（原脚本 `..._Woman`）作为店员，不要求脚本包含 `Clerk`。
- 换店员：只替换这一项的序号，例如 `1` 改成 `2`。添加店员：在数组再加一项。
- 多个 NPC 可以指向同一家店；一个 NPC 不允许被两个店铺文件同时指定。
- 每店最多 1024 个绑定；非对象、未知字段、负数/小数/溢出序号、重复 NPC 均拒绝。
- 地图和 NPC 必须已被服务器加载，地图名必须唯一；拼错或未加载会返回带文件名的中文错误，
  不会随意找相似名字的 NPC。显式绑定不会生成 NPC、改变外观、移动 NPC 或取消隐藏条件。
- 原脚本保留，但匹配商店时优先开店；不同时执行对话或制作业务。
- 商品、价格、`npcs` 修改统一通过 [`//reloadshops`](CHAT_RELOADSHOPS_COMMAND.md) 生效，
  不需要玩家重新登录或重载地图。重载成功关闭旧报价，重新与店员交互即可。

常磐市示例指定 `ViridianCity_Mart / 0`；尼比市示例指定 `PewterCity_Mart / 2`。
不要假定所有地图的店员都是 `entityIdx=0`。直接编辑这两个店铺文件的 `npcs` 即可更换店员。
当前实现首次部署仍需自行编译并重启游戏服，之后配置重载无需再次编译，客户端协议不变。

### 2.2 旧配置兼容与移除绑定

- **没有 `npcs` 字段**：兼容旧方式，地图 NPC 的 `shopId` 仍有效。
- **有 `npcs` 字段**：该列表完全接管本店 NPC 绑定，不再使用地图中指向本店的旧 `shopId`。
  因此从 `0` 改成 `1` 后，原 `0` 号不会因旧地图字段仍存在而继续开这家店。
- **`"npcs": []`**：明确解除本店全部绑定，旧地图字段也不会恢复。不要用删除 `npcs` 字段代替解绑。
- 显式列表可将原来绑定另一家旧店铺的 NPC 改绑到本店；没有显式匹配时才检查兼容规则。
- 同一 NPC 被多家店显式指定：重载整批失败，旧绑定与价格保持不变；首次启动时不启用冲突双方，
  不会按文件顺序决定谁覆盖谁。
- 要让旧式店铺完全改由店铺文件管理，给它补上 `npcs`，无需删除地图文件里的字段。

### 2.3 柜台仍属于地图

`//spawnnpc` 新建的自定义 NPC 存放于独立 `resource/npc/custom`，不修改原地图。
它的固定高序号（从 100000 起）同样可用于 `npcs.entityIdx`；启动会先恢复自定义 NPC 再校验店铺。
修改该 NPC 的出生文件需重启，修改店铺的绑定/商品仍用 `//reloadshops`。
在线删除自定义店员使用 [`//eventdeletenpc`](CHAT_EVENTDELETENPC_COMMAND.md)：
删除等待已有商店成交完成，在目录写锁下停用并移除 NPC，之后关闭该店员的报价。
店铺文件不会自动改写，需移除对应 `npcs` 绑定再重载，避免下次启动/重载报目标不存在。

当前常磐市和尼比市地图保留
`"interactionCounters": [{"x": 3, "y": 3}]`。站在 `(4,3,2)` 朝左时会跨过明确柜台，
匹配 `(2,3,0)` 店员；不会把普通障碍当作柜台。地图原始图块、碰撞位和客户端地图编码不变。
相邻普通 NPC 不需要柜台配置。新建地图/NPC、修改坐标/柜台/外观仍需重新加载地图或重启，
`//reloadshops` 只会更新已有 NPC 的商店职责，不会重建地图。

启动或重载时服务端会输出中文诊断：

```text
店铺目录加载完成: 目录=C:\...\resource\shop, 店铺数量=2, 错误数量=0
店铺配置已启用: shopId=kanto.viridian_city.mart, 商品数量=4, 可买=true, 可卖=true
店铺 NPC 绑定已启用: 店铺=kanto.viridian_city.mart, 地图=ViridianCity_Mart, NPC序号=0
```

如果只看到“NPC 绑定的店铺不存在”，请先检查上面的实际目录和
`resource/shop/kanto/viridian_city/mart_clerk.jsonc`；配置目录现在由 `ScriptManager`
按绝对规范化路径解析，不依赖从 `server.game` 子目录启动。

## 3. 服务端职责

| Package / 类型 | 职责 |
| --- | --- |
| `game.shop.ShopConfigLoader`、`ShopCatalog` | 文件校验、独立目录、不可变版本快照与原子重载 |
| `game.shop.ShopItem`、`ShopDefinition`、`ShopRequest` | 商品、店铺和经过校验的请求模型 |
| `game.shop.ShopNpcBinding`、`ShopNpcBindings` | 地图名/序号绑定模型、已加载地图目标校验、冲突排除、不可变 NPC 索引及旧绑定兼容 |
| `game.shop.ShopAccess`、`ShopSession`、`ShopSessions` | 站位、NPC、报价、序号和窗口清理 |
| `game.shop.ShopService` | 开窗、事务编排、提交后的在线状态和客户端同步 |
| `services.shop.ShopTransactions` | 角色与主背包锁定，金钱和道具的同一事务 |
| `services.shop.ShopPurchase`、`ShopSale`、`ShopItemPolicy` | 购买堆叠、回收扣除、道具限制与快照大小校验 |
| `protocol.packets.c2s.ShopControlPacket` | C2S 扩展报文的版本、长度、字段校验 |
| `protocol.packets.s2c.SendItemShopPacket`、`SendShopControlPacket` | 原生窗口、报价扩展、协商和交易结果编码 |

以上 package 均以 `org.pokemmo.gameserver` 为前缀。

`SceneInteractionService` 按原来的 C2S `0x27` 无参数场景请求或 `0x22` 明确 NPC ID
选择目标，已绑定店铺优先进入 `ShopService`，不依赖剧情事件开关。未绑定 NPC 和电脑
保持原链路；无效 `shopId` 只反馈店铺错误，不落入无关剧情脚本。

## 4. 协议选择和客户端证据

客户端参考项目：`C:\Users\z3407\Desktop\28887-obf-project`。
已只读核对 `f.ho_1`、`EL`、`lf0_2`、`Uo`、`ie0_2`、`mc0_1`、`BU`、
`px0_0`、`wk_0`、`su_1`、`RE` 和 `GH`。

- 原生商店窗口为 S2C `0x23 -> EL -> BU.yn -> Uo`，价格、名称、图标来自列表和客户端道具数据。
- 原生购买 C2S `0x23`、出售 C2S `0x24` 与本仓库旧交易兼容入口字段长度相同，不能靠长度区分。
- 本项目新增双向 `0xDC` 扩展，payload 以 `magic=0x7E, version=1` 开头；
  保持旧 `0x23/0x24` 交易注册不变，扩展商店绝不发送旧买卖请求。
- 客户端 `0xDC` 已有原 `f.HJ` 处理器，因此桥接先检查 `0x7E`，非扩展 payload
  继续交给 HJ；它是 OpenMMO 自定义扩展，不宣称属于官方协议。
- 只有收到 C2S `dc 7e 01 00` 后才发送扩展控制包或启用带扩展尾部的商店窗口。

### 4.1 S2C `0x23`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| currencyType | byte | `0` 金钱；单独 `0xFF` 表示关闭 |
| flags | byte | `0x01` 买入、`0x02` 出售、`0x04` 出售页签、`0x80` 扩展尾部 |
| layoutType | byte | `0` 道具 |
| buyCount | unsigned short LE | 0..1024 |
| buyItems[].itemId | unsigned short LE | 配置中允许买入的道具索引 |
| buyItems[].unitsPerOffer | short LE | 固定 `1` |
| buyItems[].quantityLimit | short LE | 固定 `0`，原生默认上限 |
| buyItems[].buyPrice | int LE | 配置的玩家买入单价 |
| extensionVersion | byte | 仅 flags `0x80` 时存在，固定 `1` |
| quoteId | long LE | 仅扩展模式，本次窗口正数报价编号 |
| sellCount | unsigned short LE | 仅扩展模式，0..1024 |
| sellItems[].itemId | unsigned short LE | 仅扩展模式，允许回收的道具索引 |
| sellItems[].sellPrice | int LE | 仅扩展模式，配置回收单价 |

旧客户端 flags 固定为 `0`，没有尾部；只有回收服务的店铺对旧客户端给出升级提示。
当前常磐市四件买入/四件回收商品，扩展 payload 为 `5 + 4*10 + 11 + 4*6 = 80` 字节，
含 opcode 为 `81` 字节。仅浏览报文仍为 `46` 字节。此处长度依据源码计算，未实际抓包。

### 4.2 C2S `0xDC`

共同字段 `magic:byte=0x7E, version:byte=1, action:byte`，然后按操作读取：

| action | 字段顺序 | payload 字节数 |
| ---: | --- | ---: |
| 0 | 无后续字段，协商 | 3 |
| 1 | quoteId:long LE，requestId:int LE，itemId:unsigned short LE，amount:unsigned short LE | 19 |
| 2 | quoteId:long LE，requestId:int LE，ownedItemId:long LE，amount:unsigned short LE | 25 |
| 3 | quoteId:long LE，关闭 | 11 |

所有报价、目标 ID 和请求序号必须为正；数量为 `1..999`。请求不带价格、角色 ID 或
店铺 ID，由当前 Session 和服务端报价确定。未知版本、操作、非法长度或越界字段
走既有协议解码错误路径；正常业务拒绝通过结果包反馈，不关闭网络连接。

### 4.3 S2C `0xDC`

共同字段 `magic:byte=0x7E, version:byte=1, kind:byte`：

| kind | 后续字段 |
| ---: | --- |
| 0 | 无，确认能力协商 |
| 1 | quoteId:long LE，requestId:int LE，action:byte，status:byte，message:UTF-16LE 零终止 |
| 2 | quoteId:long LE，reason:UTF-16LE 零终止，关闭指定报价 |

status `0` 成功、`1` 业务拒绝、`2` 执行/提交结果暂不能确认。所有服务端反馈使用中文。
协商和买卖请求本身无字符串。成功时先发送完整 `0x40` 背包、`0x0C` 金钱更新，
再发送 RESULT；客户端需要按最新背包重建出售列表。

## 5. 会话、价格与重复请求

协商后的开窗会先从数据库同步当前资产，再生成报价，设置 `InteractType.SHOP`。
报价绑定当前连接、角色、频道、地图、NPC、站位和配置版本。每次买卖重新校验
这些条件、当前配置快照解析出的店铺绑定、可交互性和朝向；报价不能用于远程交易或跨 NPC 使用。
`ShopService` 开店和买卖使用同一个绑定解析入口，不把 JSONC 绑定回写到 NPC 的 `shopId`。

BUY/SELL 共用从 `1` 开始的连续 requestId。当前报价最近一次请求完全相同的重发
只重发原结果，不再次执行 SQL，也不重放旧资产快照。较早请求、跳号或同序号不同内容
会拒绝并关闭报价。一次只允许一个在途请求，业务失败也消耗其序号。

金额事务与在线同步持有配置读锁，重载发布持有写锁，交易中途不能更换价格。
重载成功关闭旧版本报价；失败保留旧目录和窗口。改变价格后旧请求不会按新价格偷偷成交。
SQL/连接异常返回 status `2` 并关闭报价，不自动重试；重新开店会查询实际资产。
连接断开或进程重启后的报价不恢复，旧请求不能在新连接继续执行。

客户端若收到合法的扩展报价，即视为服务端已接受能力协商；这只修复客户端协议模式切换
期间本地状态丢失，不会接受没有 `magic=0x7E`、版本错误或报价字段非法的报文。

关闭、移动、转向、换地图、断线和重连通过 `ShopSessions` 幂等清理。
移动先关闭商店再执行原移动校验；关闭回执带 quoteId，迟到的关闭请求不能关闭新报价。
SHOP 状态使其他 NPC/电脑、战斗请求和附近交易按既有忙碌规则拒绝，不持久化成剧情上下文。

## 6. 数据库与安全边界

`ShopTransactions` 在一个 jOOQ 事务中：

1. 按当前角色 ID 锁定 `character`，取得真实余额。
2. 查找名称为 `inventory` 的主背包；按 owner + inventory 条件、有序锁定道具记录。
3. 购买校验本店商品、正数报价和余额，先填充相同无特殊限制的堆叠，再创建必要的新堆叠。
4. 出售只按当前主背包的 owned-item Object ID 扣除，禁止绑定/关键/不可交易和地区/PvP 限定道具。
5. UPDATE/DELETE 同时使用 item ID、owner ID、inventory ID 和原数量条件；空堆叠才物理删除。
6. 金钱计算使用 long，拒绝负余额和超过 `Integer.MAX_VALUE` 的结果。
7. 道具每堆不超过资源上限；快照按真实编码大小校验，预留帧头后最多 65000 字节，
   防止交易已提交却无法发送客户端背包。当前 schema 没有额外背包槽数规则。
8. 检查所有 SQL 影响行数，任何失败整体回滚；事务成功后才同步在线金钱和客户端背包。

不修改 schema 或 jOOQ 生成文件，不调用分离的扣款/增删物品 API 拼凑半笔交易。
道具名称、图标和基础属性仍由 `Item.bin`/客户端资源提供，不修改全局售价字段。

## 7. 验证和剩余范围

本次店铺直接绑定 NPC 的改动按项目要求未编译、未启动服务器、未连接或修改数据库，
也没有修改客户端。之前的客户端原版界面适配记录见客户端修改方案。
已进行源码/配置/协议静态检查。真实 SQL 回滚、并发锁定、断线提交不确定性、窗口显示、
完整买卖与重载生命周期仍需用户在测试环境验证，不能把静态断言视为集成测试。

可重复运行的静态检查脚本为 [`tools/check_shop_contract.ps1`](../tools/check_shop_contract.ps1)：

```powershell
./tools/check_shop_contract.ps1
```

脚本只读配置、地图、Item.bin 和源码，核对商品、报价编码样例、注册、事务归属条件、
重载/重复请求分支和清理调用点，不执行 Java、Gradle 或数据库操作。
脚本还核对店铺 `npcs` 格式、对应地图文件、NPC 序号、重复目标及开店/买卖统一绑定调用。

`server.game/src/test/java/org/pokemmo/gameserver/game/shop/ShopNpcBindingsTest.java`
提供解析边界、普通 NPC 绑定、更换/解绑、旧方式兼容、跨地图身份、冲突与不可变快照的
JUnit 回归用例；本次未编译或执行这些用例，不能把静态检查当作 JUnit 通过。

回归至少覆盖：两城不同价格、仅回收商品、堆叠拆分、余额/数量不足、跨角色 ID、
绑定和奖励道具、金钱溢出、重复/跳号请求、重载失败保留旧配置、重载成功关闭旧报价、
移动/转向/换图/断线，以及旧客户端与玩家交易兼容。

本地示例未实现官方剧情/徽章解锁、库存数量、折扣、跨币种或特殊颜色商品定价。
客户端完整实施清单见 [客户端修改方案](NPC_SHOP_CLIENT_INTEGRATION.md)。
