# `setot` 原训练家名称命令

## 输入格式

该命令通过 `ChatType.NORMAL` 聊天发送：

```text
//setot <party slot 0-5> <OT name or localization token>
```

客户端调试菜单已确认会发送以下内容：

```text
//setot 0 {STRING_5685}
//setot 0 {STRING_5684}
//setot 0 unknown
```

客户端还可以把当前角色名作为第二个参数发送。队伍位置使用服务端内部的 `0..5` 下标。

## 本地化 token

`{STRING_5684}` 和 `{STRING_5685}` 是客户端本地化系统使用的文本 token。客户端菜单会用语言表显示对应标签，但构造命令时会故意发送带大括号的 token 原文。

服务端必须把 token 原样保存到 `pokemon.ot_name`，不能在服务端将其替换成中文或其他语言。最终显示文本由客户端按当前语言解析。普通字符串同样可以作为 OT 名称，长度限制为 `1..32`，与数据库 `VARCHAR(32)` 一致。

## 服务端处理

命令按以下顺序执行：

1. 校验参数数量和 PARTY 槽位 `0..5`。
2. 校验名称非空且不超过 32 个字符。
3. 从在线 PARTY 查找目标，必要时按 PARTY 容器回查数据库。
4. 使用 `pokemon.id + trainer_id` 条件更新 `pokemon.ot_name`。
5. 数据库成功后更新在线 `PokemonData.otName`。
6. 发送 `0x16` 增量更新；协议 bit `32768` 把 OT 与四个可回忆技能槽放在同一字段组中。共享编码器会在每次 `0x16` 更新时携带该字段组，避免客户端用默认空字符串覆盖 OT。
7. 发送完整 PARTY 容器刷新。

该命令只修改显示用的 `ot_name`，不会修改 `original_trainer_id`。

## 客户端证据

客户端项目 `28887-obf-project` 中，`28887-renamed.jar` 的 `f.ng_2` JASM 显示：

- `bT(S)V` 构造 `{STRING_5685}` 命令；
- `vu0(S)V` 构造 `{STRING_5684}` 命令；
- `J00(S)V` 构造 `unknown` 命令；
- `Ps(S)V` 构造当前角色名命令。

上述结论来自只读 JASM/字节码检查，没有导出或反编译到临时目录。
