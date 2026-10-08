# `setribbons` 宝可梦勋章命令

## 输入格式

该命令通过 `ChatType.NORMAL` 聊天发送：

```text
//setribbons <party slot 0-5> <decimal ribbon mask>
```

例如：

```text
//setribbons 1 65536
```

最后一个参数是客户端 64 位 ribbon 字段的完整十进制位掩码，不是从 `0` 开始连续编号的单个勋章 ID。`65536 = 1L << 16`，对应 Champion Ribbon。

命令会整体替换当前 ribbon 值，因此上面的示例会清除目标宝可梦的其他华丽大赛勋章和普通勋章，只保留 Champion Ribbon。清空全部勋章使用：

```text
//setribbons 1 0
```

## 位布局

客户端将华丽大赛勋章和普通勋章放在同一个 `long` 中：

- 五个华丽大赛分组各使用 3 bit，起始位置为 `group * 3 + 1`，等级范围为 `0..4`。
- 普通勋章使用独立 bit，当前服务端映射为 bit `16`、`20..34`。
- Gift Ribbon 是 bit `21`；独立的 `setgift` 命令只添加该 bit。
- bit `0` 和 `17..19` 当前未建模，输入包含这些未知位时服务端拒绝执行。

数据库分别保存 `contest_ribbon SMALLINT[6]` 与 `normal_ribbon BOOLEAN[16]`。协议只使用前五个华丽大赛分组，第六个数据库槽位保持为 `0`。

## 客户端语义

客户端 `f.ng_2` 的调试菜单不是把单个 ID 直接发送给服务端：

- `m5(S)` 发送 mask `0`，表示清空。
- `lPt8(VU, int[])` 在当前 `CE.gU` 上 OR 一个普通勋章 bit，再返回完整 `long`。
- `qd(VU, int[])` 修改一个华丽大赛分组的 3 bit 等级，再返回完整 `long`。
- `Hx0`、`Com9` 最终把 `LongSupplier.getAsLong()` 的完整值发送为 `//setribbons` 第二个参数。

因此服务端必须按完整 mask 解码并整体替换两组数据库数组。

## 状态同步

处理顺序如下：

```text
校验 PARTY 槽位、支持位和华丽大赛等级
  -> 原子更新 pokemon.contest_ribbon 与 pokemon.normal_ribbon
  -> 更新在线 PokemonData 两个勋章数组
  -> 发送 0x16 bit 16384 ribbon long 增量
  -> 发送完整 PARTY 容器刷新
```

`PokemonRibbonMask` 统一负责 mask 编解码。完整宝可梦和增量编码均使用 `1L` 位移，确保 bit `32..34` 不会按 Java `int` 位移规则绕回低位。

## 客户端证据

上述结论来自客户端项目 `28887-obf-project` 和 Recaf 中 `28887-renamed.jar` 的只读 JASM，没有导出或反编译到临时目录。
