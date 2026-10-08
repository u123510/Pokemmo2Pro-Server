# 宝可梦换位封包

## 1. 客户端请求

客户端通过 game c2s opcode `0x09` 请求改变宝可梦在 PARTY 或 PC 中的位置。客户端类 `f.Rm0` 的 JASM 显示请求结构为：

```text
byte changeCount
repeat changeCount:
    byte oldContainerType
    short oldPosition LE
    byte newContainerType
    short newPosition LE
```

客户端限制 `changeCount` 为 `1..127`，并拒绝负位置及源、目标完全相同的变更。持久化容器类型为：

| 值 | 容器 | 位置范围 |
| ---: | --- | --- |
| `0` | PC | `0..659 + pcBoxExpansionNumber * 60` |
| `1` | PARTY | `0..5` |

抓包示例：

```text
09 01 01 01 00 01 00 00
```

去掉 opcode `09` 后，载荷表示一条变更：从 PARTY 位置 `1` 移动到 PARTY 位置 `0`。客户端拖到已占用槽位时仍只发送这一条变更，因此服务端必须交换两只宝可梦，而不能覆盖目标槽位。

## 2. 服务端处理

`ChangePokemonPosPacket` 完整保存每条变更并校验数量、容器类型、槽位范围和相同源目标。`GameServerService.changePokemonPositions(...)` 在一个 jOOQ 事务中：

1. 使用 `trainer_id` 锁定并读取当前角色的 PARTY/PC 宝可梦。
2. 将本批请求视为源宝可梦到最终目标槽位的同时映射；空目标执行普通移动。目标槽位已有且未参与本批移动的宝可梦时，将其放回对应源槽位；参与本批移动的宝可梦继续使用自己的目标槽位。
3. 拒绝不存在的源槽位；角色原本存在 PARTY 宝可梦时，拒绝会把最后一只移出
   PARTY 的请求。角色 PARTY 原本为空时，PC 内部换位不受该保护影响。
4. 使用宝可梦 ID 与 `trainer_id` 双重条件写入最终 `container_id`、`container_position`。
5. 任一更新失败时回滚整个换位事务。

事务成功且请求涉及 PARTY 时，服务端从数据库重新读取 PARTY 并重建
`CharacterManager.partyPokemons`，防止在线数组保留旧顺序；纯 PC 换位不依赖 PARTY
重载。

如果数据库中找不到客户端上报的源槽位，说明客户端容器缓存与持久化位置已经不一致。
服务端不会根据一个缺少 Object ID 的过期槽位请求猜测宝可梦，而是重载在线 PARTY
并恢复客户端容器。请求涉及 PC 时，恢复顺序是 `0x27 00` 关闭旧窗口、补发本次涉及的
PARTY/PC `0x13` 完整快照、再用 `0x27 01` 重开窗口；只有 PARTY 受影响时只补发 PARTY
快照。客户端恢复到数据库真实槽位后，后续拖动会使用正确源位置。

## 3. 客户端刷新

客户端不使用独立的换位响应 opcode。接收类 `f.OM` 通过现有 s2c opcode `0x16` 的 flag `64` 读取位置：

```text
long pokemonObjectId LE
int flags LE             // 位置位 64；当前共享编码器同时带 OT 位 32768，因此通常为 32832 (0x8040)
byte containerType
short containerPosition LE
```

`UpdatePokemonDataCodec` 必须在设置 bit `64` 时实际写入 `containerId` 和
`containerPosition`。如果目标槽位已有宝可梦，服务端会对移动者和被交换者各发送一个
`0x16` 位置更新。

客户端 `f.OM` 会修改现有宝可梦对象的位置：同容器移动时令原控制器的 `rr0 = true`、
`jf = false`；跨容器移动时从旧控制器移除对象、写入新容器和位置、再加入新控制器。
`QT.HP()` 检测控制器的 `rr0` 后调用 `rt(true)` 刷新当前箱子，因此成功换位只需要对
所有实际移动对象发送 `0x16`。

客户端 `f.Gd0` 处理带 flags bit `1` 的完整 `0x13` 时会新建并替换容器控制器；而
`QT` 的 `NK.CW()` 在窗口构造时已经把旧控制器传给各槽位。窗口打开后补发 `0x13`
会让场景状态与窗口槽位引用分离，导致数据库已移动但 PC 仍显示旧精灵。完整快照只用于
开窗前初始化，或在先关闭 PC、装入快照、再重开窗口的失败恢复流程中使用。

项目的共享 `0x16` 编码器仍会附带 bit `32768`、四个可回忆技能槽和 OT，以避免该客户端在位置刷新后把原训练家显示清空。

## 4. 静态验证

- c2s `0x09` 已在 `GameProtocol` 注册为 `ChangePokemonPosPacket`。
- s2c `0x16` 已在 `GameProtocol` 注册为 `SendUpdatePokemonDataPacket`。
- 请求字段按 `byte + short LE` 顺序读取。
- bit `64` 的响应字段按 `byte + short LE` 顺序写入。
- 换位成功后只对实际移动的对象发送 `0x16`，不会替换已打开窗口使用的控制器。
- 数据库写入限定 `pokemon.id` 和 `pokemon.trainer_id`。
- 请求涉及 PARTY 时，在线 PARTY 数组在响应前重新加载。
- 换位拒绝且涉及 PC 时会按关闭、完整快照、重开的顺序恢复窗口。

按照仓库约定，本次修改未编译、未启动服务器；客户端实际换位行为仍需运行验证。
