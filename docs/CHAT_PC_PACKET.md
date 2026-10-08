# `//pc` PC 界面命令

## 输入

```text
聊天类型：NORMAL
聊天内容：//pc
```

命令无参数，默认需要 `GM` 权限。

## 客户端协议证据

客户端项目 `C:\Users\z3407\Desktop\28887-obf-project` 的 `f.H50` 将 GM 菜单的
`PC` 项注册为 `//pc`，因此命令由游戏服处理。客户端收包分发器 `f.ho_1` 将服务端
opcode `0x27` 映射到 `f.ff_1`；`ff_1` 读取一个字节并调用 `BU.Nc0(boolean)`：

```text
0x27 + 0x01 -> BU.Nc0(true)  -> 打开 QT PC 界面
0x27 + 0x00 -> BU.Nc0(false) -> 关闭 QT PC 界面
```

`0x13` 只负责更新宝可梦容器，`0x6D` 只负责更新箱子索引/名称；它们不是开窗命令。
`QT` 创建箱子槽位时，`NK.CW()` 会取得当时的 PC 容器控制器并传给每个槽位。
因此完整 `0x13` 必须在打开窗口前发送；窗口已经打开后再用 `0x13` 替换控制器，
现有槽位仍会引用旧控制器并显示过期位置。
客户端 `f.y1_0` 使用 C2S opcode `0x1C` 提交箱子名称或顺序：

| 字段 | 类型 | 条件 | 说明 |
| --- | --- | --- | --- |
| flags | byte | 总是存在 | bit `1` 更新名称，bit `2` 更新顺序 |
| boxIndex | byte | bit `1` | 箱子编号 |
| boxName | UTF-16LE NUL | bit `1` | 新名称，最多 20 个字符 |
| boxAmount | byte | bit `2` | 顺序数组长度 |
| boxOrder | byte[] | bit `2` | 箱子编号排列 |

点击二号箱子时抓到的封包：

```text
1C 02 0D 00 01 02 03 04 05 06 07 08 09 0A 0B 0C
```

其中 `02` 表示只提交顺序，`0D` 表示后面有 13 个箱子编号。角色基础拥有
11 个箱子，当前 `pcBoxExpansionNumber = 2`，因此总数为 13。

## 服务端处理

`PcCommand` 位于 `org.pokemmo.gameserver.command.commands`，执行时：

1. 校验无参数且角色已加载；
2. 发送 `SendPcStatePacket(false)`（`0x27 00`），关闭可能仍持有旧控制器的 PC 窗口；
3. 重新读取当前角色的 PC 宝可梦并发送 `SendPokemonContainerPacket`（`0x13`）；
4. 发送 `SendBoxInfoPacket`（`0x6D`），箱子数量按
   `11 + pcBoxExpansionNumber` 计算；
5. 发送 `SendPcStatePacket(true)`（`0x27 01`），让新窗口绑定刚装入的容器控制器；
6. 只有容器读取成功并提交刷新封包后，才反馈 `PC opened.`；否则返回失败消息。

`SendPcStatePacket` 仅编码一个布尔字节，与客户端 `f.ff_1` 的读取契约一致。服务端
同时注册了客户端方向的旧 `0x27 EmptyInteractPacket` 和服务端方向的新 `0x27`
`SendPcStatePacket`，两个方向的 opcode 映射互不冲突。

`UpdatePcBoxInfoPacket` 注册为 C2S `0x1C`，负责完整读取并校验名称和顺序。顺序必须
包含当前角色全部可用箱子，且每个编号只出现一次。当前数据库 schema 没有箱子名称或
排序字段，因此这类客户端 UI 偏好本轮不持久化，也不需要返回确认包。

PC 界面的宝可梦自动排序另使用 C2S `0x1B`，不应与箱子顺序更新的 `0x1C` 混淆。其字段、
等级排序实现和未验证的其它排序位见 [`POKEMON_SORT_PACKET.md`](POKEMON_SORT_PACKET.md)。

## 验证

本次只完成源码和协议静态核对，未编译或启动服务。运行时应先确认客户端收到
`0x27 00`、`0x13`、`0x6D`、`0x27 01` 并看到 PC 界面打开。角色有两个扩容箱子时，
`0x6D` payload 应为 `01 0D 00 01 ... 0C`。点击任意箱子后即使客户端发送 `0x1C`，
Session 也不应断开。
