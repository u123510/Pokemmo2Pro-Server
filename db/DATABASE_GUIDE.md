# OpenMMO PostgreSQL 数据库说明

本文档说明 `db/schemas/init.sql` 中定义的数据库结构、各张表在当前服务端中的用途，以及常用维护 SQL。

## 1. 数据库连接

当前登录、游戏和聊天服务默认连接同一个 PostgreSQL 数据库：

| 配置 | 当前值 |
| --- | --- |
| 地址 | `127.0.0.1` |
| 端口 | `5432` |
| 数据库 | `mmo_db` |
| 用户 | `openmmo` |
| Schema | `public` |

连接信息目前直接写在各服务的 `Main.java` 中。生产环境不应继续使用源码中的固定密码，建议改成环境变量或外部配置文件。

确认当前连接目标：

```sql
SELECT current_database(), current_user, current_schema();
```

初始化数据库：

```powershell
psql -h 127.0.0.1 -p 5432 -U openmmo -d mmo_db -f "C:\Users\z3407\Desktop\OpenMMO-main\db\schemas\init.sql"
```

也可以在 pgAdmin 的 `mmo_db` 查询工具中打开并执行 `db/schemas/init.sql`。

## 2. 总体关系

```text
account
  |-- server_token                  登录服向游戏服/聊天服签发的一次性令牌
  |-- account_context               当前角色、节点及断线重连状态
  `-- character                     逻辑关联：一个账号可拥有多个角色
        |-- pokemon                 逻辑关联：角色拥有的宝可梦
        |-- owned_item              逻辑关联：角色拥有的道具
        |-- mail_message            邮件主表
        |-- mail_*_attachment       邮件道具、精灵和金钱附件
        |-- pokemon_dex             图鉴数据
        |-- black_list              黑名单数据
        |-- friend_list             好友数据
        `-- death_save              死亡存档占位表

game_node
  |-- online_game_node_server       可实际连接的游戏服务器进程
  |-- online_chat_node_server       可实际连接的聊天服务器进程
  `-- account_context               账号最后所在节点

container                           宝可梦容器类型字典
inventory                           道具容器类型字典
```

数据库共定义 20 张表。

| 表 | 主要用途 | 当前代码使用情况 |
| --- | --- | --- |
| `account` | 账号、密码哈希和登录权限 | 登录服、聊天服、公共服务使用 |
| `game_node` | 游戏大区/逻辑节点配置 | 登录服、游戏服使用 |
| `online_game_node_server` | 在线游戏服地址 | 登录服、游戏服使用 |
| `online_chat_node_server` | 在线聊天服地址 | 游戏服使用 |
| `server_token` | 服务之间的一次性会话令牌 | 登录服、游戏服和公共服务使用 |
| `account_context` | 账号当前角色及重连上下文 | 登录服、游戏服和公共服务使用 |
| `container` | 宝可梦容器类型 | 游戏服使用 |
| `character` | 玩家角色及游戏进度 | 游戏服、聊天服使用 |
| `pokemon` | 玩家宝可梦数据 | 游戏服使用 |
| `inventory` | 道具容器类型 | 游戏服使用 |
| `owned_item` | 玩家持有的道具 | 游戏服使用 |
| `mail_message` | 邮件标题、正文、收发件人和已读状态 | 游戏服使用 |
| `mail_item_attachment` | 邮件道具附件与收件人 mail inventory 中的 Object ID | 游戏服使用 |
| `mail_pokemon_attachment` | 邮件精灵附件与收件人 mail 容器中的 Object ID | 游戏服使用 |
| `mail_money_attachment` | 邮件金钱附件 | 游戏服使用 |
| `black_list` | 玩家黑名单 | 当前未发现业务代码读取 |
| `friend_list` | 玩家好友关系 | 游戏服 `FriendService` 读写并用于 S2C `0x63` 列表 |
| `pokemon_dex` | 玩家图鉴位图 | 游戏服使用 |
| `death_save` | 玩家死亡存档 | 当前仅为空结构，未发现业务代码读取 |

## 3. 账号与登录

### 3.1 `account`

账号主表。

| 字段 | 类型 | 作用 |
| --- | --- | --- |
| `account_id` | `SERIAL` | 账号主键 |
| `account_name` | `VARCHAR(12)` | 登录名，唯一，最多 12 个字符 |
| `password` | `BYTEA` | 密码的 SHA-1 原始摘要字节 |
| `created_at` | `TIMESTAMP` | 创建时间 |
| `login_permission` | `SMALLINT` | 登录权限等级，默认 `3` |
| `ban_reason` | `VARCHAR(50)` | 封禁原因；当前登录代码尚未据此拒绝登录 |

服务端收到客户端提交的十六进制 SHA-1 字符串后，将其转换为字节并与 `password` 比较。因此数据库中不能直接保存明文密码。

初始化脚本只在账号表完全为空时创建默认账号 `admin/admin`。如果表中已经有其他账号但没有 `admin`，脚本不会补建 `admin`。

新增账号：

```sql
INSERT INTO public."account" (account_name, password, login_permission)
VALUES ('player1', digest('Pass123', 'sha1'), 3);
```

修改密码：

```sql
UPDATE public."account"
SET password = digest('NewPass123', 'sha1')
WHERE account_name = 'player1';
```

列出账号：

```sql
SELECT account_id, account_name, login_permission, ban_reason, created_at
FROM public."account"
ORDER BY account_id;
```

> 安全提示：SHA-1 不适合现代密码存储，也没有为每个账号设置盐值。当前格式是为了兼容现有客户端和服务端协议，不应当视为安全的生产级密码方案。

### 3.2 `server_token`

存放登录服签发给其他服务的一次性会话令牌。它和客户端的“记住密码/保存凭证”不是同一种令牌。

| 字段 | 类型 | 作用 |
| --- | --- | --- |
| `id` | `SERIAL` | 记录主键 |
| `node_type` | `VARCHAR(4)` | 目标服务类型，例如 `game` |
| `node_id` | `INT` | 目标节点编号 |
| `account_id` | `INT` | 所属账号 |
| `account_ip` | `INET` | 签发时的客户端 IP |
| `token_type` | `VARCHAR(32)` | 令牌用途，例如普通加入或重连 |
| `token` | `UUID` | 实际会话令牌，由 PostgreSQL 自动生成 |
| `created_at` | `TIMESTAMP` | 签发时间 |

当前代码验证令牌时同时检查账号、服务类型、令牌类型和 IP。令牌有效期为 5 分钟，验证成功后立即删除，因此正常情况下只能使用一次。

查看某账号的服务令牌：

```sql
SELECT st.*
FROM public.server_token st
JOIN public."account" a ON a.account_id = st.account_id
WHERE a.account_name = 'player1'
ORDER BY st.created_at DESC;
```

### 3.3 `account_context`

保存账号从登录服进入游戏服时的上下文，也用于断线重连。

| 字段 | 类型 | 作用 |
| --- | --- | --- |
| `account_id` | `INT` | 主键，同时关联账号 |
| `character_id` | `BIGINT` | 当前或最后使用的角色 |
| `game_node_id` | `SMALLINT` | 所在逻辑游戏节点 |
| `node_name` | `VARCHAR(32)` | 节点名称 |
| `server_id` | `SMALLINT` | 所在的实际游戏服务器 |
| `is_log_out` | `BOOLEAN` | 当前实现中为 `TRUE` 时会走重连流程 |
| `update_at` | `TIMESTAMP` | 上下文更新时间 |

查看账号上下文：

```sql
SELECT ac.*
FROM public.account_context ac
JOIN public."account" a ON a.account_id = ac.account_id
WHERE a.account_name = 'player1';
```

清理异常或过期的重连上下文：

```sql
DELETE FROM public.account_context
WHERE account_id = (
    SELECT account_id
    FROM public."account"
    WHERE account_name = 'player1'
);
```

执行删除前应先查询并确认目标账号。删除上下文不会删除账号和角色，但会取消当前保存的重连状态。

## 4. 节点与服务器地址

### 4.1 `game_node`

表示客户端看到的逻辑游戏节点或大区，并不等同于一个具体 Java 进程。

| 字段 | 类型 | 作用 |
| --- | --- | --- |
| `node_id` | `SERIAL` | 节点编号 |
| `node_type` | `VARCHAR(4)` | 节点类型，默认 `game` |
| `node_name` | `VARCHAR(32)` | 节点名称，唯一 |
| `port` | `INT` | 节点端口字段；当前代码主要使用在线服务器表中的端口 |
| `permission_id` | `SMALLINT` | 加入节点所需的最低账号权限 |
| `is_join_able` | `BOOLEAN` | 节点是否允许加入 |
| `created_at` | `TIMESTAMP` | 创建时间 |

登录服的权限判断是：

```text
account.login_permission >= game_node.permission_id
```

初始化数据：

| 节点 | 端口 | 权限 | 可加入 |
| --- | ---: | ---: | --- |
| `Schemas` | `7777` | `2` | 是 |
| `Pts` | `7777` | `2` | 否 |

### 4.2 `online_game_node_server`

记录当前可以连接的实际游戏服务器地址。一个逻辑节点理论上可以对应多个游戏服务器实例。

| 字段 | 类型 | 作用 |
| --- | --- | --- |
| `server_id` | `SERIAL` | 实际服务器编号 |
| `node_id` | `INT` | 所属逻辑节点 |
| `ipv4` | `INET` | IPv4 地址 |
| `ipv6` | `INET` | IPv6 地址 |
| `port` | `INT` | 游戏服端口 |

初始化脚本默认写入 `127.0.0.1:7777`。远程客户端不能连接服务器返回的 `127.0.0.1`，公网或局域网部署时必须改成客户端能够访问的地址。

### 4.3 `online_chat_node_server`

结构与游戏服务器地址表相同，但用于聊天服务器。初始化脚本默认写入 `127.0.0.1:7778`。

检查全部节点和地址：

```sql
SELECT gn.node_id,
       gn.node_name,
       gn.permission_id,
       gn.is_join_able,
       gs.server_id AS game_server_id,
       gs.ipv4 AS game_ipv4,
       gs.port AS game_port,
       cs.server_id AS chat_server_id,
       cs.ipv4 AS chat_ipv4,
       cs.port AS chat_port
FROM public.game_node gn
LEFT JOIN public.online_game_node_server gs ON gs.node_id = gn.node_id
LEFT JOIN public.online_chat_node_server cs ON cs.node_id = gn.node_id
ORDER BY gn.node_id, gs.server_id, cs.server_id;
```

## 5. 角色数据

### 5.1 `character`

角色主表。一个账号可以通过 `account_id` 拥有多个角色，但当前 SQL 没有为 `character.account_id` 声明数据库外键，该关系主要由业务代码维护。

字段较多，可以按用途分组理解：

| 分组 | 字段 | 作用 |
| --- | --- | --- |
| 身份 | `id`, `account_id`, `name`, `union_name`, `sex` | 角色编号、账号、名称、公会名和性别 |
| 登录统计 | `login_time_stamp`, `last_login_mac`, `created_at`, `online_minutes` | 登录与在线统计 |
| 货币 | `money`, `coins`, `battle_points` | 金币、点券和对战点数 |
| 狩猎区 | `safari_steps`, `safari_ball_amount` | 狩猎区剩余步数和球数 |
| 设置与扩容 | `other_settings_value`, `pc_box_expansion_number`, `battle_box_expansion_number`, `template_amount` | 客户端设置和容量扩展 |
| 驱虫与诱饵 | `repel_steps`, `repel_item_id`, `lure_type`, `lure_item_id`, `lure_steps` | 地图效果状态 |
| 坐标 | `region_id`, `map_header_id_or_gba_map_group_id`, `gba_map_id`, `x`, `y`, `z` | 所在地区、地图和坐标 |
| 朝向与移动 | `toward`, `transportation` | 角色朝向和交通方式 |
| 权限与显示 | `nameplate_status`, `permission` | 名牌状态和角色权限 |
| 模型 | `model_region_index_id`, `model_index_id` | 角色模型信息 |
| 跟随宝可梦 | `follower_pokemon_index_id`, `follower_pokemon_rarity` | 跟随宝可梦外观 |
| 外观 | `forehead` 到 `leggings_color` | 发型、帽子、服装等部件及颜色 |
| 装备外观 | `fishing_rod`, `bike` | 鱼竿和自行车状态 |
| 地区进度 | `champion_flag`, `running_shoe_flag`, `badge_flag`, `story_line_flag`, `city_can_fly_flag` | 五个地区的主要进度 |
| 副本 | `instance_times`, `instance_next_time` | 五个地区的副本次数和下次开放时间 |
| 初始伙伴 | `first_partner_status` | 五个地区的初始宝可梦选择状态 |
| 关都剧情 | `oak_lab_status`, `oak_parcel_status` | 大木研究所和包裹剧情状态 |

五地区数组的当前索引顺序为：

```text
0 关都 KANTO
1 丰缘 HOENN
2 合众 UNOVA
3 神奥 SINNOH
4 城都 JOHTO
```

初始化脚本会给 `admin` 创建一个默认角色：

```text
角色 ID：1
角色名：Kyu
公会名：ADM
初始金钱：1500000
初始地图：region_id=0, map_header/group=4, gba_map_id=1, x=6, y=6
初始在线时长：0 分钟；在线期间由游戏服务器每分钟累计保存
```

如果数据库中仍有旧初始化脚本写入的 `120`，它代表 `120` 分钟（客户端显示为 2 小时），可按角色实际情况
手动修正，例如：

```sql
UPDATE public.character
SET online_minutes = 0
WHERE id = 1;
```

查看账号的角色：

```sql
SELECT c.id, c.name, c.region_id, c.x, c.y, c.z, c.money, c.online_minutes
FROM public.character c
JOIN public."account" a ON a.account_id = c.account_id
WHERE a.account_name = 'player1'
ORDER BY c.id;
```

### 5.2 `death_save`

设计用于保存角色死亡时的恢复信息，但当前只包含 `player_id`，没有实际存档字段，业务代码也尚未使用。现阶段可以视为预留表。

## 6. 宝可梦数据

### 6.1 `container`

宝可梦所在位置的类型字典。

| ID | 名称 | 容量 | 必需 | 用途 |
| ---: | --- | ---: | --- | --- |
| 0 | `pc` | 660 | 是 | PC 盒子 |
| 1 | `party` | 6 | 是 | 当前队伍 |
| 2 | `trade` | 6 | 否 | 交易暂存 |
| 3 | `daycare` | 26 | 否 | 培育屋 |
| 4 | `rental_party` | 6 | 否 | 租借队伍 |
| 5 | `mail` | 0 | 否 | 邮件关联 |
| 6 | `auction` | 0 | 否 | 拍卖关联 |
| 7 | `void` | 0 | 否 | 空/未归类 |
| 8 | `deleted` | 0 | 否 | 已删除 |
| 9 | `event` | 6 | 否 | 活动队伍 |

### 6.2 `pokemon`

存储玩家拥有的每一只宝可梦。

| 分组 | 字段 | 作用 |
| --- | --- | --- |
| 所属关系 | `id`, `trainer_id`, `original_trainer_id`, `ot_name` | 宝可梦编号、当前训练家、原训练家和 OT 名称 |
| 容器位置 | `container_id`, `container_position`, `widget_type` | 所在容器及槽位 |
| 基础身份 | `dex_id`, `personality_value`, `name`, `form_type` | 图鉴编号、性格随机值、昵称和形态 |
| 战斗状态 | `status`, `level_value`, `current_hp`, `exp`, `item` | 状态、等级、生命、经验和携带物；`item = -1` 表示未携带道具，读取时兼容旧值 `0` |
| 招式 | `moves`, `moves_pp`, `can_remember_moves`, `pp_up_times` | 当前招式、PP、可回忆招式和 PP 提升次数 |
| 个体培养 | `iv_values`, `ev_values`, `ability`, `friend_value` | IV、EV、特性和亲密度 |
| 捕获信息 | `catch_address`, `catch_level`, `catch_region`, `ball_type`, `catch_time` | 捕获地点、等级、地区、球和时间 |
| 稀有状态 | `has_hidden_ability`, `is_shiny`, `is_alpha`, `is_secret` | 隐藏特性、闪光、头目和特殊状态 |
| 华丽与奖章 | `contests_category_values`, `contest_ribbon`, `normal_ribbon`, `mark_status` | 华丽大赛与奖章数据 |
| 其他 | `egg_value`, `hidden_power_type`, `current_select_particle_effect_type`, `particle_effects` | 蛋、觉醒力量和粒子效果 |

`moves`、`moves_pp`、`can_remember_moves` 固定为 4 个元素；`iv_values` 和 `ev_values` 固定为 6 个元素。

初始化脚本会给角色 `Kyu` 的队伍位置 0 创建一只默认宝可梦。

历史 schema 曾将 `pokemon.item` 默认设为 `0`。当前规范值为 `-1`，服务端读取宝可梦记录时会把
`NULL` 或非正数统一转为 `-1`，避免客户端把空槽误当作可显示道具。已有数据库可执行
[`db/migrate_pokemon_empty_item.sql`](../db/migrate_pokemon_empty_item.sql) 进行一次性清理。

查看角色队伍：

```sql
SELECT p.id,
       p.container_position,
       p.dex_id,
       p.name,
       p.level_value,
       p.current_hp,
       p.moves
FROM public.pokemon p
JOIN public.character c ON c.id = p.trainer_id
WHERE c.name = 'Kyu'
  AND p.container_id = 1
ORDER BY p.container_position;
```

## 7. 道具与背包

### 7.1 `inventory`

道具容器类型字典。

| ID | 名称 | 用途 |
| ---: | --- | --- |
| 0 | `void` | 未归类 |
| 1 | `inventory` | 角色背包 |
| 2 | `warehouse` | 仓库 |
| 3 | `mail` | 邮件附件 |
| 4 | `temporary_event_inventory` | 临时活动背包 |
| 5 | `temporary_shared_event_inventory` | 共享临时活动背包 |

### 7.2 `owned_item`

存储角色拥有的道具。

| 字段 | 类型 | 作用 |
| --- | --- | --- |
| `item_id` | `BIGINT` | 这条持有记录的唯一编号；不是道具类型编号 |
| `owner_id` | `BIGINT` | 角色 ID |
| `item_index_id` | `SMALLINT` | 道具类型/索引编号 |
| `item_amount` | `SMALLINT` | 数量 |
| `inventory_id` | `SMALLINT` | 所在道具容器 |
| `color_id` | `SMALLINT` | 颜色/染色信息 |
| `item_region_index_id` | `SMALLINT` | 地区道具索引 |
| `pvp_reward_level` | `SMALLINT` | PVP 奖励等级 |
| `pvp_reward_season` | `SMALLINT` | PVP 奖励赛季 |
| `pvp_reward_time` | `TIMESTAMP` | PVP 奖励时间 |

`owner_id` 在当前 SQL 中没有数据库外键，由业务代码按 `character.id` 使用。

查看角色背包：

```sql
SELECT oi.item_id, oi.item_index_id, oi.item_amount, i.name AS inventory_name
FROM public.owned_item oi
JOIN public.inventory i ON i.id = oi.inventory_id
JOIN public.character c ON c.id = oi.owner_id
WHERE c.name = 'Kyu'
ORDER BY oi.inventory_id, oi.item_id;
```

### 7.3 邮件表

`mail_message` 保存邮件正文和收发件人；三张附件表分别保存道具、精灵和金钱附件。发送邮件时，
服务端在一个事务中将道具/精灵转移到收件人的 `mail` 容器，并把转移后的 Object ID 写入附件表；
金钱从发送者 `character.money` 扣除后写入 `mail_money_attachment`。附件领取流程尚未实现，暂时不要
直接删除 `mail_*_attachment` 记录或 mail 容器中的资产。

可单独执行 `db/create_mail_tables.sql` 为已有数据库创建这些表；初始化数据库则使用
`db/schemas/init.sql`。

## 8. 社交与图鉴

### 8.1 `black_list`

计划用于记录玩家黑名单。

| 字段 | 作用 |
| --- | --- |
| `player_id` | 执行屏蔽的角色 |
| `block_player_id` | 被屏蔽角色 |
| `block_time` | 屏蔽时间 |
| `block_player_name` | 被屏蔽角色名快照 |
| `block_reason` | 屏蔽原因 |

当前 `player_id` 单独作为主键，意味着每名玩家最多只能有一条黑名单记录。这通常不符合实际需求，合理的主键更可能是 `(player_id, block_player_id)` 或独立自增 ID。

### 8.2 `friend_list`

记录单向好友关系，包含 `player_id`、`friend_id` 和 `add_time`。游戏服收到 C2S `0x60` 时，
由 `FriendService` 在事务中锁定角色和关系记录：不存在则添加，存在则删除。目标角色必须存在，
且数据库约束禁止把自己加入好友列表。列表查询按添加时间和好友 ID 稳定排序，并由 S2C `0x63`
发送给客户端；在线状态不落库，而是通过游戏服会话池实时判断。

主键为 `(player_id, friend_id)`，因此每名玩家可以保存多个好友；两个字段都引用 `character(id)`，
角色删除时关系会级联删除。旧数据库可执行 `db/migrate_friend_list.sql` 升级主键和约束，随后重新
生成 jOOQ 代码，不要直接修改 `db/src/main/java/org/pokemmo/db/jooq` 下的生成文件。

### 8.3 `pokemon_dex`

每个角色一条图鉴记录，四个 `BYTEA` 字段用位图表示各图鉴编号的状态：

| 字段 | 作用 |
| --- | --- |
| `meet_level` | 已遇见 |
| `already_have_level` | 已拥有 |
| `caught_level` | 已捕获 |
| `caught_alpha_level` | 已捕获头目 |

每个位图默认 82 字节，即 656 位。游戏服将这些字节转换成 Java `BitSet`。

## 9. 常用检查 SQL

检查关键表是否存在：

```sql
SELECT to_regclass('public.account') AS account_table,
       to_regclass('public.character') AS character_table,
       to_regclass('public.game_node') AS game_node_table,
       to_regclass('public.online_game_node_server') AS game_server_table;
```

查看每张表的记录数量：

```sql
SELECT 'account' AS table_name, count(*) FROM public."account"
UNION ALL SELECT 'character', count(*) FROM public.character
UNION ALL SELECT 'pokemon', count(*) FROM public.pokemon
UNION ALL SELECT 'owned_item', count(*) FROM public.owned_item
UNION ALL SELECT 'server_token', count(*) FROM public.server_token
UNION ALL SELECT 'account_context', count(*) FROM public.account_context;
```

检查账号、角色和重连上下文：

```sql
SELECT a.account_id,
       a.account_name,
       c.id AS character_id,
       c.name AS character_name,
       ac.game_node_id,
       ac.server_id,
       ac.is_log_out,
       ac.update_at
FROM public."account" a
LEFT JOIN public.character c ON c.account_id = a.account_id
LEFT JOIN public.account_context ac ON ac.account_id = a.account_id
ORDER BY a.account_id, c.id;
```

## 10. 当前结构的重要注意事项

1. `account.password` 使用无盐 SHA-1，只适合当前兼容性测试，不适合公开生产环境。
2. 数据库密码目前硬编码在三个服务的 `Main.java` 中，应迁移到环境变量或配置文件。
3. `character.account_id`、`pokemon.trainer_id`、`pokemon.container_id` 和 `owned_item.owner_id` 等关系没有数据库外键，直接删除账号或角色可能留下孤立数据。
4. `black_list` 仍以 `player_id` 为单列主键，只能保存一条记录；`friend_list` 已改为
   `(player_id, friend_id)` 复合主键。已有数据库必须执行好友表迁移脚本，否则第二名好友会因旧主键冲突。
5. `account_context.is_log_out = TRUE` 会触发登录服重连路径；节点或服务器记录不完整时可能表现为客户端“系统错误”。
6. 初始化脚本显式插入了部分自增主键，例如 `character.id = 1`、`online_game_node_server.server_id = 1` 和 `container.id = 0..9`，但没有同步对应序列。后续使用默认自增值插入时可能发生主键冲突。
7. 初始化脚本中的游戏服和聊天服地址是 `127.0.0.1`。只有服务端与客户端在同一台机器时该地址才可用。
8. 当前部分节点查询的 `JOIN` 条件没有直接关联两张表，部署多个节点或多个服务器实例前应重新检查查询结果是否重复或选错服务器。

检查自增序列的当前值：

```sql
SELECT pg_get_serial_sequence('public.character', 'id') AS character_sequence,
       pg_get_serial_sequence('public.online_game_node_server', 'server_id') AS game_server_sequence,
       pg_get_serial_sequence('public.container', 'id') AS container_sequence;
```

在确认数据正确并完成备份后，可以让序列追上表中的最大 ID：

```sql
SELECT setval(
    pg_get_serial_sequence('public.character', 'id'),
    COALESCE((SELECT max(id) FROM public.character), 1),
    true
);

SELECT setval(
    pg_get_serial_sequence('public.online_game_node_server', 'server_id'),
    COALESCE((SELECT max(server_id) FROM public.online_game_node_server), 1),
    true
);

SELECT setval(
    pg_get_serial_sequence('public.container', 'id'),
    COALESCE((SELECT max(id) FROM public.container), 1),
    true
);
```

## 11. 备份建议

执行批量更新、删除账号、清理角色或修改节点地址之前，先备份数据库：

```powershell
pg_dump -h 127.0.0.1 -p 5432 -U openmmo -d mmo_db -Fc -f "mmo_db_backup.dump"
```

数据库结构的权威来源是 `db/schemas/init.sql`。jOOQ 生成类位于 `db/src/main/java/org/pokemmo/db/jooq`，登录、游戏和聊天服务通过这些生成类访问数据库。
