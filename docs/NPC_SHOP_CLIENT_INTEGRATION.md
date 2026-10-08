# NPC 商店客户端修改方案

## 1. 交付范围

服务端已经实现独立店铺配置、NPC `shopId`、购买/出售事务、报价会话与重载。
店铺 JSONC 现在可直接用 `npcs: [{map, entityIdx}]` 指定店员并通过 `//reloadshops` 更新；
这仅改变服务端绑定解析，客户端原版窗口、报价字段和 `0xDC` 买卖协议均无需改变。
本文件同时记录客户端实际修改；**截至 2026-09-09 已完成源码修改，但未编译或启动客户端**。
客户端项目位于 `C:\Users\z3407\Desktop\28887-obf-project`。

不要只把购买按钮打开。必须同时实现报价读取、独立回收价格、扩展请求、资产刷新、
重复请求控制和关闭清理，最后再发送能力协商。

## 2. 改动位置

| 类型/位置 | 必需修改 |
| --- | --- |
| `pro.pokemmo2.shop.protocol.OpenMmoShopRequest` | `super(0xDC)`，编码 magic、版本、HELLO、BUY、SELL、CLOSE |
| `pro.pokemmo2.shop.protocol.OpenMmoShopControl` | 解码 S2C `0xDC`，处理协商、交易回执与关闭；非扩展 payload 转给原 `f.HJ` |
| `pro.pokemmo2.shop` 客户端模块 | 报价模型、连接状态、请求编码、收包桥接、原版窗口动作和背包刷新 |
| `f.ho_1`、`f.EL`、`f.k20_0` | 仅保留最小网络桥接，不承载商店业务 |
| `f.ho_1.tS` 游戏服分支 | 将 `case 220` 从未实现/default 分组移出，映射到新控制包 |
| 当前游戏连接的扩展状态 | 保存 negotiated、quoteId、nextRequestId、pendingRequest；不能跨连接复用 |
| `f.EL.Oj0/os0` | 在原生字段之后解析 flag `0x80` 的扩展报价，完整解析后再发布模型 |
| `f.lf0_2` | 保存报价编号和本店回收价表，不写全局道具价格 |
| `f.Uo.XH0/b5/eh/hA` | 使用本店回收白名单、独立价格及 `1..999` 数量限制 |
| `f.ie0_2` 或新的出售条目子类 | 报价模式下的 `oF0()` 返回本店回收价，保留旧模式构造器 |
| `f.oj0_0.run/Fr` | 报价模式发送 `0xDC`，不调用旧 `px0_0`/`wk_0` |
| `f.BU.ig`、`f.EL` 关闭路径 | 区分本地关闭和服务端关闭，避免关闭回声和误关新报价 |
| `f.su_1.os0` 对应的容器刷新后续处理 | 不保留被 S2C `0x40` 替换的旧 `RJ0/K5/ie0_2` 引用 |

客户端不再创建独立商店窗口。原版 `f.BU.yn(...)` 继续创建 `f.Uo`，因此主题、布局、
买入/出售页签、商品图标、数量控件、确认框和关闭行为均与原版一致。`ShopClient` 只
保存报价并接管原版确认动作；原版 `f.Uo.XH0()` 在当前报价存在时使用本店回收白名单
和回收价格。

`0xDC` 是本项目的自定义扩展，不是官方商店 opcode。当前客户端 `case 220` 已由
原 `f.HJ` 使用，因此商店桥接必须识别独立 magic `0x7E`，不允许直接替换 HJ；
非 `0x7E` payload 继续交给 HJ。不要使用 `0xE0..0xEF`，这些值在当前客户端已有处理器。

## 3. 能力协商

游戏连接进入游戏协议后发送：

```text
C2S: dc 7e 01 00
S2C: dc 7e 01 00
```

`7e` 是扩展 magic，`01` 是扩展版本，`00` 是 HELLO/协商确认。可以在角色完成加载后发送；
收到确认前不能声称支持可交易商店。断线时清除全部扩展状态，重连重新协商。
**只能在全部相关处理器安装完成后启用 HELLO**，否则服务端会发送旧客户端无法解析的报价。

未协商的连接只收到原生只读 `0x23`，无扩展尾部，也不能执行买卖。
重复 HELLO 只重发确认，不重置当前报价或请求序号。
客户端收到带 `magic=0x7E` 且通过完整校验的扩展报价时，会将其视为服务端已接受
HELLO，并修复客户端协议模式切换期间可能丢失的本地协商标志；普通原生 `0x23`
不会触发该回退。

## 4. 原生 `0x23` 的扩展尾部

原生货币类型、flags、布局、可选字段、买入商品列表保持 `EL` 当前解析顺序。
`flags & 0x80 != 0` 表示在全部原生字段之后追加：

| 字段 | 类型 | 校验 |
| --- | --- | --- |
| extensionVersion | unsigned byte | 必须为 `1` |
| quoteId | long LE | 必须大于 `0`，按 Java `long` 原样保留 |
| sellCount | unsigned short LE | `0..1024` |
| sellItems[].itemId | unsigned short LE | `1..65535`，不得重复 |
| sellItems[].sellPrice | int LE | 必须大于 `0` |

原生买入列表仍是 `itemId:short, unitsPerOffer:short, quantityLimit:short, buyPrice:int`。
本版本固定 `unitsPerOffer=1`、`quantityLimit=0`；买入/回收列表均最多 1024 项。
`0x01` 启用买入，`0x02` 启用出售，`0x04` 显示出售页签，`0x80` 表示报价扩展。
只回收店铺允许原生买入列表数量为 `0`。无扩展时保留旧客户端路径。

不要把 flag `0x80` 加进 `hasAdditionalEntries` 的判断，也不要把它当成兑换商店
`cr_0.Xz0` 的附加条目。需在 `EL.Oj0()` 原有分支全部结束后解析：

```java
if ((flags & 0x80) != 0) {
    int version = Rj.get() & 0xFF;
    long quoteId = Rj.getLong();
    int count = Rj.getShort() & 0xFFFF;
    // 先校验 version、quoteId、count 和剩余字节，再读取 count 组 short + int。
    // 校验重复 ID、正数价格和末尾无剩余数据后，一次性发布到新的店铺模型。
}
```

打开新报价后 `nextRequestId=1`，清空 pending。资产列表刷新不能重置报价或请求序号。

## 5. 出售 UI 必须使用店铺报价

当前 `ie0_2.oF0()` 直接返回 `XH0.gQ()`；`mc0_1.gQ()` 从全局回收价或基础价格的一半计算。
这不是 NPC 店铺价格，不能继续用于扩展商店，也不能临时修改全局 `TD/d2` 后再恢复。

建议为扩展出售条目增加独立的 `sellPrice`，或增加专用子类：

```java
@Override
public int oF0() {
    return quotedSellPrice;
}
```

保留 `ie0_2` 原有构造器和旧模式行为。`Uo.XH0()` 在报价模式下：

1. 从**最新**主背包 `RJ0` 重新取得拥有道具。
2. 仅保留回收价表中存在 `itemId` 的条目；不要再要求全局 `TD/gQ()` 为正。
3. 保留已有不可交易/关键/绑定道具过滤；地区限定与 PvP 奖励道具不可出售。
4. 展示当前拥有道具的名称、颜色和数量，但单价、总价和确认文本必须统一使用本店价格。
5. 请求使用 `ownedItemId`（当前 `uq().Sa` / `K5.nn.Br.Sa`），不是道具索引。

购买选项使用原生列表中的报价。报价模式不要走 `mc0_1.Iq` 对应的招式教学分支；
此处购买的是道具，不是支付费用直接教招式。所有路径都必须尊重 flags、pending 和数量上限。
显示总价采用 `long` 运算，禁止 `int price * amount` 溢出。

## 6. C2S `0xDC` 买卖与关闭

所有请求以 `magic:byte=0x7E, version:byte=1, action:byte` 开头。没有价格字段：

| action | 后续字段 | payload 总长度，不含 opcode |
| ---: | --- | ---: |
| `0` HELLO | 无 | 3 |
| `1` BUY | quoteId:long LE，requestId:int LE，itemId:unsigned short LE，amount:unsigned short LE | 19 |
| `2` SELL | quoteId:long LE，requestId:int LE，ownedItemId:long LE，amount:unsigned short LE | 25 |
| `3` CLOSE | quoteId:long LE | 11 |

`requestId` 是 `1..2147483647` 的连续正整数，BUY/SELL 共用序列，不能按页签分别计数。
`amount` 必须为 `1..999`；出售时还要受实际持有数量约束。关闭不占用请求序号。
`RE` 框架负责 opcode 和帧头，`ig0` 只编码 payload，沿用已有连接的 little-endian ByteBuffer。

以报价编号 `0x0102030405060708`、请求 `1`、购买两颗精灵球为例，构造的预期报文为：

```text
dc 7e 01 01 08 07 06 05 04 03 02 01 01 00 00 00 8c 13 02 00
```

这个示例不是实际抓包。报价编号必须使用服务端本次下发的值，不能硬编码。

一次只允许一个 pending 请求，发送后禁用重复确认。超时不能直接生成新序号重买：
在同一连接、同一报价下只能原样重发当前 pending 请求；收到匹配回执后再递增序号。
服务端只缓存最近一次请求，完全一致的重发只返回原结果，不再次写数据库。
更早请求、同序号不同内容或跳号会被拒绝并关闭报价。断线后禁止自动重放旧请求。

## 7. S2C `0xDC` 控制与结果

公共头为 `magic:byte=0x7E, version:byte=1, kind:byte`：

| kind | 后续字段 |
| ---: | --- |
| `0` CAPS | 无 |
| `1` RESULT | quoteId:long LE，requestId:int LE，action:byte，status:byte，message:UTF-16LE 零终止字符串 |
| `2` CLOSED | quoteId:long LE，reason:UTF-16LE 零终止字符串 |

RESULT 的 `status=0` 成功，`1` 已拒绝/未成交，`2` 提交结果暂时无法确认。
只有匹配当前报价和 pending 的回执才能完成该请求；重复回执不得再次递增序号。
正常业务失败也会消耗当前请求序号，下一次尝试使用下一个序号。

成功时服务端按顺序发送：`0x40` 主背包快照、`0x0C` 金钱更新、`0xDC RESULT`。
`su_1.os0()` 会替换整个 `RJ0`，因此匹配 RESULT 时必须重新构建出售列表、清除失效
`K5/ie0_2` 选择，再刷新金钱与数量控件；不能仅修改原来的列表控件或按本地计算扣款。
重复请求的缓存回执不重发旧资产快照，避免覆盖之后的真实资产变化。

`status=2` 后服务端会关闭商店。客户端显示中文原因，不自动重试；重新开店会从数据库
读取金钱和背包。协议校验失败、连接断开或进程重启后的旧报价同样不能继续使用。

## 8. 关闭与生命周期

- 用户关闭：发送一次 CLOSE，禁止继续提交该报价；等待 CLOSED 后恢复正常交互。
- 服务端 CLOSED：只有 quoteId 匹配当前窗口时才关闭，清理 pending/报价，不回发 CLOSE。
- 服务端还会发送原生 `23 ff`；对应 `EL` 关闭路径也必须走不回传的本地关闭方法。
- 建议将 `BU.ig` 拆成用户关闭入口和 `closeShopLocally`，用明确参数或方法区别来源。
- `BU.yn` 替换旧窗口时不能让旧窗口的关闭回调误清新报价。
- 移动、转向、换地图、重连、服务端重载均可使报价失效；连接关闭时清除能力协商状态。
- 旧 CLOSED/RESULT 不能影响后来打开的新报价。不要使用固定延迟解决窗口和回执顺序。

## 9. 实施顺序与验收

1. 先实现模型、`0xDC` 编解码和 `0x23` 扩展解析，保持 HELLO 关闭。
2. 完成报价价格显示、独立回收条目、按钮状态、请求序号、关闭和资产刷新。
3. 在本地调试构建中启用协商，再进行真实客户端回放；不要给尚未完整适配的客户端发 HELLO。
4. 测试两座城市同种道具不同买入价/回收价、仅回收商品、购买满堆叠后新建堆叠和全部出售。
5. 测试余额不足、数量不足、跨角色 Object ID、绑定/PvP/地区道具、金额上限和重复请求。
6. 开窗后执行 `//reloadshops`：旧窗口关闭，旧报价不能成交，新窗口显示新价格。
7. 测试关闭后立即开另一家店、移动/转向/换图/断线、旧回执迟到、失败后再提交与超时原样重发。
8. 测试旧客户端仍只有只读商店，以及 PC、GTL、邮箱、附近玩家交易没有被改写。

服务端对应的字段、静态检查范围和未运行项见 [商店协议](NPC_SHOP_PACKET.md)。
