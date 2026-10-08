# `setgift` 礼物勋章命令

## 输入格式

该命令通过 `ChatType.NORMAL` 聊天发送：

```text
//setgift <party slot 0-5>
```

例如：

```text
//setgift 0
```

队伍位置使用服务端内部的 `0..5` 下标。命令没有第二个参数，因此 `0` 是 PARTY 槽位，不是礼物模板 ID。

## 实际作用

`setgift` 为目标宝可梦添加 Gift Ribbon。服务端枚举映射为：

```text
PokemonNormalRibbonType.GIFT_RIBBON
normal_ribbon index = 2
protocol ribbon bit = 21
```

该操作是单向、幂等的：只把 `normal_ribbon[2]` 从 `false` 设为 `true`，重复执行不会移除或切换勋章。它不会创建礼物宝可梦，不会读取 `resource/gift/Gift.jsonc`，也不接受礼物模板编号。

## 服务端处理

命令按以下顺序执行：

1. 校验参数数量和 PARTY 槽位 `0..5`。
2. 查找目标宝可梦，并要求普通勋章数组恰好包含 16 项。
3. 如果 Gift Ribbon 已存在，返回幂等提示，不重复写库。
4. 克隆数组并只将索引 `2` 设为 `true`。
5. 使用 `pokemon.id + trainer_id` 条件更新 `pokemon.normal_ribbon`。
6. 数据库成功后更新在线 `PokemonData.normalRibbon`。
7. 发送 `0x16` ribbon 增量更新和完整 PARTY 容器刷新。

`UpdatePokemonDataCodec` 会把普通勋章索引 `2` 编码到 64 位 ribbon 字段的 bit 21。

## 客户端证据

客户端项目 `28887-obf-project` 中，`28887-renamed.jar` 的 `f.ng_2.O30(S)V` JASM 只构造 `//setgift <slot>`。同一菜单项显示为 `Set Gift`，并在 `f.CE.gS(21)` 为真时禁用。`f.CE.gS(I)Z` 检查的是 64 位 ribbon 字段 `CE.gU` 的目标 bit，因此客户端语义可以确定为添加 Gift Ribbon。

上述结论来自只读 JASM/字节码检查，没有导出或反编译到临时目录。Gift Ribbon 是否触发交易限制等额外规则尚未验证，本实现不声明或新增这类限制。
