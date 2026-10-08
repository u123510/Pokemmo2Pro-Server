# 宝可梦自动排序封包

## 1. 客户端请求

客户端 PC 界面的自动排序按钮使用 game c2s opcode `0x1B`。客户端 `f.Sx0`
按小端序写入：

```text
byte containerType
byte containerAmount
repeat containerAmount:
    int containerIndex LE
short sortFlags LE
```

PC 请求中的 `containerIndex` 是箱子编号，不是数据库 `container.id`。日志示例：

```text
1B 00 01 00 00 00 00 01 00
```

解析为：

| 字段 | 值 | 说明 |
| --- | ---: | --- |
| `containerType` | `0` | `PC` |
| `containerAmount` | `1` | 只排序一个箱子 |
| `containerIndex` | `0` | 第一个箱子 |
| `sortFlags` | `0x0001` | 客户端已确认的等级排序位 |

当前抓包只确认 `0x0001` 表示等级排序；升降序及其它排序位尚未从客户端数据中确认。
服务端对未知排序位拒绝业务操作并保留 Session，不把未知字段猜测成其它领域规则。

## 2. 服务端处理

`SortPokemonPacket` 注册在 `org.pokemmo.gameserver.protocol.packets.c2s`，负责：

1. 校验容器数量、可读长度和尾部数据；
2. 只接受 `PC` 或 `PARTY` 容器，并拒绝重复/越界箱号；
3. 拒绝交易期间或包含交易报价宝可梦的排序；
4. 调用 `GameServerService.sortPokemon(...)`，由
   `PokemonContainerService` 在事务中按 `trainer_id` 锁定记录；
5. 对每个目标箱子提取已有宝可梦，按等级升序稳定排序，并从该箱第一个槽位开始重新分配；
6. 只写入实际变化的 `container_position`，然后发送现有 S2C `0x16` 位置增量。

PARTY 排序成功后还会更新在线 `CharacterManager.partyPokemons`。PC 排序不替换已经打开的
PC 容器控制器，客户端通过 `0x16` 的位置刷新位自行重排当前箱子。

## 3. 静态验证

- C2S `0x1B` 已注册为 `SortPokemonPacket`；S2C `0x1B` 仍保持
  `SendRemoveAroundEntityPacket`，两个方向互不冲突。
- 日志中的 8 字节 payload 按小端序消费为 `00 01 00 00 00 00 01 00`。
- 数据库更新同时限定宝可梦 Object ID、`trainer_id` 和容器 ID。
- 非法长度、未知容器、重复箱号、越界箱号、交易状态和未知排序位均有拒绝路径。
- 本次未编译、未启动服务器；客户端其它排序位和排序方向仍标记为未验证。
