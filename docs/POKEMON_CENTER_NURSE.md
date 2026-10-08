# 宝可梦中心护士治疗

## 触发方式

场景 A 键或明确 NPC 对话仍由 `SceneInteractionService` 识别。只对地图 key 以
`PokemonCenter_1F` 结尾、且脚本以 `EventScript_Nurse` 结尾的 NPC 启用护士流程，
因此常磐市宝可梦中心护士脚本
`ViridianCity_PokemonCenter_1F_EventScript_Nurse` 会被正确接管。

## 流程

服务端复用客户端原生 `0x21` 交互：

```text
护士交互
  -> 询问是否治疗
  -> 玩家确认
  -> “暂时由我保管”
  -> PokemonHealingService.healParty(..., cureStatus=true)
  -> 重新读取 PARTY 并发送完整容器
  -> “体力都恢复了”
  -> “欢迎下次再来”
```

取消治疗只播放欢迎对白，不修改数据库。确认治疗会恢复 PARTY 宝可梦的 HP、招式 PP
和异常状态；蛋不会被修改。治疗事务按角色 ID、训练家 ID、容器和槽位校验，失败时
不发送成功对白，也不会伪造在线状态。

## 协议与文本

不新增 opcode。对白使用客户端已有文本引用：

| 用途 | 文本编号 |
| --- | ---: |
| 治疗询问 | `271001323` |
| 接收队伍 | `271001448` |
| 治疗完成 | `271001496` |
| 欢迎再来 | `271001563` |

确认回执继续使用 C2S `0x21` 的 `interactTimes + choice` 两字节格式。

## 代码位置

- `org.pokemmo.gameserver.game.story.PokemonCenterNurseStory`：护士入口、对白顺序和完成后的刷新。
- `org.pokemmo.gameserver.services.pokemon.PokemonHealingService`：带归属校验的整队恢复事务。
- `org.pokemmo.gameserver.game.story.PalletStoryParty`：重读 PARTY、更新在线数组和发送完整容器。

## 验证状态

已静态核对地图脚本键、文本编号、统一 NPC 路由、`0x21` 回执路由、治疗事务和 PARTY
刷新调用。按仓库约定未编译、未启动服务端、未连接数据库、未进行客户端实机验证。
