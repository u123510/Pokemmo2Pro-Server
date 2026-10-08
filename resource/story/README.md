# 新剧情资源

当前按章分文件：

- [kanto/pallet_town/opening.jsonc](kanto/pallet_town/opening.jsonc)：大木拦路、选择初始宝可梦、首次劲敌战斗。
- [kanto/oak_parcel/chapter.jsonc](kanto/oak_parcel/chapter.jsonc)：常磐商店包裹、研究所交付、图鉴和 5 个精灵球。
- [kanto/viridian_city/catch_tutorial/chapter.jsonc](kanto/viridian_city/catch_tutorial/chapter.jsonc)：常磐市老人捕获教学。
- [kanto/pokemon_center_nurse/chapter.jsonc](kanto/pokemon_center_nurse/chapter.jsonc)：通用宝可梦中心护士治疗。
- [kanto/early_route_to_vermilion/chapter.jsonc](kanto/early_route_to_vermilion/chapter.jsonc)：尼比、月见山、华蓝、比尔、枯叶市、圣安奴号和马志士。
- [kanto/mainline_to_champion/chapter.jsonc](kanto/mainline_to_champion/chapter.jsonc)：幽灵塔、火箭队本部、西尔佛公司、野生原野区、宝可梦屋、后四枚徽章、常磐道馆、四天王和冠军主线骨架。
- `resource/trainer/KantoTrainer.jsonc`：由 FireRed 原版训练家脚本生成的 639 个队伍和 432 个地图 NPC 绑定。
- `resource/item/KantoItem.jsonc`：由 FireRed 原版 `finditem/giveitem` 脚本生成的 Kanto 道具拾取绑定。

旧 `resource/interact` 和 `resource/event` 已删除并停止加载。
原地图、商店与自定义 NPC 配置未删除。

各文件的 `enabled` 只控制对应章节；配置变更重启游戏服读取，Java 变更才需要重新编译。
`text` 和 `starters[].prompt` 引用现有客户端原版文本，数字是资源引用而非玩家进度。
`starters[].moves` 是四个招式槽；选择顺序固定为妙蛙种子、小火龙、杰尼龟，
不可通过调换顺序改变既有存档编码。`trainerModel` 是客户端劲敌训练家模型引用。

玩家进度由服务端写 PostgreSQL 的角色表，不写本目录：
`oak_lab_status` 保存检查点，`first_partner_status` 保存初始选择，`oak_parcel_status` 保存包裹状态。
开场完成为阶段 4；包裹交付为 5；图鉴和五球奖励完成为 6，旧阶段 6..9 不再领奖。
只改开场状态不会清空包裹状态，完整重玩还需由维护者把测试角色的包裹状态重置为 3。
已完成玩家不重播，领取与进度原子保存；妈妈治疗可重复。
手动改表不实时刷新在线进度：非战斗登录/重连才重新读库。重置后可在完整退出并提交修改后重新登录，
由 GM 使用 `//moveto 0 3 0 12 2` 到北出口前一格，再向上走触发；不要在进行中的战斗里改表。
包裹领取任务固定绑定 `ViridianCity_Mart` 的 `npc_0`，博士为研究所 `npc_3`。
更改店铺配置只改变商品绑定，不把剧情移给其他 NPC。领取物品和阶段在同一事务内保存，
已经携带包裹或已完成玩家仍可购物。此章奖励固定为 `5004 × 5`，与原生对白一致，不在 JSONC 随意改数量。
常磐捕获教学已接入服务端：完成大木章节后进入 `ViridianCity`，
地图加载确认后自动播放 `npc_3` 的教学对白并进入 5 级独角虫野生战斗；
服务端自动提交普通精灵球 `5004`，成功捕获后设置关都 `story_line_flag` 第 5 位。
尼比至枯叶的早期主线已由独立通用章节接入；`mainline_to_champion` 已接入从幽灵塔、
火箭队本部、Silph 公司、野生原野区、宝可梦屋、后四枚徽章到四天王和冠军的关键主线，
并包含化石复活、Safari 入场/计步、Silph 公司劲敌战和冠军前 Route 22 劲敌战。
普通训练家和一次性地图道具已经接入通用交互与个人事件标志存档；
四天王当前阶段也使用同一张角色事件标志表保存，重连后从 `stage_0..4` 恢复；
地图机关仍由当前地图碰撞、传送和事件能力提供，尚未声称所有机关演出与客户端效果已实机验证。

六个章节现在都使用通用 `id/version/triggers/actors/resources/nodes/text` 格式，
由 `game.story.StoryRuntime` 统一解释。数据库事务、战斗和队伍刷新仍通过有限动作
适配器调用已有领域服务，不把任意 Java 方法暴露给 JSONC。
早期主线使用关都 `story_line_flag[1]` 的 bit 5..14 记录关键节点；徽章 bit 0..2
分别对应灰色、蓝色和橙色徽章。

离线校验命令见 [`tools/story/README.md`](../../tools/story/README.md)。ROM 脚本
自动转换器尚未接入；后续转换结果应直接生成本目录同一格式。

完整用法、修改文件、协议与验证状态见 [真新镇剧情文档](../../docs/PALLET_TOWN_STORY.md)
、[包裹与图鉴剧情](../../docs/OAK_PARCEL_STORY.md) 及
[常磐市捕获教学](../../docs/VIRIDIAN_CATCH_TUTORIAL.md)。
