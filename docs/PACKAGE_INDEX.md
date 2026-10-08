# OpenMMO Java 包索引

该索引按 Java package 汇总源码数量，配合 [`SOURCE_FILE_INDEX.md`](SOURCE_FILE_INDEX.md) 使用。每个 package 的职责、主要类型和修改边界见 [`PACKAGE_PURPOSES.md`](PACKAGE_PURPOSES.md)。数量是静态扫描结果，不代表每个类都已完成业务实现。

## server

| Package | 文件数 |
| --- | ---: |
| `org.server` | 10 |
| `org.server.bytes` | 2 |
| `org.server.context` | 1 |
| `org.server.handlers` | 4 |
| `org.server.node` | 2 |
| `org.server.protocol.tls` | 3 |
| `org.server.protocol.tls.hash` | 4 |
| `org.server.protocol.tls.packets.c2s.incoming` | 2 |
| `org.server.protocol.tls.packets.c2s.outgoing` | 4 |
| `org.server.protocol.tls.packets.s2c.incoming` | 1 |
| `org.server.protocol.tls.packets.s2c.outgoing` | 1 |
| `org.server.redis` | 2 |
| `org.server.services` | 1 |
| `org.server.union.chat` | 2 |
| `org.server.union.language` | 1 |
| `org.server.util` | 4 |

## server.game

| Package | 文件数 |
| --- | ---: |
| `mmo` | 1 |
| `org.pokemmo.gameserver` | 2 |
| `org.pokemmo.gameserver.codecs` | 24 |
| `org.pokemmo.gameserver.command` | 7 |
| `org.pokemmo.gameserver.command.commands` | 33 |
| `org.pokemmo.gameserver.game.account` | 1 |
| `org.pokemmo.gameserver.game.backgroundmusic` | 1 |
| `org.pokemmo.gameserver.game.badge` | 1 |
| `org.pokemmo.gameserver.game.battle` | 58 |
| `org.pokemmo.gameserver.game.battle.effect` | 13 |
| `org.pokemmo.gameserver.game.bin` | 1 |
| `org.pokemmo.gameserver.game.building` | 1 |
| `org.pokemmo.gameserver.game.character` | 15 |
| `org.pokemmo.gameserver.game.container` | 1 |
| `org.pokemmo.gameserver.game.entity` | 12 |
| `org.pokemmo.gameserver.game.events` | 5 |
| `org.pokemmo.gameserver.game.frame` | 1 |
| `org.pokemmo.gameserver.game.friend` | 1 |
| `org.pokemmo.gameserver.game.giftshop` | 4 |
| `org.pokemmo.gameserver.game.gtl` | 13 |
| `org.pokemmo.gameserver.game.holiday` | 1 |
| `org.pokemmo.gameserver.game.instance` | 1 |
| `org.pokemmo.gameserver.game.interact` | 4 |
| `org.pokemmo.gameserver.game.item` | 20 |
| `org.pokemmo.gameserver.game.kick` | 1 |
| `org.pokemmo.gameserver.game.map` | 30 |
| `org.pokemmo.gameserver.game.move` | 11 |
| `org.pokemmo.gameserver.game.npc` | 4 |
| `org.pokemmo.gameserver.game.particleEffectType` | 1 |
| `org.pokemmo.gameserver.game.permission` | 1 |
| `org.pokemmo.gameserver.game.platform` | 3 |
| `org.pokemmo.gameserver.game.player` | 2 |
| `org.pokemmo.gameserver.game.pokemon` | 46 |
| `org.pokemmo.gameserver.game.pool` | 1 |
| `org.pokemmo.gameserver.game.region` | 2 |
| `org.pokemmo.gameserver.game.rom` | 2 |
| `org.pokemmo.gameserver.game.script` | 43 |
| `org.pokemmo.gameserver.game.shop` | 11 |
| `org.pokemmo.gameserver.game.story` | 26 |
| `org.pokemmo.gameserver.game.skin` | 2 |
| `org.pokemmo.gameserver.game.string` | 7 |
| `org.pokemmo.gameserver.game.trainer` | 9 |
| `org.pokemmo.gameserver.game.trade` | 12 |
| `org.pokemmo.gameserver.protocol` | 1 |
| `org.pokemmo.gameserver.protocol.packets.c2s` | 75 |
| `org.pokemmo.gameserver.protocol.packets.s2c` | 105 |
| `org.pokemmo.gameserver.protocol.packets.scriptsession` | 1 |
| `org.pokemmo.gameserver.script` | 1 |
| `org.pokemmo.gameserver.services` | 1 |
| `org.pokemmo.gameserver.services.character` | 1 |
| `org.pokemmo.gameserver.services.friend` | 1 |
| `org.pokemmo.gameserver.services.gtl` | 19 |
| `org.pokemmo.gameserver.services.inventory` | 1 |
| `org.pokemmo.gameserver.services.item` | 1 |
| `org.pokemmo.gameserver.services.mail` | 6 |
| `org.pokemmo.gameserver.services.pokemon` | 8 |
| `org.pokemmo.gameserver.services.shop` | 6 |
| `org.pokemmo.gameserver.services.story` | 7 |
| `org.pokemmo.gameserver.services.trade` | 5 |
| `org.pokemmo.gameserver.services.world` | 1 |
| `org.pokemmo.gameserver.util` | 5 |

## server.login

| Package | 文件数 |
| --- | ---: |
| `org.pokemmo.loginserver` | 2 |
| `org.pokemmo.loginserver.login` | 2 |
| `org.pokemmo.loginserver.protocol` | 1 |
| `org.pokemmo.loginserver.protocol.packets.c2s` | 3 |
| `org.pokemmo.loginserver.protocol.packets.s2c` | 7 |
| `org.pokemmo.loginserver.service` | 1 |

## server.chat

| Package | 文件数 |
| --- | ---: |
| `org.pokemmo.chatserver` | 1 |
| `org.pokemmo.chatserver.chat` | 2 |
| `org.pokemmo.chatserver.protocol` | 1 |
| `org.pokemmo.chatserver.protocol.c2s` | 3 |
| `org.pokemmo.chatserver.protocol.s2c` | 3 |
| `org.pokemmo.chatserver.services` | 1 |

## db

| Package | 文件数 |
| --- | ---: |
| `org.pokemmo.db` | 1 |
| `org.pokemmo.db.jooq` | 5 |
| `org.pokemmo.db.jooq.routines` | 35 |
| `org.pokemmo.db.jooq.tables` | 15 |
| `org.pokemmo.db.jooq.tables.records` | 15 |

## patcher

| Package | 文件数 |
| --- | ---: |
| `org.patcher` | 4 |

## 重点包职责

| 包 | 后续开发用途 |
| --- | --- |
| `org.server` / `org.server.handlers` | 通用 Netty 生命周期、帧、封包、Session |
| `org.server.protocol.tls` | TLS 握手、密钥、加密和哈希 |
| `org.pokemmo.gameserver.protocol` | 游戏 opcode 注册和连接断开生命周期 |
| `org.pokemmo.gameserver.protocol.packets` | 游戏 c2s/s2c 封包处理 |
| `org.pokemmo.gameserver.codecs` | 游戏对象和封包字段序列化 |
| `org.pokemmo.gameserver.command` | NORMAL 聊天命令分发和 GM 命令 |
| `org.pokemmo.gameserver.game.character` | 在线角色上下文、队伍状态、管理员地图隐身与定向可见性同步 |
| `org.pokemmo.gameserver.game.interact` | 场景 A 键目标分派、NPC 脚本入口、电脑菜单与交互类型；商店目标转交 game.shop |
| `org.pokemmo.gameserver.game.item` | 道具基础资源、模型和规则、任务包裹禁止转移判定，不承载剧情编排、店铺价格或结算 |
| `org.pokemmo.gameserver.game.npc` | 独立自定义 NPC 定义、严格 JSONC、原子保存/停用、实际实体身份校验、固定高序号和启动恢复；不修改原地图 |
| `org.pokemmo.gameserver.game.shop` | 店铺配置、NPC 绑定校验与不可变索引、绑定/价格快照、NPC 访问、报价会话、重载与在线同步；不直接执行 SQL |
| `org.pokemmo.gameserver.services.shop` | 金钱与主背包的购买/出售原子事务、道具限制和提交结果；不发送封包 |
| `org.pokemmo.gameserver.game.pokemon` | 宝可梦数据、生成、属性和容器 |
| `org.pokemmo.gameserver.game.battle` | 战斗上下文、队伍、行动和结算 |
| `org.pokemmo.gameserver.game.script` | 通用交互封包数据与旧脚本类型兼容；旧剧情图不再加载执行 |
| `org.pokemmo.gameserver.game.story` | 通用剧情章节加载、触发器/节点/动作解释、六个现有章节的配置驱动路由、节点奖励事务编排、内联训练家战斗、登录/重连进度重读、检查点一致性校验、独立 NPC 投影、原生表现与战斗衔接 |
| `org.pokemmo.gameserver.services.story` | 带角色/账号条件的检查点、初始宝可梦/图鉴和战斗结算事务、包裹与五球奖励、早期徽章/故事位原子变更、通用剧情动作幂等奖励事务；不发包 |
| `org.pokemmo.gameserver.services` | 领域服务组装与旧调用方兼容门面 |
| `org.pokemmo.gameserver.services.character` | 角色查询、角色资产、跟随精灵和外观持久化 |
| `org.pokemmo.gameserver.services.friend` | 好友关系切换、持久化和列表查询 |
| `org.pokemmo.gameserver.services.gtl` | 交易行上架、购买、取消、成交款领取、列表查询、筛选和排序 |
| `org.pokemmo.gameserver.services.inventory` | 背包、拥有道具和道具数量变更持久化 |
| `org.pokemmo.gameserver.services.item` | 道具使用效果、归属校验、事务扣除和宝可梦持久化 |
| `org.pokemmo.gameserver.services.pokemon` | 宝可梦属性、捕获、PC 放生、PARTY HP/PP 恢复持久化、容器查询、换位、等级排序和空槽分配 |
| `org.pokemmo.gameserver.services.world` | 账号上下文、节点、容器、事件、图鉴和实例数据 |
| `org.pokemmo.db.jooq` | 生成的数据库表、记录、键和 routines |
