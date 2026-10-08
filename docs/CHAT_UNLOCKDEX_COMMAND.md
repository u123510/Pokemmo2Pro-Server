# `//unlockdex` 管理员点亮全部图鉴

## 用法

```text
//unlockdex
//unlockdex [玩家名称]
```

- **别名**：`//alldex`、`//fulldex`
- **权限要求**：**ADM 管理员权限（权限值 >= 10）**。普通玩家、STAFF、CM、MOD、GM（值 7）等均会被命令分发器直接拒绝。
- **参数说明**：
  - 不带参数时：为执行命令的管理员自身点亮所有图鉴。
  - 带 1 个参数时：为指定的角色名称点亮所有图鉴（支持在线角色或已存在的离线角色）。
  - 参数超过 1 个时：返回用法提示。

## 点亮范围

- **前五代全国图鉴**：编号 `1 ~ 649` 全部点亮。
- **自定义与新增世代图鉴**：通过 `PokemonManager.getAllPokemonDexData()` 扫描并点亮服务端加载的全部物种（包括 `gen6`、`gen7`、`gen8`、`gen9` 中编号到达 1000+ 的新宝可梦，如仙子伊布 1058、密勒顿 1008 等）。
- **图鉴层级位图（4 层全满）**：
  - `meet_level`（已遇见）：对应比特位置 1。
  - `already_have_level`（已收服）：对应比特位置 1。
  - `caught_level`（形态捕获）：对应比特位置 1。
  - `caught_alpha_level`（头目/满捕获）：对应比特位置 1。
- 客户端的图鉴 4 根统计进度条（白条遇见、蓝条收服、绿条形态、粉条完美）均会达到 100% 满额。

## 状态持久化与客户端同步

1. **数据库持久化**：
   - 目标表：`public.pokemon_dex`。
   - 包含角色存在性检查与事务 Upsert（记录存在则 `UPDATE`，不存在则 `INSERT`）。
   - 将动态生成的变长 `BitSet.toByteArray()`（如 138+ 字节）写入 4 个 `BYTEA` 字段，规避了初始 schema 默认 82 字节（只能容纳 656 位）导致的截断问题。
2. **客户端实时刷新**：
   - 若目标角色在线，服务端通过目标 `Session` 发送 S2C `0x0A`（`SendLoadDexPacket`），客户端接收后调用 Deflater 解压并载入 `BitSet[]`，图鉴界面即时点亮，无需重登。
   - 若目标角色离线，数据成功落库保存，下次登录时由登入流程下发 `SendLoadDexPacket` 展现。

## 涉及文件

- `org.pokemmo.gameserver.command.commands.UnlockDexCommand`：命令入口、ADM 权限校验、目标角色路由与反馈。
- `org.pokemmo.gameserver.services.world.WorldService`：图鉴位图全量构建、数据库 Upsert 持久化。
- `org.pokemmo.gameserver.services.GameServerService`：门面委托。
- `org.pokemmo.gameserver.command.GameCommandModule`：Guice 命令注册。
