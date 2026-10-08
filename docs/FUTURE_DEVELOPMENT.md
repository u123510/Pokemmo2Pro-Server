# 后续开发准备清单

## 1. 新增游戏功能

先确定功能属于哪一层：

| 问题 | 处理位置 |
| --- | --- |
| 客户端发送新封包 | `server.game/protocol/packets/c2s` + `GameProtocol` |
| 服务端发送新封包 | `server.game/protocol/packets/s2c` + Codec + `GameProtocol` |
| 持久化字段 | `db/schemas` + jOOQ 重新生成 + 对应领域服务 |
| 在线角色状态 | `CharacterManager` 或对应 domain manager |
| 宝可梦属性 | `game/pokemon` + 数据库 + 更新封包 |
| 地图/剧情逻辑 | `resource` + `game/script` / `game/interact` |
| GM 聊天命令 | `command/commands` + `GameCommandModule` + 文档 |

## 2. 封包开发模板

```text
1. 记录抓包中的 opcode、长度、字段顺序和字节序。
2. 在正确方向注册 opcode。
3. 编写 Packet.decode 或 Packet.encode。
4. 对照 ByteBufEx 的 LE、UTF-16LE、数组和终止符方法。
5. 在 Session 日志中确认 READ/WRITE 长度。
6. 检查是否需要异步处理、压缩或 TLS 状态。
```

## 3. 数据修改模板

```text
请求参数校验
  -> 找到目标对象（在线内存优先，数据库回退）
  -> 数据库写入（带 owner/trainer 条件）
  -> 更新内存对象
  -> 发送增量更新包
  -> 必要时发送完整容器/角色刷新
  -> 返回可验证的反馈消息
```

## 4. 需要优先治理的问题

- 把连接地址、用户名、密码和证书路径迁移到配置文件或环境变量。
- 给 `Protocol.decode` 增加更明确的 opcode、方向和连接上下文日志。
- 统一 `PokemonData` 的性别、性格、IV/EV 顺序和最大 HP 计算。
- 为数据库 schema 建立版本化迁移，不只依赖完整初始化脚本。
- 把资源加载错误从启动日志升级为可定位的文件、字段和行号错误。
- 为关键协议 Codec、命令参数和数据库写入增加单元测试。
- 减少 `Session.send` 固定 sleep 对批量刷新造成的延迟。

## 5. 文档维护规则

源码新增或移动后，应同步更新：

1. `docs/SOURCE_FILE_INDEX.md`；
2. `docs/PACKAGE_INDEX.md` 和 `docs/PACKAGE_PURPOSES.md`；
3. 对应模块文档；
4. 协议或命令专用 Markdown；
5. `PROJECT_ARCHITECTURE.md` 中的模块边界（如果边界发生变化）。
