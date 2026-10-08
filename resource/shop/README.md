# 商店配置：在一个 JSONC 里指定店员和商品

每家店一个 `.jsonc`，目录和文件名只用于分类。普通 NPC 也能成为店员，
不需要改地图中的 `script`，也不需要再给地图 NPC 添加 `shopId`。

## 示例

```jsonc
{
  "shopId": "kanto.cerulean_city.mart",
  "npcs": [
    { "map": "CeruleanCity_Mart", "entityIdx": 1 }
  ],
  "buyEnabled": true,
  "sellEnabled": true,
  "items": [
    { "itemId": 5004, "buyPrice": 200, "sellPrice": 100 }
  ]
}
```

- `map` 是地图文件名去掉 `.json`，大小写要一致。
- `entityIdx` 对应该地图 NPC 的序号。例如日志 `NPC名称=npc_1` 对应 `1`。
- `npcs` 指定谁经营；`items` 指定卖什么；`shopId` 是这份店铺配置的唯一名称。
- 上例仅作说明，不会自动添加华蓝市店铺文件或修改女性 NPC。
- `buyPrice` 是玩家购买单价，`sellPrice` 是玩家出售回收价；`null` 表示禁用该方向。

## 换店员、增加店员

只把 `entityIdx: 1` 改成 `entityIdx: 2`，即可把店铺转给同地图的 2 号 NPC。
两人都经营同一家店时：

```jsonc
"npcs": [
  { "map": "CeruleanCity_Mart", "entityIdx": 1 },
  { "map": "CeruleanCity_Mart", "entityIdx": 2 }
]
```

两人卖不同商品时，分别创建不同 `shopId` 的店铺文件，每个 NPC 只出现在一家的列表。
地图不存在、序号不存在或两店重复指定同一个 NPC 都会报中文错误，重载失败时保留旧配置。

## 生效与兼容

首次更新本功能，需要自行编译并重启游戏服；之后编辑店铺文件，GM 输入：

```text
//reloadshops
```

绑定和商品价格一起生效，旧窗口会关闭，再与目标 NPC 交互即可；不必重启客户端或重新登录。
`npcs` 存在时会覆盖本店旧地图 `shopId`，因此改序号就能换店员。
写 `"npcs": []` 可取消本店全部绑定；删除 `npcs` 字段则恢复旧地图绑定方式，并不是解绑。

这里只绑定已经加载的 NPC，不会生成新的 NPC 或改变外观、坐标、隐藏条件。
相邻交互不需要柜台；隔柜台仍需地图支持 `interactionCounters`。
新建 NPC 或修改地图、外观、柜台需重载地图或重启游戏服。

使用 `//spawnnpc` 生成的自定义 NPC 会即时生效，并保存到独立 `resource/npc/custom`。
商店可用命令返回的地图名与固定高序号（从 100000 起）绑定；重启先恢复 NPC 再恢复店铺，
不必把它写进原地图的 NPC 数组。详见 [独立 NPC 保存说明](../npc/custom/README.md)。

## 现有文件

- [常磐市商店](kanto/viridian_city/mart_clerk.jsonc)：`ViridianCity_Mart / 0`。
- [尼比市商店](kanto/pewter_city/mart_clerk.jsonc)：`PewterCity_Mart / 2`，不是 0。
- [详细协议与维护文档](../../docs/NPC_SHOP_PACKET.md)。

## 修改记录（2026-09-09）

新增 `game.shop.ShopNpcBinding` 和 `ShopNpcBindings`，修改 `ShopDefinition`、
`ShopConfigLoader`、`ShopCatalog`、`ShopService` 与 `ScriptManager`，
将绑定校验和查询集中在 `org.pokemmo.gameserver.game.shop`。
两个示例店铺改用 `npcs`；本次不修改地图、客户端、买卖封包、数据库或道具价格。
源码索引、包职责、模块、资源、协议及重载文档同步更新。

验证：已运行只读商店配置/源码静态检查；JUnit 用例已添加但未编译执行。
未启动服务器、未连接数据库，真实热重载、更换店员和买卖仍需运行验证。
