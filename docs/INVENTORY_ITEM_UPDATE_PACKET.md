# 背包道具增量更新封包

背包完整快照使用 S2C `0x40`。战斗等需要保留当前背包容器对象的流程，应使用
S2C `0x41` 更新已有条目的数量，或使用 S2C `0x43` 删除数量已经归零的条目。

客户端参考实现：

```text
28887-obf-project/build/recompile/src/f/ho_1.java
28887-obf-project/src/main/java/f/TC.java
28887-obf-project/src/main/java/f/Kx0.java
```

`ho_1` 将 `0x41` 映射到 `TC`，将 `0x43` 映射到 `Kx0`。两者都会在更新后触发
客户端背包视图刷新，但不会像 `0x40` 的完整替换模式那样创建新的背包容器对象。

## S2C 0x41 数量更新

```text
flow: SERVER_TO_CLIENT
opcode: 0x41 (十进制 65)
payload length: 10
```

| 偏移 | 长度 | 字段 | 说明 |
| ---: | ---: | --- | --- |
| `0x00` | 8 | 道具 Object ID，LE | `owned_item.item_id` |
| `0x08` | 2 | 最新数量，LE | 必须大于 `0`；数量归零改用 `0x43` |

客户端会在所有已加载背包容器中查找该 Object ID，并把匹配条目的数量直接设置为新值。

## S2C 0x43 条目删除

```text
flow: SERVER_TO_CLIENT
opcode: 0x43 (十进制 67)
payload length: 9
```

| 偏移 | 长度 | 字段 | 说明 |
| ---: | ---: | --- | --- |
| `0x00` | 1 | 背包容器 ID | 主背包通常为数据库 `inventory.id` |
| `0x01` | 8 | 道具 Object ID，LE | 从指定容器删除该条目 |

野生捕获扣球后，剩余数量大于 `0` 时发送 `0x41`，等于 `0` 时发送 `0x43`。这样可以在
捕获失败后继续使用其它精灵球或同一堆叠中的剩余精灵球。完整流程见
[`BATTLE_CAPTURE_PACKET.md`](BATTLE_CAPTURE_PACKET.md)。
