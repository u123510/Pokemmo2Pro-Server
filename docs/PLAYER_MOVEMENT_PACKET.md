# 玩家移动与可见性同步

## 协议

| opcode | 方向 | 作用 |
| ---: | --- | --- |
| `0x06` | c2s | 玩家移动请求：`short x`、`short y`、`byte direction/flags`。方向低两位为下/上/左/右，`0x80` 表示跑步，`0x40` 表示自行车跳跃；服务端只持久化低两位方向。 |
| `0x0D` | s2c | 向可见玩家发送移动动画，使用 `SendEntitySportPacket`；字段为 `long entityId`、`boolean isNdsType`、`byte actionCount` 和动作类型数组。 |
| `0x28` | s2c | 同步玩家交通方式（步行/自行车）；字段为 `long playerId` 和 `byte transportation`。 |
| `0x11` | s2c | 使用 `SendSetEntityPosPacket` 强制设置实体位置，仅用于非法移动回拉等位置纠正。 |
| `0x05` | s2c | 玩家进入可见范围时发送完整 `SendLoadPlayerPacket`。字段顺序为 `long entityId`、`byte sex`、皮肤、UTF-16LE 名称、`byte region`、`byte mapGroup`、`byte mapId`、`short x`、`short y`、`byte z`、`byte packedDirectionFlags`（服务端仅发送低两位方向）、`byte entityPd0Flags`、`byte nameplateState`、`byte optionalFlags` 及其可选字段；交通方式不属于此包，由 `0x28` 独立同步。 |
| `0x08` | s2c | 玩家离开可见范围或断线时发送 `SendRemoveEntityPacket`。 |

## 服务端流程

`MovePacket` 校验起点、交互状态和当前地区的跑步鞋状态后调用 `CharacterManager.computerNewPosition`。位置更新成功且仍在同一可见地图集合时，服务端只向同频道的其他会话发送一个 `0x0D` 移动动画；动作类型根据 `0x80` 跑步标志选择 `WALK_*` 或 `RUN_*`，封包中的 `isNdsType` 则根据地区选择客户端动作表，不能复用跑步标志。客户端按动作的方向、距离和时长推进实体位置。`0x11` 只用于非法移动回拉等明确的位置纠正，避免在动画刚开始时再次强制设置终点而造成卡顿。跨地图时发送 `0x08` 移除旧实体，并向新可见范围发送 `0x05` 完整玩家数据；连接地图切换时只补发尚未加载的连接地图，避免客户端地图实体集合缺失。

通用 `Session` 不再为每个封包固定增加 20ms 延迟；普通封包仍按连接顺序处理，
会执行脚本等待的对话封包改走独立的串行队列，因此脚本中的 `sleep` 不会堵住后续
`0x06` 移动请求。连续移动包仍在同一条普通队列中按到达顺序处理，避免位置倒退。

每个 `MapData` 独立维护玩家 Session 池。角色初次进图、脚本重载地图、传送、地图边界切换、重连和断线都会更新该池，避免所有地图共享会话或残留失效 Session。GBA 传送事件按 warp 的坐标和方向匹配，忽略移动包中的运行时 `z` 层；生成资源中的 warp 高度与玩家移动层不是同一字段。进入动画门时，服务端根据目标地图门 tile 自动把玩家落到门外可行走格并面朝下。传送事件会先确认目标地区和地图存在，再更新角色地图坐标并重载地图；断链目标会记录警告并拒绝传送，避免角色进入无效地图状态。

进入地图时，服务端先注册玩家所在地图的 Session；客户端通过 C2S `0x05`
确认地图加载并请求自身实体后，服务端才向该玩家发送当前可见的其他玩家，
同时向已在场的玩家发送新玩家的 `0x05` 完整实体。这样可以避免客户端在地图初始化阶段
清理过早到达的附近玩家封包。使用自行车道具时，`ItemUseService` 先在事务中更新持久化交通状态，`UseItemPacket` 再更新在线 `PlayerEntity.transportation` 并将 `0x28`
发送给本人及当前可见、同频道玩家；因此上车和下车都会立即刷新其他玩家视角。本人会额外
直接收到一次更新，避免地图会话池尚未完成注册时丢失自身状态。

## 验证边界

- `//hide` 开启后，完整实体、移动/方向/位置/交通等广播由 `PlayerVisibilityService` 按观察者过滤。
  进图两方向分别判断，普通玩家不能重新加载隐身 GM，GM 自己仍可见且仍能看到普通玩家。
  取消隐身恢复完整 `0x05` 和当前 `0x28`；切换与发送共享可见性监视器。
  同频道与地图范围规则不变，范围和未运行验证见 [`CHAT_HIDE_COMMAND.md`](CHAT_HIDE_COMMAND.md)。
- 已完成源码级检查：`GameProtocol` 已注册 `0x05`、`0x06`、`0x07`、`0x0D`、`0x08`、`0x11`，移动广播使用已有 S2C 封包。
- 已检查同频道过滤、发送者排除、地图池加入/移除、跨地图移除与加载路径。
- 未按仓库约定执行 Gradle 编译、启动服务器或客户端运行验证。
