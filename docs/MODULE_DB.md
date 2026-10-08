# `db` 数据库模块

## 1. 职责

`db` 模块提供 PostgreSQL JDBC 连接池、jOOQ 生成代码和初始化 schema。业务模块通过 `org.pokemmo.db.Database#ctx()` 获取 jOOQ `DSLContext`；每次查询从 HikariCP 连接池借用连接并在执行后归还，不再长期复用单条 JDBC 连接。

三个服务入口继续使用现有远程 PostgreSQL 配置。
连接池会校验空闲连接、定期保活并淘汰超过生命周期的连接，因此 PostgreSQL 重启或网络连接被回收后可自动建立新连接。

## 2. Schema 主要对象

| 表 | 作用 |
| --- | --- |
| `account` | 登录账号、密码摘要和登录权限 |
| `game_node` | 游戏/聊天节点定义 |
| `online_game_node_server` | 在线游戏节点地址 |
| `online_chat_node_server` | 在线聊天节点地址 |
| `server_token` | 节点认证 token |
| `account_context` | 账号当前角色、节点和登出上下文 |
| `character` | 角色位置、资产、外观、权限和事件状态 |
| `container` | PARTY、PC、交易、托儿所等宝可梦容器 |
| `pokemon` | 宝可梦持久化属性和容器位置 |
| `gtl_listing` | GTL 挂单、价格、数量、状态、原容器位置和有效期；状态 `0/1/2/3` 分别为活跃、已售、取消、已领取 |
| `gtl_trade_history` | GTL 每次完成购买的买卖双方、商品快照、数量、金额和成交时间；供客户端近期交易记录使用 |
| `inventory` | 物品容器类型 |
| `owned_item` | 玩家拥有的道具和数量 |
| `black_list` / `friend_list` | 玩家社交关系 |
| `pokemon_dex` | 图鉴解锁位图 |
| `death_save` | 死亡存档标记 |

已存在的数据库缺少 GTL 表时，可单独执行
[`db/create_gtl_listing.sql`](../db/create_gtl_listing.sql)。该脚本创建
`public.gtl_listing`、`public.gtl_trade_history` 及其索引，可重复执行，不会修改现有角色、
宝可梦或容器数据。

`friend_list` 使用 `(player_id, friend_id)` 复合主键，两个字段都引用 `character(id)`；
好友关系是单向记录，删除角色时由外键级联清理。旧数据库从单列 `player_id` 主键升级时，
可执行 [`db/migrate_friend_list.sql`](../db/migrate_friend_list.sql)，再重新生成 jOOQ 模型。

## 3. 生成代码

`src/main/java/org/pokemmo/db/jooq` 下的 tables、records、routines、Keys 和 Tables 是生成文件。数据库 schema 变化后的推荐流程：

1. 修改 `db/schemas/init.sql` 或迁移脚本。
2. 启动与目标 schema 一致的 PostgreSQL。
3. 执行 jOOQ codegen。
4. 检查业务服务的字段类型和数组转换。
5. 更新数据库文档。

不要直接编辑 `db/src/main/java/org/pokemmo/db/jooq` 中的生成类。

## 4. 数组和特殊字段

角色事件使用 PostgreSQL 数组；宝可梦 moves、EV、IV、ribbon 和 particle effects 也使用数组。业务层通过 `ArrayUtil` 在 Java primitive array 与 jOOQ record array 之间转换。修改数组字段时必须检查长度、索引顺序和空值默认值。

## 5. 重要约束

- `pokemon.id` 是宝可梦对象 ID，`container_position` 只是队伍/PC 位置。
- 活跃 GTL 宝可梦挂单会把 `pokemon.container_id` 改为 `6`（`auction`），并在
  `gtl_listing` 保留原容器与槽位。
- `gtl_listing` 当前由动态 jOOQ 字段访问；schema 更新后仍应重新生成 jOOQ 模型。
- `gtl_trade_history` 一次购买一条记录，包含 `buyer_id`，因此部分物品挂单的多次购买可
  分别显示在买家的近期交易记录中；它同样由动态 jOOQ 字段访问。
- 成交款领取将已售挂单的 `status` 从 `1` 改为 `3`，保留 `sold_amount` 作为成交历史并防止重复入账；该状态值不需要新增数据库列。
- 宝可梦修改通常必须同时限制 `trainer_id`。
- `owned_item.item_id` 是拥有记录 ID，不等于 `item_index_id`。
- `account_context` 用于跨登录节点和游戏节点维护在线状态。
- 好友在线状态不写入 `friend_list`，由游戏服 `GameSessionPool` 在编码 S2C `0x63` 时实时判断。
