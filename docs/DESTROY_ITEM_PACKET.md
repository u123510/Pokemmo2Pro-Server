# 道具销毁封包说明

本文档记录客户端销毁普通背包道具时发送的 `CLIENT_TO_SERVER` 封包。
S2C `0x37` 才是野生捕获专用动画；`0x39` 的 S2C 方向是普通战斗道具动作，捕获流程不使用它。

## 1. 封包编号

```text
opcode: 0x39 (十进制 57)
```

服务端注册位置：

```text
server.game/src/main/java/org/pokemmo/gameserver/protocol/GameProtocol.java
```

如果没有注册 `0x39`，服务端会报错：

```text
无法找到封包来自 CLIENT_TO_SERVER 封包id 57
```

## 2. 数据结构

正文长度为 10 字节，全部使用小端序：

| 偏移 | 长度 | 字段 | 示例 |
| ---: | ---: | --- | --- |
| `0x00` | 8 | `item_id`，道具实例 ID | `00 10 c2 84 25 00 00 00` |
| `0x08` | 2 | 销毁数量 | `64 00`，即 `100` |

示例完整封包：

```text
39 00 10 c2 84 25 00 00 00 64 00
```

解析结果：

```text
opcode       = 0x39
item_id      = 161141100544
destroyAmount = 100
```

对应实现：

```text
server.game/src/main/java/org/pokemmo/gameserver/protocol/packets/c2s/DestroyItemPacket.java
```

## 3. 服务端处理流程

```text
收到 0x39
  |
  |-- 读取当前角色 ID、item_id 和销毁数量
  |-- 查询当前角色普通背包中的对应道具实例
  |-- 检查数量是否足够
  |-- 检查 Item.bin 中的道具是否允许销毁
  |       |-- 数量剩余大于 0：更新 item_amount
  |       `-- 数量归零：删除 owned_item 记录
  `-- 成功后发送完整 SendInventoryPacket 刷新客户端
```

实现位置：

```text
server.game/src/main/java/org/pokemmo/gameserver/services/GameServerService.java
```

请求中的 `item_id` 必须属于当前登录角色，不能借此删除其他角色的道具。数量为 `0`、负数、超过当前堆叠数量、道具不存在或道具不可销毁时，服务端不修改数据库。
