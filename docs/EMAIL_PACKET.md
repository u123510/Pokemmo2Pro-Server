# 邮箱封包

邮箱列表由客户端在打开收件箱和发件箱时分别请求。服务端使用
`org.pokemmo.gameserver.services.mail.MailService` 持久化邮件和附件，发送成功后将
道具/精灵移入收件人的 `mail` 容器，并在同一事务中扣除发送者金钱。

## 初始计数 `0x98`（S2C）

世界加载时发送三个 little-endian `short`：

1. 收件箱条目数；
2. 未读条目数；
3. 发件箱条目数。

三个值分别来自 `mail_message` 的收件、未读和发件记录数；计数超过协议 `short` 上限时截断为
`32767`。

邮箱计数在角色进入世界时就会下发，但这只用于 HUD 显示未读数量，不代表邮箱窗口已经打开。
发送邮件的 `0x95` 请求还必须满足当前 Session 已通过 PC 交互菜单进入邮箱（包括新增的医院电脑）；未打开邮箱、
已关闭邮箱、移动/换地图或重连后的旧请求都会返回失败结果，不会写入数据库。

## 请求列表 `0x99`（C2S）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `currentPageIndex` | `short LE` | 页码，必须非负 |
| `isSendMailType` | `byte` | `0` 收件箱，`1` 发件箱 |

服务端对页码和类型做边界检查，按创建时间倒序返回每页 10 条记录。普通玩家邮件使用
`tagType = 0`：收件箱编码发送者名称；发件箱只编码收件人名称。

## 列表响应 `0x97`（S2C）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `pageIndex` | `short LE` | 回显请求页码 |
| `sendMailType` | `byte` | 回显邮箱类型 |
| `entryCount` | `short LE` | 当前页条目数量，最多 `10` |

每个条目按客户端 `f.yq0_0.m5` 的读取顺序编码：

```text
long mailId
long recipientCharacterId
long senderCharacterId
byte tagType (普通玩家邮件为 `0`；非零值为不读取 senderName 的标签/系统分支)
UTF-16LE senderName (仅 `sendMailType = 0` 且 `tagType = 0` 时存在)
UTF-16LE recipientName (仅 `sendMailType = 1` 时存在)
int createdAtEpochSeconds
UTF-16LE title
byte unreadFlag
byte reserved
```

普通玩家邮件的 `tagType` 为 `0`。客户端仅在收件箱方向且 `tagType = 0` 时读取发送者名称；
发件箱只读取收件人名称并显示该字段。给收件箱使用非零 `tagType` 后仍写发送者名称，或给发件箱
额外写发送者名称，都会导致时间、标题和后续字段整体错位。客户端参考类 `f.oi0_1`
先读取页码、类型和条目数；当 `entryCount` 为零时不会读取条目内容。该字段顺序来自客户端
字节码/JASM 追踪，服务端编译和运行时验证尚未执行。

传输层必须在 opcode 后保留一个压缩标志字节；当前未压缩模式写入 `0`，由客户端网络解码器消费后
再交给具体封包解析。不能省略该字节，否则 `0x97`/`0x99` 的首个业务字段会整体错位，邮件列表或
正文会停留在“加载中”。

## 读取详情 `0x96`（C2S）

客户端点击收件箱或发件箱条目时发送一个 `long LE mailId`。服务端只允许邮件的发送人或收件人
读取详情；收件人打开未读邮件时会在同一事务中标记 `is_read`。随后回送 S2C `0x99` 详情包和
最新的 S2C `0x98` 邮箱计数。未知邮件、无权限访问或非法 ID 会发送单字节失败包，再刷新计数。

## 详情响应 `0x99`（S2C）

成功时先按客户端 `f.g_0` 的外层响应编码，再按
`f.yq0_0.m5(direction, true)` 编码详情主体：

```text
byte success (1 成功，0 失败；失败时包到此结束)
byte direction (0 收件箱，1 发件箱；仅成功时存在)
long mailId
long recipientCharacterId
long senderCharacterId
byte tagType (普通玩家邮件为 `0`；非零值为不读取 senderName 的标签/系统分支)
UTF-16LE senderName (仅 `direction = 0` 且 `tagType = 0` 时存在)
UTF-16LE recipientName (仅 `direction = 1` 时存在)
int createdAtEpochSeconds
UTF-16LE title
UTF-16LE body
byte unreadFlag
byte reserved
byte attachmentCount (收件箱和发件箱详情都会发送；发件箱固定为 `0`)
```

客户端 `f.g_0` 将 `direction = 0` 传给 `f.yq0_0.m5` 时表示收件箱，并继续读取附件数量。只有
收件箱的 `tagType = 0` 会读取 `senderName`，发件箱只读取 `recipientName`；`unreadFlag` 只是
已读状态，不能用它代替附件数量。每个附件的公共字段为：

```text
byte slot
byte claimed
byte type (0 道具，1 精灵，2 金钱)
long value (Object ID 或金额)
byte reserved
short field1
short field2
byte field3
```

类型 `0`（普通道具）后追加 `byte exists = 1` 和客户端 `BM()` 道具结构：`byte flags`、
`long itemObjectId`、`short itemIndexId`、`short amount`、`byte A5`；类型 `1` 后追加
`byte exists = 1`、完整 `PokemonCodec` 以及 6 个 little-endian `short` 保留字段；类型 `2`
（金钱）没有额外字段。`claimed`
为数据库附件表的领取状态；领取后附件记录保留，详情仍会发送原附件并将该字节写为 `1`。发件箱
详情不会追加附件条目，客户端对应解析路径会在 `attachmentCount = 0` 后结束。邮件不存在或无权限时服务端只发送
`success = 0`，不追加详情字段；客户端会进入“指定邮箱不存在”分支，不会把后续计数字段误解析为详情。
如果附件表存在但底层 `owned_item` 或 `pokemon` 已缺失，服务端仍保留该附件槽位并发送
`exists = 0`，以保证后续附件不会发生字节错位。

服务端会在读取收件箱详情前检查三张附件表是否都有 `claimed` 列。尚未执行迁移的旧数据库仍可
打开邮件，附件按“未领取”读取；领取和删除请求会被拒绝并记录迁移提示。要启用领取流程，需先
执行 [`db/create_mail_tables.sql`](../db/create_mail_tables.sql)（或重新初始化
[`db/schemas/init.sql`](../db/schemas/init.sql)）。

## 发送结果 `0x96`（S2C）

发送 `0x95` 后客户端会禁用撰写框，直到收到一个字节的结果码：

| 值 | 说明 |
| --- | --- |
| `0` | 发送成功；客户端清空撰写框并恢复发送按钮 |
| `1` | 通用拒绝；客户端恢复发送按钮并保留错误提示 |

服务端在邮件事务提交后发送 `0`；角色未加载、附件处于交易中或事务校验失败时发送 `1`。

## 删除邮件 `0x97`（C2S）

客户端删除邮件时发送：

```text
long mailId
short pageIndex
```

服务端只允许邮件发送人或收件人删除；仍有未领取附件时拒绝删除，并按请求页刷新列表。

## 领取邮件附件 `0x98`（C2S）

客户端点击收件箱条目的领取按钮时发送：

```text
long mailId
byte slot
byte hasOptionalData (当前邮箱 UI 为 `0`)
short pageIndex
byte claimMode (客户端操作模式：`0` 默认领取，`1/2` 为精灵领取位置操作)
```

`slot` 是详情附件数组中的全局槽位；附件类型由详情中的槽位记录决定，不能由客户端最后一个字节
伪造。`claimMode` 是客户端的领取/位置操作，服务端只允许收件人领取单个附件；重复领取、邮件
不是收件人、附件资产缺失、PARTY/PC 无空槽或收件人金钱溢出都会回滚本次事务。道具从 `mail`
inventory 转入主背包，普通可堆叠道具在容量允许时合并；为保持
详情外键，合并后的原附件 Object ID 记录移入 `void` inventory，删除邮件时清理该归档记录。精灵
优先进入 PARTY 空槽，满后进入 PC 空槽；金钱原子增加到 `character.money`。成功后只将选中的附件
`claimed` 置为 `TRUE`、标记邮件已读，并刷新 `0x0C` 金钱、`0x40` 背包、`0x13` PARTY/PC、`0x98`
邮箱计数、当前收件箱 `0x97` 列表和 `0x99` 详情；成功时另外发送 S2C `0x9A` 的邮件 ID 与槽位回执，
失败也会刷新当前列表/详情以解除客户端按钮等待状态。

## 领取确认 `0x9A`（S2C）

领取事务成功后服务端发送：

```text
long mailId
byte slot
```

客户端 `f.TH0` 使用邮件 ID 和附件槽位立即禁用对应领取按钮；随后服务端仍会刷新详情，
以同步数据库中的 `claimed` 状态。该方向的 `0x9A` 与 GTL 创建挂单的 C2S `0x9A` 共用 opcode，
但方向不同。

## 删除邮件（服务端能力）

现有服务层的删除入口按当前角色的发件人或收件人身份校验邮件；仍有未领取附件时会被保护。
附件全部领取后才允许删除，删除时保留已领取到主背包或 PARTY/PC 的资产，并清理合并时留在
`void` inventory 的原道具记录。

## 发送邮件 `0x95`（C2S）

客户端发送邮件时，payload 按以下顺序编码：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `recipientName` | UTF-16LE，以 `00 00` 结束 | 收件人名称；示例中为 `LM` |
| `title` | UTF-16LE，以 `00 00` 结束 | 标题；示例中为 `A` |
| `body` | UTF-16LE，以 `00 00` 结束 | 正文；示例中为 `A` |
| `attachmentCount` | `byte` | 附件数量 |
| `type` | `byte` | `0` 道具，`1` 精灵，`2` 金钱 |

附件字段：

- `type = 0`：`long LE itemObjectId` + `short LE amount`。`itemObjectId` 是
  `owned_item.item_id`，不是道具类型编号；服务端再通过该 Object ID 查询
  `owned_item.item_index_id`。本次抓包的 `489987117056` 对应道具 Object ID，
  `amount = 1`，而道具类型 `1476` 不在 `0x95` payload 中。
- `type = 1`：`long LE pokemonObjectId`，对应 `pokemon.id`。本次抓包为
  `757671792640`。
- `type = 2`：`int LE moneyAmount`。本次抓包为 `1000`。

金额附件必须为正数；收件人、标题和正文不得包含 UTF-16 `NUL`，避免终止符截断后续字段。

服务端将客户端发来的道具 Object ID 作为 `owned_item.item_id` 校验，并从
`owned_item.item_index_id` 获取道具类型。发送事务会：

1. 按名称找到唯一收件人，并按角色 ID 顺序锁定双方角色，避免并发互发死锁；
2. 锁定发送者主背包道具及 PARTY/PC 精灵，校验归属、数量、账号绑定状态和 PARTY 不被清空；
3. 写入 `mail_message` 与对应附件表；
4. 将完整道具或精灵转移到收件人的 `mail` 容器，部分道具堆叠生成新的 Object ID；
5. 原子扣除 `character.money`，金额写入 `mail_money_attachment`。

事务任一步失败都会回滚，发送者在线状态随后刷新金钱、主背包、PARTY/PC 和邮箱计数；若收件人在线，
服务端也会推送新的邮箱计数。

## 其他说明

- `0x17` 关闭邮箱窗口请求会清除当前 Session 的邮箱打开状态；
- 邮件附件领取已实现为 C2S `0x98`；删除邮件使用 C2S `0x97`（`long mailId + short pageIndex`）；
- 邮件 schema 同时位于 `db/schemas/init.sql` 和可单独执行的
  `db/create_mail_tables.sql`；本次未执行数据库迁移、编译或运行时验证。
