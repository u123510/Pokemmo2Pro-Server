# 宝可梦放生封包

PC 释放宝可梦使用 C2S `0x0C`，payload 固定为一个 little-endian `long` Object ID。
客户端 PC 的 `release-widget` 触发的 `sj0_1` 封包继承协议号 `12`，并写入选中宝可梦的
Object ID；例如 Object ID `535688253440` 的请求为：

```text
0c 00 10 82 b9 7c 00 00 00
```

其中首字节 `0c` 是 opcode，后 8 字节按 little-endian 解码为
`535688253440`（十六进制 `0x0000007CB9821000`）。

服务端只接受当前角色所属且仍位于 PC 容器的宝可梦，并拒绝交易中或已经报价的目标。
数据库事务按 `pokemon.id + trainer_id + container_id = PC` 锁定目标，将其移到 `deleted`
容器（`container_id = 8`，保留原 Object ID 作为审计记录），成功后发送已有 S2C `0x15`
`SendRemovePokemonPacket(PC, objectId)`，让客户端按 Object ID 移除 PC 条目。非法长度、非正
Object ID、未知目标、非 PC 容器或数据库失败均不会发送成功移除包；其中协议解码错误仍按
`Session` 的统一错误路径处理，业务拒绝和数据库失败会保留 Session。

当前字段含义和 little-endian 顺序已通过客户端源码 `f.sj0_1` 与该抓包交叉确认；服务端运行时
行为尚未按用户要求编译或启动验证。
