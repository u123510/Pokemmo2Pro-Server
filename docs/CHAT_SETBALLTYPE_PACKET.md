# `setballtype` 精灵球类型命令

## 输入格式

该命令通过 `ChatType.NORMAL` 聊天发送：

```text
//setballtype <character name> <party slot 0-5> <ball type 0-24>
```

例如：

```text
//setballtype LM 1 0
```

`LM` 是目标角色名，`1` 是 PARTY 数组槽位，最后的 `0` 是精灵球类型 ID，不是宝可梦图鉴 ID。

## 客户端语义

客户端 `f.ng_2.le(S, I)` 固定按以下顺序构造命令：

```text
当前角色名 + PARTY 槽位 + 球类型 ID
```

客户端调试菜单循环创建 `0..24` 共 25 个球类型选项；`0x16` 接收类 `f.OM` 对 bit `8192` 的值也校验为 `0..24`，越界时退回默认类型 `3`。服务端因此直接拒绝越界值。

## 状态同步

服务端按角色名查找目标，并按以下顺序处理：

```text
校验角色、PARTY 槽位和球类型
  -> 使用 pokemon.id + trainer_id 更新 pokemon.ball_type
  -> 目标在线时更新 PokemonData.ballType
  -> 发送 0x16 bit 8192 球类型增量
  -> 发送完整 PARTY 容器刷新
```

目标离线时只写入数据库，下次登录时从 `pokemon.ball_type` 加载。

## 客户端证据

结论来自客户端项目 `28887-obf-project` 和 Recaf 中 `28887-renamed.jar` 的只读 JASM：

- `f.ng_2.le(S, I)` 构造命令参数；
- `f.ng_2.nd0(...)` 创建 `0..24` 的球类型菜单；
- `f.OM` 读取 bit `8192` 后校验相同范围。

没有导出或反编译到临时目录。
