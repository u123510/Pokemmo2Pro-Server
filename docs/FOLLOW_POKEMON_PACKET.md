# 精灵跟随封包

本文记录客户端版本 `28887` 的精灵跟随协议。字段和行为来自 Recaf 工作区 `28887-renamed.jar` 的 JASM：请求类 `f._for`、响应类 `f.T0`、队伍槽位读取 `f.Mj.Ry0`、请求构造点 `f.wg0_0.Zs/Pg/Z0`，以及跟随外观构造 `f.wg0_0.wl`、`f.Ai0.Me`。

## 1. 客户端请求 `0x11`

方向：client to server。

| 顺序 | 类型 | 含义 |
| ---: | --- | --- |
| 1 | `byte` | 选择方式 |
| 2 | `long LE` | 与选择方式对应的值 |

选择方式：

| 值 | 含义 | long 字段 |
| ---: | --- | --- |
| `0` | 按队伍槽位跟随 | 0 基队伍位置 `0..5` |
| `1` | 按宝可梦 ID 跟随 | 宝可梦 Object ID |
| `2` | 取消跟随 | 固定为 `0` |

抓包示例：

```text
11 01 00 10 c2 8e 4a 00 00 00
```

- `11`：opcode。
- `01`：按宝可梦 Object ID 选择。
- 后续 8 字节：小端宝可梦 Object ID。

服务端只接受当前角色队伍中的宝可梦。客户端不会为蛋提供跟随操作，服务端也使用与客户端 `CE.vn()` 一致的 egg flag 校验拒绝蛋。

## 2. 服务端响应 `0x2B`

方向：server to client。客户端 `f.T0` 读取该包后，先按第一字段查找本人或地图中的玩家实体，再更新该实体的跟随精灵。

| 顺序 | 类型 | 含义 |
| ---: | --- | --- |
| 1 | `long LE` | 玩家角色实体 ID，不是宝可梦 ID |
| 2 | `short LE` | 跟随宝可梦的图鉴/物种 index ID；`0` 表示取消 |
| 3 | `byte` | 形态和稀有度位 |
| 4 | `boolean` | 是否忽略跟随显示错误 |

rarity byte：

```text
rarity = (formType & 0x1F)
       | (female ? 0x20 : 0)
       | (shiny ? 0x40 : 0)
       | (alpha ? 0x80 : 0)
```

玩家主动设置跟随时最后一个布尔值为 `false`。取消跟随时 index ID 和 rarity 都为 `0`。

## 3. 状态同步

```text
解析并校验 0x11
  -> 从当前在线队伍按槽位或 Object ID 找到宝可梦
  -> 拒绝不存在、非队伍、蛋或非法形态数据
  -> 更新 character.follower_pokemon_index_id / follower_pokemon_rarity
  -> 更新在线 PlayerEntity
  -> 向本人发送 0x2B
  -> 向可见地图内同频道玩家广播 0x2B
```

数据库更新失败时不修改在线内存，也不发送成功刷新包。重登时 `CharacterData` 从这两个角色字段恢复跟随状态，`SendLoadPlayerPacket` 会将其写入完整角色数据。

`//hide` 仅对普通观察者发送物种/稀有度均为 0 的跟随清除（`ignoreFollowError=true`），
随后移除该玩家实体；不修改真实跟随字段或数据库。隐身期间的 `0x2B` 通过
`PlayerVisibilityService.broadcast` 过滤，自己和 GM 及以上仍收到更新。
取消隐身时完整角色包恢复当前跟随外观，详见 [`CHAT_HIDE_COMMAND.md`](CHAT_HIDE_COMMAND.md)。

## 4. 验证要点

1. 选择队伍宝可梦后，`0x2B` 第一字段必须是角色 ID；旧实现误写宝可梦 ID 会导致客户端找不到玩家实体。
2. 本人应立即看到跟随精灵，同一可见地图且同频道的其他玩家也应看到。
3. 取消跟随后，服务端发送 index ID 和 rarity 均为 `0` 的 `0x2B`。
4. 重登后跟随状态应保持。
5. 非法队伍位置、非当前队伍 Object ID、蛋、未知 type 或 `type=2` 携带非零值时，不发送 `0x2B`。
