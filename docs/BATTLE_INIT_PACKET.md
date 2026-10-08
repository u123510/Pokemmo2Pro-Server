# 战斗初始化与训练家队伍头

## 本次故障

2026-09-09 用户反馈：选好初始宝可梦后，劲敌战斗未出现，角色也不能移动。
客户端 `28887-obf-project/log/console.log` 在 23:12:37 记录：

```text
Reading failed for packet [C] 0x30 iv_1
java.lang.NullPointerException: Undefined xg_1 19
    at f.pd0_0.ml(...)
    at f.iv_1.Oj0(...)
```

23:19:50 的客户端状态仍为 `Has Battle false`。服务端已创建 `BattleManager`，
移动处理器正常拒绝战斗期间移动；但客户端丢弃了解析失败的初始化包，因此没有显示战斗。
这不是剧情存档未保存，也不是需要给 Boss 枚举添加数值 19。

## 确认的字段

方向：S2C，opcode `0x30`，变长 payload；使用现有 `SendBattleInitPacket`，本次不新增 opcode。
当前客户端将 `0x30` 路由到 `BattleActiveMonsterUpdatePacket`，该包负责读取完整战斗状态并安装战斗界面。
客户端的 `0x17` 路由到 `BattleInitPacket`，其字段是经验条/UI 更新，不是完整战斗初始化包。
以下只描述初始化包内部的普通训练家队伍头，不是完整 `0x30` 帧。

| 顺序 | 字段 | 长度/编码 |
| --- | --- | --- |
| 1 | 队伍类型 `TRAINER = 2` | byte |
| 2 | 最大队伍容量 | byte |
| 3 | 训练家地区 | byte |
| 4 | 训练家引用 | short LE |
| 5 | 道具使用次数 | byte |
| 后续 | 阵营场地 flags、队伍数、宝可梦数和记录 | 后续段落，不能与队伍头混读 |

关都开场当前队伍头样例：`02 06 00 48 01 04`，共 **6 字节**。
`48 01` 为引用 328；最后的 `04` 独立存在，不能用地区 `00` 代替，也不能省略读取。
队伍头没有字符串；其他玩家队伍分支的名称沿用 UTF-16LE 双零终止字符串，本次未修改。

原始 Recaf JASM：`f.iv_1.LPt4(B)Lf/O8;` 的 W..AA 分支，在公共头之后
依次执行 `ByteBuffer.get()B`、`getShort()S`、`get()B`，最终调用 `f.MC.<init>(BBSBB)V`。
Java 参数顺序为阵营、地区、训练家引用、容量、道具使用次数。

客户端旧 Java 将地区局部变量重复作为最后参数，少消费一个字节；
额外的 `04` 被当成场地 flags，随后宝可梦记录错位，最终在 Boss 类型解析处报错。
服务端 `BattleTeamInfoCodec.TRAINER` 的写入顺序与原始 JASM 一致，保持不变。

## 修改边界

- 客户端仅修复已有 `f.iv_1.LPt4` 的 `case 2`：最后一个参数改成独立 `super.Rj.get()`。
  未在 `f` 包新增剧情业务或自定义 UI，也未修改 `pro.pokemmo2` 模块、Maven exclude 或运行配置。
- 服务端 `game.story.PalletStoryBattle` 先用现有编码器检查初始化包，再发布战斗引用。
  检查缓冲区总会释放；发送/初始化抛出异常时按对象身份清理角色、剧情和战斗池引用。
- 失败保留阶段 3 和已领取宝可梦，不标记开场完成、不重发奖励。
- `Session.send` 会记录编码异常但不向调用方抛出，因此增加的是发送前编码检查，
  **不是客户端解码成功确认**。现有协议没有在此链路确认客户端解码成功，不能宣称服务器能发现所有客户端错误。
- 未调整未受本故障影响的共享 Codec 或其他客户端解析分支。

客户端 `iv_1.java` 既有 751 行，本次缩减为 750 行。为遵守客户端本轮单类语义修复范围，
暂不拆整个原始协议类；后续可按基础头、队伍解析和 UI 安装分工逐项对照 JASM 拆分。
服务端本次修改的 Java 文件均低于 500 行。

## 文件与验证

- 客户端源码：`C:\Users\z3407\Desktop\28887-obf-project\src\main\java\f\iv_1.java`。
- 客户端日志：`C:\Users\z3407\Desktop\28887-obf-project\build\text\iv_1-battle-init-20260909-per-file-log.txt`。
- 服务端：[PalletStoryBattle.java](../server.game/src/main/java/org/pokemmo/gameserver/game/story/PalletStoryBattle.java)。
- 回归源码：[PalletStoryTest.java](../server.game/src/test/java/org/pokemmo/gameserver/game/story/PalletStoryTest.java)。

```powershell
./tools/check_battle_init_contract.ps1
./tools/check_story_contract.ps1
```

静态合同脚本通过 132 项断言、24 组内存字节样例，证明该字段的消费位置；
剧情脚本 76 项断言与 9 条路线检查通过。新增三个 JUnit 用例覆盖训练家头对齐、
编码检查失败不发布战斗、初始化异常释放锁且保留存档；仅核对源码，**未运行 JUnit**。
按用户约定未执行 Maven/Gradle 编译，未启动客户端/服务端，未连接数据库。

## 用户验收

需要重新编译并重启 **客户端和服务端**，只重启服务端不能修复旧客户端漏读。
重新登录已领取角色后，不重新选宝可梦、不重置存档；在研究所与劲敌交互或走向出口重试。
确认 `0x30` 不再出现该读取异常，战斗界面出现，战后正常移动且完成阶段落库。
同一日志中的 `ly_1` 控件重复挂载属于另一条 UI 调用链，已有用户修改，本次没有覆盖。
