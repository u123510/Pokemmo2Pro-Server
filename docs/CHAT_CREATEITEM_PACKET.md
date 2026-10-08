# `//createitem` 聊天命令说明

本文档记录 `NORMAL` 聊天中的 `//createitem` 命令，以及服务端如何把道具写入目标角色的普通背包。

## 1. 命令格式

```text
//createitem <目标角色名> <道具编号> <数量>
```

示例：

```text
//createitem LM 6220 1
```

参数含义：

| 参数 | 示例 | 说明 |
| --- | --- | --- |
| 目标角色名 | `LM` | 接收道具的角色名 |
| 道具编号 | `6220` | `Item.bin` 中的道具 ID，不是 `owned_item.item_id` |
| 数量 | `1` | 增加的数量，服务端限制为 `1..32767` |

命令默认需要 `GM` 及以上权限。

## 2. 聊天包

命令使用现有聊天包，聊天类型为 `ChatType.NORMAL`：

```text
ChatType.NORMAL
//createitem LM 6220 1
```

聊天内容使用 UTF-16LE，并以 `00 00` 结束。`ChatPacket` 将 `//` 消息交给通用 `CommandDispatcher`，不会把命令广播到普通聊天频道。

## 3. 服务端处理流程

```text
收到 NORMAL ChatPacket
  |
  |-- 解析目标角色名、道具编号和数量
  |-- 使用 ItemManager 校验道具编号
  |-- 查找目标角色的普通背包 inventory(id=1)
  |-- 查找同编号且无特殊限制的已有道具
  |       |-- 找到：增加 item_amount
  |       `-- 未找到：生成新的 item_id 并插入 owned_item
  |-- 目标在线：发送完整 SendInventoryPacket
  `-- 回复执行结果
```

具体实现位置：

```text
server.game/src/main/java/org/pokemmo/gameserver/command/commands/CreateItemCommand.java
server.game/src/main/java/org/pokemmo/gameserver/services/GameServerService.java
```

## 4. 数据库字段

普通背包对应 `inventory.id = 1`，道具记录写入 `owned_item`：

| 字段 | 写入规则 |
| --- | --- |
| `item_id` | 使用 `SnowflakeIdGenerator` 生成的新实例 ID；合并已有堆叠时保留原 ID |
| `owner_id` | 目标角色 ID |
| `item_index_id` | 命令中的道具编号，例如 `6220` |
| `item_amount` | 原数量加命令数量，不能超过 `SMALLINT` 最大值 `32767` |
| `inventory_id` | 普通背包 ID `1` |
| `color_id` | `-1` |
| `item_region_index_id` | `-1` |
| `pvp_reward_level` | `-1` |
| `pvp_reward_season` | `-1` |

只有颜色、地区和 PVP 字段均为默认无限制值的同编号道具才会合并，避免破坏特殊道具记录。

## 5. 在线刷新

项目当前没有单独的新增道具包。目标在线时，服务端重新查询普通背包中的全部 `OwnedItemRecord`，并发送：

```java
new SendInventoryPacket(inventory, items)
```

目标离线时只写入数据库，角色下次进入游戏时由既有 `handleLoadGameWorldContext()` 流程发送普通背包。

## 6. 校验清单

- [ ] `//createitem LM 6220 1` 给角色 `LM` 增加 6220 号道具 1 个。
- [ ] 不存在的角色名不会修改数据库。
- [ ] 不存在的道具编号不会修改数据库。
- [ ] 数量必须为 `1..32767` 的整数。
- [ ] 已有普通堆叠时合并数量，不重复创建记录。
- [ ] 数量溢出 `32767` 时拒绝写入。
- [ ] 目标在线时收到完整背包刷新。
- [ ] 目标离线时登录后能看到新增道具。
- [ ] 命令不会进入普通聊天广播。

