# OpenMMO Agent 开发规范

本文件是 OpenMMO 项目的根目录开发约定，适用于在本仓库中工作的 AI agent 和开发者。除非用户明确要求，否则不要编译、启动服务器、修改数据库或改变运行环境；用户会自行完成编译和运行验证。

## 1. 开始工作前

开始任何代码修改前，必须先阅读与任务相关的文档和源码，不要根据类名猜测职责：

1. [`docs/README.md`](docs/README.md)：文档入口。
2. [`docs/PROJECT_ARCHITECTURE.md`](docs/PROJECT_ARCHITECTURE.md)：模块边界、运行时数据流和状态同步原则。
3. [`docs/PACKAGE_INDEX.md`](docs/PACKAGE_INDEX.md)：package 与文件数量索引。
4. [`docs/PACKAGE_PURPOSES.md`](docs/PACKAGE_PURPOSES.md)：每个 Java package 的职责和主要类型。
5. [`docs/STORY_DEVELOPMENT_GUIDE.md`](docs/STORY_DEVELOPMENT_GUIDE.md)：剧情章节状态、参考来源和新增流程。
6. 与任务直接相关的 `MODULE_*.md`、协议文档、命令文档和源码。

新增、修改或删除任何剧情 Java、JSONC、地图触发、剧情存档字段、剧情协议或剧情文档前，
必须先读取 [`docs/STORY_DEVELOPMENT_GUIDE.md`](docs/STORY_DEVELOPMENT_GUIDE.md)，
并按照其中的当前章节状态、参考来源、持久化和验证流程执行。

先检查工作区现有改动。不要撤销、覆盖或格式化用户已有的无关修改；如果已有修改影响当前任务，应在现有内容上继续工作。

## 2. 项目边界

项目使用 Java 17、Gradle、Netty、Guice、PostgreSQL、jOOQ 和 Redis。Gradle 子项目与职责如下：

| 模块 | 允许负责的内容 | 主要 package |
| --- | --- | --- |
| `server` | 通用 Session、Protocol、Packet、Netty pipeline、TLS、压缩、Redis 和跨服务模型 | `org.server.*` |
| `server.login` | 登录、账号校验、游戏节点列表、token 和进入游戏 | `org.pokemmo.loginserver.*` |
| `server.game` | 游戏世界、角色、宝可梦、战斗、地图、脚本、道具、GM 命令和游戏协议 | `org.pokemmo.gameserver.*` |
| `server.chat` | 聊天服务器、频道、聊天连接和广播 | `org.pokemmo.chatserver.*` |
| `db` | PostgreSQL 连接、schema 和 jOOQ 生成模型 | `org.pokemmo.db.*` |
| `patcher` | Java Agent、ASM 和客户端证书替换 | `org.patcher.*` |

不要把游戏业务写进 `server`，不要把数据库查询散落在 packet 或 command 中，不要把聊天业务复制到游戏服务器中。跨模块通用能力必须先确认确实属于共享层，再放入 `server`。

## 3. Package 放置规则

### 3.1 通用层 `server`

- `org.server`：`Server`、`Session`、`Protocol`、`Packet`、数据流和连接生命周期。
- `org.server.bytes`：`ByteBufEx` 等字节读写辅助；协议字段优先使用这里已有的方法。
- `org.server.handlers`：Netty pipeline 的帧、封包、加密、压缩和日志处理器。
- `org.server.protocol.tls*`：TLS 握手、哈希和 TLS 封包；不得与游戏 opcode 混用。
- `org.server.services`、`org.server.redis`：跨服务节点、token、账号上下文和 Redis 能力。
- `org.server.union.*`：跨模块共享的聊天、语言等模型。
- `org.server.util`：确实通用且无业务语义的工具。业务工具放回所属模块。

### 3.2 游戏层 `server.game`

- `org.pokemmo.gameserver`：游戏服务器入口和启动数据。
- `org.pokemmo.gameserver.protocol`：`GameProtocol` 和 opcode 注册。
- `org.pokemmo.gameserver.protocol.packets.c2s`：客户端发往游戏服务器的请求 packet，负责 `decode` 和 `handle`。
- `org.pokemmo.gameserver.protocol.packets.s2c`：游戏服务器发往客户端的响应 packet，负责 `encode`。
- `org.pokemmo.gameserver.protocol.packets.scriptsession`：脚本/战斗会话专用协议对象。
- `org.pokemmo.gameserver.codecs`：宝可梦、战斗、角色和消息等复杂对象的二进制序列化。
- `org.pokemmo.gameserver.command`：命令接口、解析、注册、权限、上下文和反馈。
- `org.pokemmo.gameserver.command.commands`：每个 GM/聊天命令一个独立类；禁止把多个命令堆在一个类里。
- `org.pokemmo.gameserver.game.pokemon`：宝可梦领域模型、属性、性别、性格、IV、EV、特性、状态和更新对象。
- `org.pokemmo.gameserver.game.character`：角色数据、在线角色管理和队伍上下文。
- `org.pokemmo.gameserver.game.battle`、`game.battle.effect`：战斗状态、行动、结算和战斗效果。
- `org.pokemmo.gameserver.game.item`：道具数据、资源解析、道具效果和道具管理。
- `org.pokemmo.gameserver.game.map`、`region`、`entity`、`interact`：地图、地区、实体、移动和交互。
- `org.pokemmo.gameserver.game.script`、顶层 `org.pokemmo.gameserver.script`：脚本节点、动作解析和脚本管理入口。
- `org.pokemmo.gameserver.game.story`：章节配置、触发器、节点编排、原生对白、地图/NPC 表现和剧情运行时；
  新剧情按章节组织，不要为每句对白或每个事件创建专用类。
- `org.pokemmo.gameserver.game.move`、`trainer`、`string`、`skin`、`platform` 等：只放对应领域的数据和规则。
- `org.pokemmo.gameserver.services`：游戏数据库访问和持久化业务；需要 owner/trainer 条件的更新必须集中在这里。
- `org.pokemmo.gameserver.util`：游戏模块专用工具；不要用它承载领域规则。
- `mmo`：历史兼容工具包。新增代码不要继续扩大该包，优先放入明确的 `org.pokemmo.gameserver.*` package。

完整的 package 职责以 [`docs/PACKAGE_PURPOSES.md`](docs/PACKAGE_PURPOSES.md) 为准；当前生产源码静态索引为 95 个 package，数量变化时以索引为准。如果现有职责说明不足，先补文档再决定放置位置；不要创建含义模糊的 `common`、`misc`、`manager2` 或无边界的工具包。

剧情开发的当前地区/章节完成状态、参考来源和阶段语义以
[`docs/STORY_DEVELOPMENT_GUIDE.md`](docs/STORY_DEVELOPMENT_GUIDE.md) 为准。
当前关都已接入真新镇开场、大木包裹/图鉴和常磐市捕获教学三个章节；
地图已加载不等于该地区其他剧情已实现。

### 3.3 登录、聊天、数据库和补丁层

- `org.pokemmo.loginserver.protocol.packets.c2s/s2c`：登录方向的请求/响应 packet，方向必须与实际数据流一致。
- `org.pokemmo.loginserver.login`：登录拒绝、踢出和状态类型。
- `org.pokemmo.loginserver.service`：账号、角色、节点和登录上下文业务。
- `org.pokemmo.chatserver.chat`：聊天连接、频道和广播；`protocol.c2s/s2c` 只放聊天封包。
- `org.pokemmo.chatserver.services`：聊天节点服务。
- `org.pokemmo.db`：数据库连接封装。
- `org.pokemmo.db.jooq`、`routines`、`tables`、`tables.records`：jOOQ 生成代码，禁止直接手改；修改 schema 后重新生成。
- `org.patcher`：只放 Java Agent 和 ASM 字节码转换逻辑，不放游戏服务器业务。

## 4. Java 代码规范

- 使用 Java 17 语法和项目已有的 Guice、Lombok、Netty、jOOQ 约定；不要为简单功能引入新的框架。
- package 全部小写；类型使用 `PascalCase`；方法和变量使用 `camelCase`；常量使用 `UPPER_SNAKE_CASE`。
- 一个源文件只放一个主要 public 类型，文件名必须与类型名一致。
- 新代码使用 4 个空格缩进、K&R 风格大括号；修改旧文件时保留该文件已有格式，避免无关格式化。
- import 使用明确的类型导入；新代码不要增加 wildcard import。按项目现状将 `java.*`、第三方依赖和项目类型分组。
- 优先使用 `final`、不可变参数和小方法；避免过长方法、深层嵌套和隐藏的全局状态。
- 公共 API、协议字段、复杂状态转换和非直观算法添加简短注释；不要写重复代码内容的注释。
- 不吞异常。必须记录或转换为有意义的业务反馈；命令错误不能导致 Session 无提示断开。
- 不要为了“规范化”顺手重命名现有协议字段、opcode、数据库字段或客户端可见字符串。
- 修改共享基类、`Protocol`、`Session`、Codec、数据库服务或宝可梦模型时，要检查所有调用方和客户端刷新路径。

## 5. 协议与封包规范

新增或修改封包必须按以下顺序处理：

1. 记录抓包中的 opcode、方向、长度、字段顺序、字节序、字符串编码和终止符。
2. 将 packet 放入正确的 `c2s` 或 `s2c` package；不要因为类名方便而放反方向。
3. 在对应的 `GameProtocol`、`LoginProtocol` 或 `ChatProtocol` 注册 opcode。
4. 使用 `ByteBufEx` 现有的 LE、UTF-16LE、数组和终止符方法，不要手写重复的字节转换。
5. `IncomingPacket` 实现 `decode`/`handle`；`OutgoingPacket` 实现 `encode`，不要让方向职责混乱。
6. 对未知 opcode、截断数据、非法长度和越界参数提供明确错误路径；不要静默接受脏数据。
7. 发送 packet 后确认数据库、在线内存和客户端状态是否都需要更新。
8. 更新对应的协议 Markdown，记录 opcode、字段、示例抓包和验证方式。

不要只根据日志中的十六进制内容猜测字段含义。若字段含义尚未确认，先在文档中标注未知，并继续收集抓包或源码证据。

### 5.1 客户端参考

客户端项目位于 `C:\Users\z3407\Desktop\28887-obf-project`。后续直接参考该客户端项目；缺失信息只读取字节码/JASM，不再导出或反编译到临时目录。

## 6. 聊天命令规范

聊天命令入口为 `ChatType.NORMAL` 的 `//...` 消息，标准链路是：

```text
ChatPacket
  -> CommandDispatcher
  -> CommandParser
  -> CommandRegistry
  -> 独立 Command
  -> GameServerService
  -> 内存对象更新
  -> s2c 更新封包/容器刷新
  -> 聊天反馈
```

新增命令必须：

- 在 `org.pokemmo.gameserver.command.commands` 创建独立类并实现 `Command`。
- 使用小写命令名，提供准确的 `getUsage()`，明确槽位、Object ID、数值范围和布尔值格式。
- 在 `GameCommandModule` 注册；没有注册的命令视为未实现。
- 在命令类中完成参数数量、数字范围、目标存在性、权限和性别/特性等领域约束检查。
- 将数据库更新放进 `GameServerService`，使用角色/训练家 ID 和目标对象 ID 双重条件，避免跨角色修改数据。
- 数据库成功后再更新在线 `PokemonData`、角色容器和客户端封包；失败时不得伪造成功反馈。
- 对会影响队伍、战斗预览或跟随宝可梦的属性，确认是否还需要完整容器刷新，而不是只发送单体更新包。
- 为每个命令增加或更新独立 Markdown，记录示例、参数顺序、索引约定、边界和客户端刷新行为。

目前已记录的命令文档见 [`docs/COMMAND_FRAMEWORK.md`](docs/COMMAND_FRAMEWORK.md) 和 `docs/CHAT_*.md`。不要把新命令直接写入一个巨大的命令处理器或通用软件包。

## 7. 数据库和状态同步

项目有三种必须分别考虑的状态：

1. 数据库状态：由 `GameServerService`、`LoginService`、`ServerService` 和 jOOQ 访问。
2. 在线内存状态：由 `CharacterManager`、`PokemonData`、战斗对象和地图对象维护。
3. 客户端状态：由 `Send*Packet` 和 Codec 同步。

修改宝可梦、道具、角色或容器时，按以下顺序设计：

```text
参数校验
  -> 找到目标对象（在线内存优先，数据库回退）
  -> 带 owner/trainer 条件写数据库
  -> 更新在线内存
  -> 发送增量更新包
  -> 必要时发送完整容器/角色刷新
  -> 返回可验证的反馈
```

只改数据库不会立即改变在线客户端；只改内存会在重载或重启后丢失。jOOQ 生成文件只读，schema 变更必须修改 `db/schemas` 并重新生成代码，不要直接编辑 `tables` 或 `records`。

## 8. 资源、配置和运行环境

- `resource/map`、`interact`、`event`、`pokemon`、`item`、`gift`、`trainer`、`move` 是运行时资源，路径依赖项目根目录工作目录。
- `resource/story` 是按地区和章节拆分的剧情配置目录；当前状态和配置契约见
  [`docs/STORY_DEVELOPMENT_GUIDE.md`](docs/STORY_DEVELOPMENT_GUIDE.md)。
- 修改 JSON/JSONC 资源时，保持现有字段命名、数组顺序和编码；先确认加载器，再改资源格式。
- 不要把密码、私钥、token 或客户端证书提交到新的源码、日志或文档中。
- 当前项目部分连接配置仍硬编码；新增功能不要扩大硬编码范围，优先沿用现有配置入口，并在文档中注明后续外置化需求。
- 不要修改 `build/`、`.gradle/` 等生成目录来“修复”源码问题。

## 9. 文档同步规则

源码新增、移动、删除或职责变化后，必须同步维护：

1. [`docs/SOURCE_FILE_INDEX.md`](docs/SOURCE_FILE_INDEX.md)
2. [`docs/PACKAGE_INDEX.md`](docs/PACKAGE_INDEX.md)
3. [`docs/PACKAGE_PURPOSES.md`](docs/PACKAGE_PURPOSES.md)
4. 对应的 `MODULE_*.md`
5. 对应的协议或命令 Markdown
6. [`docs/PROJECT_ARCHITECTURE.md`](docs/PROJECT_ARCHITECTURE.md)（模块边界或数据流变化时）
7. [`docs/STORY_DEVELOPMENT_GUIDE.md`](docs/STORY_DEVELOPMENT_GUIDE.md)（新增/删除/重命名章节、阶段、参考来源或验证状态时）

如果新增 package，必须同时写明它的职责、允许依赖、主要类型和不应承载的业务。文档中的 package 名必须使用 Java 点号格式，例如 `org.pokemmo.gameserver.game.pokemon`，不能写成文件路径反斜杠格式。

剧情任务完成前，必须重新读取 [`docs/STORY_DEVELOPMENT_GUIDE.md`](docs/STORY_DEVELOPMENT_GUIDE.md)，
复核当前地区/章节状态、完成节点、参考来源、修改文件和实际验证结果；未验证的编译、运行、
客户端效果或数据库操作必须明确标记为“未执行”。

## 10. 验证与完成标准

默认不编译项目，但每次代码修改仍应完成可执行的静态检查：

- 检查 import、package、类名、方法签名和调用方是否一致。
- 检查新增 packet 是否注册、方向是否正确、字段读取/写入顺序是否一致。
- 检查新增命令是否注册、权限和参数校验是否完整、反馈是否覆盖失败路径。
- 检查数据库写入是否带 owner/trainer 条件，jOOQ 生成代码是否未被手改。
- 检查在线内存、数据库和客户端刷新是否同步。
- 检查相关 Markdown 链接和文件索引是否存在。
- 检查剧情章节状态表、配置路径、阶段语义和参考来源是否与当前源码一致。
- 用 `rg` 搜索旧字段名、旧命令名、opcode 和所有调用方，避免遗漏。
- 若无法验证某个协议字段或客户端行为，明确记录“未验证”，不要声称已完成。

完成任务时，向用户说明：修改了哪些文件、放在哪些 package、实现了哪些边界检查、做了哪些静态验证，以及是否按用户要求跳过编译。

## 11. 禁止事项

- 不要把新业务塞进 `org.server`、`mmo` 或无明确职责的工具类。
- 不要为每一个对白、坐标触发器或 NPC 事件创建一个 Java 类；优先使用章节配置、
  通用触发器、条件和动作，只有真正的新领域副作用才增加可复用服务或动作处理器。
- 不要把规划中的通用 JSONC 节点格式当成当前已实现的运行时 API；先确认加载器和执行器是否存在。
- 不要直接修改 jOOQ 生成代码。
- 不要在未注册 opcode 的情况下新增 packet。
- 不要只更新数据库而不更新在线内存和客户端状态。
- 不要吞掉异常、返回虚假的成功消息或用固定延时掩盖时序问题。
- 不要为了完成小功能进行无关的大规模重构或格式化。
- 不要使用破坏性 Git 命令撤销用户修改。

## 12. 类规模与模块化规则

- 单个 Java 源文件以 **500 行（含空行）为强制上限**；新增文件和本次修改的文件不得超过该上限。既有超限类必须在本次变更中拆分，或在变更说明中记录受阻原因与后续拆分计划；新增代码不得继续扩大超限类。
- 单个类只能承载一个清晰的主要职责。上架、查询、筛选、购买、结算、广播、持久化等不同职责必须拆为独立的领域服务、策略、构建器或映射器。
- 超过 300 行的 `decode`/`encode`、状态类或服务类必须优先提取字段解析、校验、状态转换和副作用操作；方法重载不得替代职责拆分。
- 拆分后的组件放入职责明确的 package，优先使用构造器注入、不可变参数和小型接口；只有存在多个实现或需要隔离边界时才新增接口，禁止为形式拆分创建空壳接口。
- 旧类需要兼容现有调用方时，可以保留短期兼容门面，但门面只能转发到领域组件，不得重新实现业务逻辑；新代码应直接注入领域组件。
- 每次拆分都必须检查调用方、Guice/构造器创建点、协议注册、数据库事务边界和客户端刷新路径，并同步 `docs/SOURCE_FILE_INDEX.md`、`docs/PACKAGE_INDEX.md`、`docs/PACKAGE_PURPOSES.md` 及对应模块文档。
