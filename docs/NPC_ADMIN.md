# NPC / 商店网页管理工具

`tools/web/` 提供本地网页管理台，用浏览器替代手写 JSONC：
商店编辑、自定义 NPC 生成与停用、真实贴图地图浏览三合一。
纯 Python 标准库实现（与 `tools/account_admin.py` 同款），无新依赖。

## 启动

在项目根目录（必须，资源路径依赖根目录）：

```text
python tools/web/npc_admin.py
```

浏览器打开 `http://127.0.0.1:8081/`（可用环境变量 `NPC_ADMIN_PORT` 换端口）。
页面标题栏显示店铺 / 自定义 NPC / 地图数量，以及关都、丰缘反编译图源是否可用。

## 三个标签页

### 商店管理

- 左侧列出 `resource/shop` 全部店铺（shopId、文件、商品数、店员绑定）。
- 编辑：shopId、允许购买/出售、商品表（**点「道具」列即弹出搜索框**，按中文名、
  括号内英文名或编号搜索选择，无需记忆道具编号；价格留空 = null）。
- 店员绑定三种模式，与游戏服语义一致：
  - 不写 `npcs` 字段：沿用地图 NPC 上的旧 `shopId`；
  - 写空数组 `[]`：解绑本店全部店员；
  - 指定列表：从「地区 → 地图 → NPC 序号」下拉选择（含原生与自定义 NPC）。
- 「只校验」按钮显示服务端同款错误（未知字段、道具不存在、地图未加载、序号不存在、
  NPC 被两家店重复绑定、shopId 重复等），校验通过才允许保存。
- 保存成功后提示：**游戏内输入 `//reloadshops` 生效**；原文件自动备份到 `tools/web/backups/`。
- 「新建店铺」自动生成 shopId（目录.文件名规则），「删除店铺」把原文件移入备份目录，不物理删除。

### 自定义 NPC

- 左侧列出 `resource/npc/custom` 全部配置（按地图、序号、外观、坐标），停用的置灰。
- **地图可视化操作**（地图浏览标签页）：
  - 蓝框=原生 NPC、绿框=已启用自定义 NPC（旁边标序号），灰框=已停用；
  - **右键空白格** → 「在此新建自定义 NPC」，自动带出当前地图、点击格坐标和下一个可用序号；
  - **拖动绿框** → 自定义 NPC 直接移动到目标格，先过校验（越界/碰撞/重叠）再保存，
    校验不过会拒绝并提示原因；原生 NPC 不可拖动（原生地图只读）；
  - **右键绿框** → 编辑 / 停用 / 启用；右键任意格子 → 绑定为店员、用作出生点。
- 新建：地区（0 关都 / 1 丰缘 / 3 神奥）→ 地图 → 自动分配下一个可用序号
  （对齐 `CustomNpcCatalog.nextEntityIdx`，跳过原生占用）。
- 「在地图上选出生点」跳到地图浏览点一格，自动回填 X/Y。
- 校验与服务端逐条一致：外观 0..10000、外观地区 {0,1,2,3,4,10}、移动类型按地区白名单
  （关都/丰缘 GBA 集、神奥 NDS 集）、活动范围 0..4、朝向 0..3、Z −128..127、
  序号 ≥100000、版本 1 无事件字段 / 版本 2 必填 eventId、sparkles、spriteScale(0.25..4.0)。
- 出生格校验：越界、碰撞位、与原生/自定义 NPC 同层重叠（GBA 按 z/3 分层）、单图 1024、总量 10000。
  其中"可站立"按 blockData 碰撞位近似判断，服务端加载时仍会做最终校验。
- 「停用/启用」只改 `enabled`，不物理删文件（与 `//eventdeletenpc` 行为一致）。
- 保存后需**重启游戏服**生效；游戏服运行中请用 `//spawnnpc` / `//eventdeletenpc`。

### 地图浏览

- 关都、丰缘使用 pret 反编译工程（`pokefirered-src` / `pokeemerald-src`）的真实图块
  合成原版画面；图源缺失的个别地图回退为示意图（按图块编号上色、碰撞格压暗）。
  反编译工程路径默认取桌面，可用环境变量 `OPENMMO_FIRERED_ROOT` / `OPENMMO_EMERALD_ROOT` 覆盖。
- 覆盖率：关都 425/425 全部真实渲染；丰缘 494/518（24 张秘密基地地图使用运行时
  组装的 SecretBase 图块集，反编译工程无静态图形，回退示意图）；神奥图源未接入，回退示意图。
- 图块集目录按反编译工程 `src/data/tilesets/headers.h` 的结构体字段解析
  （tiles/palettes/metatiles 可分别指向共享目录，如 SilphCo 复用 Condominiums 图形），
  反编译工程若为稀疏检出需先检出 `data/tilesets`、`graphics` 相关目录。
- 标记层：蓝框原生 NPC（旁边标序号）、绿框自定义 NPC、紫点传送点、橙框柜台，
  可叠加碰撞蒙层；点格子显示坐标、碰撞、NPC 明细、传送目标。
- 点 NPC 可直接「绑定为店员」（回商店页自动加一行绑定）或「用作出生点」。
- 渲染结果缓存于 `tools/web/cache/maps/`，地图文件变更（mtime）后自动重渲。

## 数据与校验依据

| 工具行为 | 对齐的服务端类 |
| --- | --- |
| 商店字段/数量/类型 | `game.shop.ShopConfigLoader` |
| 店员绑定冲突检测 | `game.shop.ShopNpcBindings` |
| 自定义 NPC 字段与版本 | `game.npc.CustomNpcCodec` / `CustomNpcDefinition` |
| 序号分配/放置校验/上限 | `game.npc.CustomNpcCatalog` / `entity.NpcSpawnRequest` / `NpcSpawnAppearance` |
| 文件路径/原子写/目录上限 | `game.npc.CustomNpcStore` |
| 地图块解码（tileId/碰撞） | `game.map.OpenMmoMapDataReader` |
| 真实渲染规则 | ROM `LoadTilesetPalette` / `fieldmap.h`（主副 640/1024、调色板 0-6 主 7-12 副） |

工具校验通过 ≠ 服务端必然加载成功：道具的账号绑定/关键道具/可交易性、出生格可站立性
等仍由游戏服在加载或生成时最终校验；工具会在错误信息里注明。

## 自测

```text
python tools/web/selftest.py
```

覆盖 JSONC 注释往返、地图索引、商店/自定义 NPC 校验、沙箱写回（临时目录，不触碰
真实资源）与地图渲染缓存。25 项全部通过为正常。

## 已知边界（未验证项）

- 神奥地图由 OpenMMO-DS 管线生成，图源未接入，走示意图。
- 丰缘 24 张秘密基地地图与个别引用未落地动画图块的格子会回退示意图/留黑块。
- NPC 立绘（外观编号 → 头像）未接入，地图上以编号标注；可后续从客户端 ROM 提取。
- 道具的堆叠/绑定/可交易校验依赖 `ItemManager`（含 Item.bin 数据），工具只做
  `Item.jsonc` 存在性检查；被服务端拒绝的配置会在 `//reloadshops` 日志中给出中文原因。
- 道具不显示图片图标（用户明确只要搜索）。若将来要接：客户端 `BwTrainerModelLoader.CR()`
  从心金 `.nds` 的 `/a/0/2/5`（item_icon NARC）读取 5001..5626 的图标块/调色板索引；
  PokéMMO 自定义道具（region 10，iconId 直接命中）在客户端
  `data/resources.zip` 的 `sprites/itemicons/<iconId>.png`，共 332 张；
  火红 ROM 的道具图标表位于 0x3D4304（376 行 × [tiles, palette] 指针对，tiles 为
  24x24 4bpp 的 ROM 级共享窗口 LZ77，标准 BIOS LZ77 解不开，属私服私有变体）。
