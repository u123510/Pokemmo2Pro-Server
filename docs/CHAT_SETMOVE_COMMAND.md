# `setmove` 宝可梦技能更换命令

## 输入格式

该命令通过 `ChatType.NORMAL` 聊天或客户端 `pro` 扩展 UI 发送：

```text
//setmove <party slot 0-5> <move slot 0-3> <move id>
```

或全槽位配置模式：

```text
//setmove <party slot 0-5> <move1> <move2> <move3> <move4>
```

别名：`//setmoves`、`//setskill`、`//setskills`。

示例：

```text
//setmove 0 0 85
```
表示将当前出战队伍第 1 个槽位（索引 0）的宝可梦的第 1 个技能（索引 0）替换为 85 号技能（十万伏特）。

```text
//setmove 0 1 0
```
表示清空第 1 个槽位宝可梦的第 2 个技能槽位（遗忘技能）。

```text
//setmove 0 85 94 53 202
```
表示将第 1 个槽位宝可梦的 4 个技能同时设置为 85、94、53、202。

## 参数规范与约束

1. `party slot`: 必须为 `0..5` 的整数，且当前槽位必须存在宝可梦。
2. `move slot`: 单槽模式下必须为 `0..3` 的整数。
3. `move id`:
   - `0`: 清空槽位（遗忘）。宝可梦不可遗忘全部技能，必须保留至少 1 个有效技能。
   - `> 0`: 必须为服务端 `MoveManager` 中已注册的有效技能索引 ID（范围 `1..10000`）。
4. 替换非零技能时，该技能槽位的 PP 提升次数会被重置，初始 PP 自动设为该技能的基础最大 PP (`moveBasePp`)。

## 状态同步时序

```text
参数校验（槽位、范围、技能存在性与非空技能约束）
  -> 使用 pokemon.id + trainer_id 双重条件更新数据库 (pokemon.moves, pokemon.moves_pp, pokemon.pp_up_times)
  -> 数据库成功后更新在线 PokemonData 内存
  -> 发送 0x16 增量更新封包 (SendUpdatePokemonDataPacket, flag |= 4, reloadMove)
  -> 客户端接收 0x16 并刷新本地配招视图
  -> 返回聊天栏执行成功反馈
```

## 客户端交互

客户端在 `pro.pokemmo2.skill` 包中提供了可视化的技能替换窗口：
- 通过聊天栏输入 `/skill`、`/move` 或 `/jn` 打开窗口。
- 窗口可自动列出队伍中 6 只宝可梦及其现有 4 个技能槽位的名称与 PP。
- 提供全量技能搜索（支持按中文名或 ID 检索），选定后自动构造并发送 `//setmove` 指令。
