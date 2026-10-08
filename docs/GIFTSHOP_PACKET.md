# 礼品商城封包与协议规范

## 1. 概述

客户端打开“礼物商城”（Gift Shop）时，通过 C2S `0x70` 向上汇报打开请求。服务端必须响应 S2C `0x70`，将当前商城可购商品列表回传给客户端。

客户端源码参考：
- 发送端：`f.LF0`（商城窗口主控制器）、`f.ot0_0`（界面渲染与状态判定）、`f.F80`（C2S 0x70 封包）
- 接收端：`f.ho_1`（协议分发器，`case 112 -> new N70(...)`）、`f.N70`（S2C 0x70 响应包）、`f.HV`（商城商品领域条目）、`f.E10`（商城商品大类）

---

## 2. 打开商城请求 C2S 0x70

```text
flow: CLIENT_TO_SERVER
opcode: 0x70 (十进制 112)
payload length: 0
packet type: org.pokemmo.gameserver.protocol.packets.c2s.OpenGiftShopPacket
client class: f.F80
```

### 抓包示例

```text
70
```

### 交互流程

1. 客户端玩家点击界面上的“礼物商城”按钮。
2. 客户端构造 `f.LF0` 并调用 `update()` 触发 `f.ot0_0`。
3. `f.ot0_0.run()` 检测到 `LF0.Kl0 == null`（商品列表尚未加载），渲染提示文本“加载中...”并实例化 `f.F80` 发送 opcode `0x70`。
4. 服务端接收并在 `OpenGiftShopPacket.handle` 中调用 `GiftShopManager.getItems()` 获取配置商品列表。
5. 服务端通过当前 `Session` 编码发送 S2C `0x70`（`SendGiftShopPacket`）。

---

## 3. 商城列表响应 S2C 0x70

```text
flow: SERVER_TO_CLIENT
opcode: 0x70 (十进制 112)
packet type: org.pokemmo.gameserver.protocol.packets.s2c.SendGiftShopPacket
client class: f.N70
```

### 协议二进制结构

| 偏移 | 字段 | 类型 | 说明 |
| ---: | --- | --- | --- |
| 0x00 | `keyBytesLength` | unsigned byte | Web 充值密钥长度，无密钥填 `0` |
| 可变 | `keyBytes` | byte[] | Web 充值密钥内容（`keyBytesLength > 0` 时存在） |
| 可变 | `itemCount` | unsigned short LE | 商城商品条目总数 |
| 可变 | `items` | entry[] | 商品条目数组，长度为 `itemCount` |

每个商品条目（`items[i]`）依次包含以下字段：

| 字段 | 类型 | 字节序 | 默认值 | 说明 |
| --- | --- | --- | ---: | --- |
| `id` | int | LE | - | 商城条目唯一 ID (lpt3) |
| `targetType` | byte | - | `1` | 目标类型 (yd)：1=普通物品/时装，3=质子/特殊，5=染料 |
| `category` | byte | - | - | 分类 ID (0..8)，对应 `E10` |
| `itemIndexId` | short | LE | - | 物品索引 ID (wa)，对应 `Item.bin` |
| `price` | int | LE | - | 奖励点现价 (eo/yx0) |
| `originalPrice` | int | LE | - | 奖励点原价 (yg/pO)，`> price` 时客户端展示折扣标签 |
| `quantity` | int | LE | `1` | 堆叠数量 (zz/Lpt1)，`> 1` 时客户端在名称前添加 `xN` |
| `param1` | int | LE | `0` | 保留参数 (op/ME) |
| `releaseTime` | int | LE | `0` | 上架时间戳秒 (aq/wG)，7 天内显示 NEW 徽标 |
| `seasonal` | byte | - | `0` | 季节限定标志 (zi/yg0)，1 为季节限定 |
| `flag2` | byte | - | `0` | 保留标记 |
| `hasCondition` | byte | - | `0` | 是否限时 (0=否，1=是) |
| `[startMonth]` | byte | - | - | 限时起始月 (1-12)，仅 `hasCondition == 1` 时存在 |
| `[startDay]` | byte | - | - | 限时起始日 (1-31)，仅 `hasCondition == 1` 时存在 |
| `[endMonth]` | byte | - | - | 限时结束月 (1-12)，仅 `hasCondition == 1` 时存在 |
| `[endDay]` | byte | - | - | 限时结束日 (1-31)，仅 `hasCondition == 1` 时存在 |
| `featuredOrder` | int | LE | `-1` | 精选推荐位 (xk0)，`>= 0` 出现在精选标签页，`-1` 不推荐 |

### 客户端分类映射（`f.E10`）

| ID | 枚举名 | 本地化字符串 ID | 中文描述 |
| ---: | --- | ---: | --- |
| 0 | `SPECIAL` | 3050 | 特殊 |
| 1 | `DONATOR` | 3051 | 捐赠者状态 |
| 2 | `VANITY` | 3052 | 时装 |
| 3 | `CONSUMABLES` | 3053 | 消耗品 |
| 4 | `MISC` | 3054 | 杂货 |
| 5 | `FURNITURE` | 3055 | 装饰品 |
| 6 | `OCARINAS` | 3056 | 技能陶笛 |
| 7 | `PARTICLES` | 3057 | 质子 |
| 8 | `VOUCHERS` | 3058 | 点券 |

---

## 4. 配置文件规范

商品配置位于 `resource/gift/GiftShop.jsonc`。

```jsonc
{
  "items": [
    {
      // [230]甜甜香气陶笛
      "id": 1,
      "targetType": 1,
      "category": 6,
      "itemIndexId": 1179,
      "price": 500,
      "originalPrice": 500,
      "quantity": 1,
      "featuredOrder": 0
    }
  ]
}
```

若文件不存在或无条目，服务端向客户端发送 `itemCount = 0`，客户端将正常关闭“加载中...”并显示“沒有可用的物品.”。
