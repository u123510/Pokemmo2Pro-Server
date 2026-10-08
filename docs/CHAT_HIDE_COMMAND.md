# `//hide` 管理员地图隐身

## 1. 用法

```text
//hide
```

无参数，每次切换状态。开启后普通玩家不再看到执行者的地图角色、名称牌与跟随精灵；
执行者自己和 **GM/SGM/HGM/ADM（权限值 >= 7）** 仍能看到。
再次输入恢复附近玩家视角中的完整角色、跟随与当前交通方式。
STAFF、CM、MOD 不属于本命令的隐身观察权限，不能按 `permission > 3` 判断。

默认 GM 命令权限，服务层也检查当前会话和权限。
角色/地图未就绪、地图加载尚未完成或失败、正在战斗/交易/场景交互时拒绝切换。
开启时取消现有待处理的单挑/交易请求，不强行结算或修改进行中的交易资产。

**范围是地图实体隐身，并暂停该角色的野外遭遇，不是假离线或匿名模式。** 普通聊天、
私聊、好友在线状态、邮件、排行榜等不在本次隐藏范围内；不改变角色权限、碰撞规则或数据库。

## 2. 状态与生命周期

- `PlayerVisibilityState` 属于 `CharacterManagerState`，仅保存当前隐身角色 ID。
- 换图、转向和移动不会清除该状态；复用同一个角色上下文的连接交换保留隐身。
- 新建上下文的完整重新登录或游戏服重启后默认可见，不写数据库或文件。
- 状态绑定角色 ID，不能把同一上下文中其他角色误判为隐身。
- 原生 NPC 和独立自定义 NPC 不受影响。

开启隐身只改变其他观察者的显示，不移除操作者自己的实体。
取消隐身恢复的是服务端当前完整角色数据，而不是上次隐藏前缓存的位置或外观。

## 3. 发送路径

```text
ChatPacket NORMAL -> CommandDispatcher -> HideCommand
  -> PlayerVisibilityService.toggle
  -> 在线角色隐身状态切换
  -> 普通观察者：清除跟随 + 移除玩家
  -> GM 及以上：保留/刷新完整角色
```

`PlayerVisibilityService.canSee(subject, viewer)` 是有方向的判断。
隐身 GM 仍能看到普通玩家，但普通玩家不能看到隐身 GM；进图同步的两个方向必须分别判断。
同频道与现有可见地图范围规则仍保留。切换时还枚举已加载此地图的反向连接地图观察者，
避免单向地图连接造成已经显示的玩家残留。

所有向其他玩家发送的完整实体均走 `sendPlayer`，其余角色地图更新走 `sendIfVisible`：

- 初次进图、地图确认、传送、连接地图加载、跨图广播。
- 移动动画、方向、坐标纠正广播和交通方式。
- 跟随精灵切换、换装和地图 PvP 状态。
- 断线和离开视野使用统一移除方法，永不移除本地玩家实体。

可见性检查与发包同持隐身角色的 `PlayerVisibilityState` 监视器，
切换状态也持同一监视器。若旧加载包先发送，隐身移除包随后发送；
隐身切换完成后，新的普通玩家加载/增量请求被过滤，不会把角色重新显示出来。
只锁消息所属角色，不同时获取两个玩家的可见性锁。

附近单挑和玩家交易在注册/接受时检查双方可见性，观战按目标对观战者的可见性检查。
普通玩家不能使用旧名字/Object ID 对隐身角色继续发起这些地图交互。
管理员之间仍可按正常规则交互；地图隐身不提供战斗内匿名或旁观者身份隐藏。

## 4. 协议与客户端证据

用户抓包为 C2S `0x08`，共 16 字节：NORMAL 类型 `00`、UTF-16LE `//hide`、双零终止。
本次只注册命令，不新增 opcode，不修改客户端 UI 或二进制字段。

| 操作 | 既有 S2C 响应 |
| --- | --- |
| 隐藏跟随 | `0x2B`：执行者 ID、物种 0、稀有度 0、ignoreFollowError=true |
| 移除角色 | `0x08`：执行者 Object ID，8 字节 LE |
| 恢复角色 | `0x05`：完整玩家实体，包含现有跟随字段 |
| 恢复交通 | `0x28`：执行者 ID 与当前交通方式 |
| 命令反馈 | `0x09`：中文系统通知 |

只读 Recaf JASM（`28887-renamed.jar`）：
`f.mv0` 将实体 ID 交给 `yt_1.xc`；`yt_1.xc` 移除其他实体及名称 UI，
如果错误传入本人 ID 会清空本地玩家控制器，因此服务端必须排除自己。
`f.T0.os0` 按玩家 ID 查找实体再更新跟随精灵，先发清除跟随可避免残留。
客户端已有 `//hide` 菜单入口，服务器隐身实现不依赖修改客户端标志位。

## 5. 文件与验证

新增：

- `command.commands.HideCommand`：无参数开关和中文反馈。
- `game.character.PlayerVisibilityState`：在线角色绑定状态与发送互斥监视器。
- `game.character.PlayerVisibilityService`：权限规则、定向发送、切换同步和旁观者查询。

修改 `GameCommandModule` 注册，`CharacterManagerState` 持有独立状态；
`MapVisibilityService`、`CharacterMovementService`、`ChangeFllowPokemonPacket`、
`ResetCharacterClothesPacket`、`BattleManager` 接入过滤；
`BattleRequestManager`、`TradeRequestRegistry`、`BattleSpectatingService` 接入目标可见性；
`TradeManager` 增加仅转发的待处理请求清理入口。
Java package 均以 `org.pokemmo.gameserver` 为前缀，数据库、客户端源码和通用 Session 未修改。

新增 `PlayerVisibilityTest` / `HideCommandTest`：权限矩阵、开关/恢复、清除跟随、自己保护、
双向进图、相邻地图、增量过滤、上下文重连、忙碌拒绝和并发发包顺序。
使用测试会话收集封包，不依赖数据库、真实服务器或客户端。

2026-09-09：仅完成源码/协议静态核对，按项目约定未编译、未运行 JUnit、
未启动服务器或客户端，未改变任何在线角色状态。实机验证需至少两名普通/GM 观察者，
覆盖开关、换图、相邻地图、重连、跟随、换装和移动。

```powershell
./tools/check_hide_contract.ps1
```
