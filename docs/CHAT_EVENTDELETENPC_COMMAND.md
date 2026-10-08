# `//eventdeletenpc` 删除自定义 NPC

## 1. 用法与删除范围

```text
//eventdeletenpc <NPC运行时Object ID>
```

用户抓包中的命令为：

```text
//eventdeletenpc 553459519488
```

`553459519488` 是当次运行中的 Object ID，需要按 `long` 读取，不能按 `int`。
它不是地图配置的 `entityIdx=100000`。服务器重启后 Object ID 会变，
因此部署本次修改后应重新从客户端选择目标，使用最新 ID，不能直接复用旧日志编号。
命令只接收一个 ID；无参数只显示用法，不猜测目标、不删除前方不明确的实体。

- 默认 GM 权限，只查找操作者当前主地图，不跨地图按 ID 搜索。
- 只能删除由 `CustomNpcCatalog` 本次启动加载或成功保存过的实体。
- 即使一个原生 NPC 的名字恰好是 `npc_100000`，或有人伪造同名对象，也不能通过检查。
- 未保存的历史临时 NPC、原生 NPC、未知/已删除 ID、未就绪/失效会话均拒绝。
- 战斗、玩家交易、场景交互或地图尚未加载完成时拒绝；先关闭当前交互再执行。
- 当前地图 NPC 跨频道共享，删除会同步给所有已加载该地图的会话，包括相邻地图观察者。
- 不新增客户端界面，不改原地图 JSON 或数据库，不实现 `eventspawnnpc` 的事件编辑。

## 2. 永久停用而非物理删文件

删除先原子更新对应独立 JSONC，将 `enabled` 设为 `false`，再移除在线实体。
保留完整出生配置和固定序号，重启后不再加载该 NPC；后续新建 NPC 不重用停用序号。
文件仍位于：

```text
resource/npc/custom/<地区>/<地图>/npc_<固定序号>.jsonc
```

停用前在保存锁内重新读取磁盘，要求与内存中原定义一致。
文件缺失、损坏、外部修改、锁冲突或写入失败时不移除在线实体；
如果前一次已经写入相同的停用定义但没有完成在线清理，重试可以继续清理。
原子写入通过同目录临时文件、`force(true)` 和 `ATOMIC_MOVE` 发布，
只有校验过的停用路径允许替换原文件；新增文件仍禁止覆盖。
字段值除 `enabled` 外保持不变，JSONC 会重新序列化，不保留手写注释或排版。
版本 2 事件 NPC 的 `eventId`、`sparkles`、`spriteScale` 也会保留，不降级为版本 1。

删除命令不会修改店铺文件的 `npcs`。已绑定商店的 NPC 被删后，需要手动移除相应
绑定，再执行 `//reloadshops`，否则下一次配置校验会提示该店员不存在。
如果要恢复，停服后将其文件设回 `enabled: true`，然后重启；恢复原序号，但分配新的 Object ID。

## 3. 在线与商店一致性

执行链路：

```text
ChatPacket (NORMAL 0x08)
  -> CommandDispatcher / CommandRegistry
  -> EventDeleteNpcCommand
  -> NpcDeleteService
  -> ShopCatalog.withNpcMutation 写锁
  -> 操作者 InteractManager 锁 -> 地图锁 -> CustomNpcCatalog / 文件保存锁
  -> JSONC enabled=false
  -> 关闭实体加载/交互标志 -> 地图复制快照移除
  -> 释放上述锁
  -> ShopSessions.closeForNpc
  -> S2C 0x08 移除实体 -> 中文结果反馈
```

商店原有开店/成交路径持有目录读锁；删除持有同一目录写锁。
已开始的成交完成后才删除，删除持锁时不会开始新成交。
停用成功后该 NPC 不再被当前地图查询到，其旧报价无法继续成交；
再按 NPC Object ID 关闭相关报价，不影响其他 NPC 的商店窗口。
跨角色报价清理在释放操作者锁后进行，避免持有一个角色锁时等待另一个角色锁。

`MapData.removeEntity` 要求对象身份一致，复制后发布新字典。
Object ID 查询改为单一字典快照遍历，避免并发删除时多次 getter 读到不同字典产生空引用。
`NpcEntity.isLoad/canInteract` 为可见字段，旧引用不能继续当作有效目标。

`NpcVisibilityService` 共用新增和地图 NPC 快照通知，在地图锁内发送。
若地图初始化先发旧 NPC，删除后发移除；若删除先完成，后续地图快照不会包含该 NPC。
`CharacterWorldLoader` 的 GBA/NDS 加载和 `CharacterMovementService` 的相邻地图加载
都走此路径，避免已删除 NPC 被迟到的地图快照重新显示。

## 4. 协议记录

输入为现有 C2S `0x08` 聊天包，共 62 字节：
`NORMAL=0`，UTF-16LE 文本 `//eventdeletenpc 553459519488`，两字节零终止。
不新加 C2S opcode，只注册命令。

客户端移除使用现有 S2C `0x08 SendRemoveEntityPacket`，
payload 仅 8 字节 NPC Object ID，little-endian，无字符串或附加字段。
虽然服务端成员历史命名为 `characterId`，客户端 `f.mv0` 读取通用实体 ID。
只读 Recaf JASM：`f.mv0.Oj0()` 调用 `pE()` 读取 ID；
`os0()` 调用 `sr0().cJ0.xc(kg)` 移除实体。未修改客户端或该封包编码器。
方向不同，C2S 聊天 `0x08` 与 S2C 实体移除 `0x08` 不冲突。

## 5. 文件与验证

新增 `command.commands.EventDeleteNpcCommand`、`game.entity.NpcDeleteService`、
`game.entity.NpcVisibilityService`，Java package 均以 `org.pokemmo.gameserver` 为前缀。
注册位于 `GameCommandModule`。

修改 `CustomNpcDefinition`、`CustomNpcCatalog`、`CustomNpcStore` 提供已知自定义实体的停用保存；
`MapData` / `NpcEntity` 提供安全移除与旧引用失效；`ShopCatalog` / `ShopSessions`
负责交易互斥和报价关闭；三个现有 NPC 发送入口转交 `NpcVisibilityService`。
源码索引、包职责、模块、架构和配置说明同步更新。

回归用例：

- `EventDeleteNpcCommandTest`：权限、精确命令名、参数和大于 int 的 Object ID。
- `NpcDeleteServiceTest`：先保存后移除、失败不改变实体、过期/重复 ID、旧对象引用及移除包格式。
- `CustomNpcDeletionTest`：重启不恢复、固定序号保留、保护原生/未保存/伪造实体、外部文件修改、
  仅磁盘已提交时的幂等重试。
- `ShopNpcMutationTest`：删除等待现有事务读锁，以及异常释放写锁。

2026-09-09：只做源码/协议/注册与只读脚本检查；按约定未编译、未执行 JUnit、
未启动服务器或客户端、未删除实际运行中的 NPC、未改写已有 NPC 配置或数据库。
实际删除、跨频道可见性、地图加载竞态、并发交易和重启恢复仍需要用户运行验证。

静态检查：

```powershell
./tools/check_npc_delete_contract.ps1
./tools/check_spawnnpc_contract.ps1
./tools/check_custom_npc_contract.ps1
./tools/check_shop_contract.ps1
```
