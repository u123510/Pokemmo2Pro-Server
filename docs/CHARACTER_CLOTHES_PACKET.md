# 角色换装封包

本文记录客户端版本 `28887` 的单槽位换装协议。字段结论来自 Recaf 中的客户端类 `f.C6`、`f.nb0_1`、`f.wm0_0`、`f.X90`，服务端运行时结果仍需用实际客户端验证。

## 1. 客户端请求 `0x30`

方向：client to server。

| 顺序 | 类型 | 含义 |
| ---: | --- | --- |
| 1 | `byte` | `SkinType`，例如 `TOP = 6` |
| 2 | `boolean` | 是否直接携带 addon ID |
| 3a | `short LE` | 标志为 `true` 时的 addon ID |
| 3b | `long LE` | 标志为 `false` 时的 owned item Object ID |

抓包示例：

```text
30 06 00 00 10 82 09 1a 01 00 00
```

- `30`：opcode。
- `06`：上衣槽位 `TOP`。
- `00`：后续字段不是 addon ID，而是 owned item Object ID。
- `00 10 82 09 1a 01 00 00`：8 字节小端 owned item Object ID，不是角色 ID。

客户端在直接模式下会发送内置外观或表情使用的 addon ID，包括 `-1` 表示清除对应槽位。服务端接受可穿戴槽位 `0..9` 和自行车槽位 `11`；`FISHING_ROD = 10` 仍不走本换装写入流程。

客户端在 owned item 模式下会发送 `ownedItemId = 0` 清除当前槽位。此时服务端不查询
owned item，直接使用 `skin = -1`、`color = -1` 更新对应外观字段，并继续发送 `0x93`
同步；非零 owned item ID 仍必须通过角色归属、库存、数量和槽位映射校验。

自行车皮肤抓包示例：

```text
30 0b 00 00 10 42 06 34 01 00 00
```

- `0b`：自行车槽位 `BIKE = 11`。
- `00`：后续字段是 owned item Object ID。
- `00 10 42 06 34 01 00 00`：8 字节小端 owned item Object ID。

## 2. 服务端响应 `0x93`

方向：server to client。客户端类 `f.nb0_1` 读取该包并立即替换本地或其他玩家的一个外观槽位。

| 顺序 | 类型 | 含义 |
| ---: | --- | --- |
| 1 | `long LE` | 发生变化的角色 ID |
| 2 | `byte` | `SkinType` |
| 3 | `short LE` | 打包后的 addon ID 和颜色 |

打包规则：

```text
packed = (addonId & 0x3FF) | ((color & 0x3F) << 10)
```

- 低 10 位：addon ID，`0x3FF` 表示无 addon。
- 高 6 位：颜色，`0x3F` 表示无颜色。
- 有效 addon ID 范围为 `0..1022`，有效颜色范围为 `0..62`。

原有 `0x90` 是完整角色外观刷新；正常单槽位换装应回发 `0x93`，无需为每次换装重发整套角色数据。

## 3. 时装道具映射

owned item 记录保存的是 `itemIndexId`，响应需要的是 addon ID。客户端 `f.X90.nW()` 显示映射关系为：

```text
普通槽位: itemIndexId = 2000 + skinType * 256 + addonId
扩展帽子: itemIndexId = addonId + 4320    // addonId 256..495
```

自行车同样使用普通槽位公式。当前资源表定义的自行车索引范围为 `4816..4877`：

```text
itemIndexId = 4816 + bikeAddonId
```

服务端只接受该资源范围内的自行车索引，避免把后续普通道具索引误识别为自行车。

服务端反向计算 addon ID，并检查 owned item 同时满足：

- `item_id` 等于请求中的 Object ID；
- `owner_id` 等于当前角色 ID；
- `inventory_id` 等于主背包 ID；
- 数量大于零；
- item index 能映射到请求的服装槽位；
- 颜色和 addon ID 没有占用协议保留值。

上述 owned item 校验仅适用于非零 `ownedItemId`；`ownedItemId = 0` 是客户端清空槽位的保留值。

自行车没有独立颜色字段。服务端忽略 owned item 的颜色值，持久化时只更新 `character.bike`，并在 `0x93` 中把颜色编码为 `0x3F`（无颜色）。

当前物品资源中的 `ItemType` 元数据不完整，不能用 `ItemType.COSMETICS` 作为换装前置条件。例如 `itemIndexId=3620` 是“龙机器精灵服”，但运行时类型判断会将其误判为非时装。服务端改用客户端确认的 item index 区间和请求槽位做分类校验；无法映射到该槽位的普通道具仍会被拒绝。

## 4. 状态同步顺序

```text
解析并校验请求
  -> 按角色和 owned item 查询数据库
  -> 更新 character 对应外观字段（自行车为 `character.bike`，普通服装为 skin/color）
  -> 更新在线 CharacterData.SkinData
  -> 向请求者发送 0x93
  -> 向同地图、同频道玩家广播 0x93
```

数据库更新失败时不更新在线内存，也不发送成功刷新包。`SkinCodec` 使用与 `0x93` 相同的 10 位 addon ID、6 位颜色规则，保证重登后的完整外观与即时刷新一致。

## 5. 验证要点

1. 更换上衣后，服务端应发送一个 `0x93`，载荷长度为 11 字节，总包体含 opcode 为 12 字节。
2. 本人客户端应立即更新对应槽位，同地图且同频道的其他客户端也应看到变化。
3. 重登后外观应保持，证明数据库字段和完整 `0x90` 编码一致。
4. 使用 `ownedItemId = 0` 卸下当前槽位时，服务端应写入无外观保留值并发送 `0x93`。
5. 使用其他角色的 owned item Object ID，或使用无法映射到请求槽位的 item index 时，服务端应拒绝且不发送 `0x93`。
