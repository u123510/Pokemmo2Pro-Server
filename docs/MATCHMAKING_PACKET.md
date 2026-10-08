# 匹配赛与锦标赛协议规范 (Matchmaking & Tournament Packet)

本文档记录 OpenMMO 项目中客户端点击主菜单“匹配赛报名”与“锦标赛报名”涉及的通信封包与数据结构。

## 1. 协议概述

| Opcode | 方向 | 名称 | 客户端对应类 | 职责说明 |
| --- | --- | --- | --- | --- |
| `0x44` (68) | C2S | `PvpStatisticsPacket` | `f.sd_2` | 客户端请求打开 PvP 统计界面（无载荷） |
| `0x46` (70) | C2S | `OpenMatchmakingFramePacket` | `f.b4_0` | 客户端请求打开匹配赛或锦标赛界面 |
| `0x47` (71) | C2S | `CloseMatchmakingFramePacket` | `f.SA` | 客户端关闭匹配赛或锦标赛界面（无载荷） |
| `0x47` (71) | S2C | `SendMatchmakingFramePacket` | `f.el_1` | 服务端下发匹配赛/锦标赛界面初始化数据 |
| `0x48` (72) | C2S | `PvpQuenePacket` | `f.wl_0` | 客户端点击报名排队/锦标赛报名 |
| `0x49` (73) | C2S | `CancelPvpQueuePacket` | `f.a8_0` | 客户端取消匹配赛报名排队（无载荷） |
| `0x4A` (74) | C2S | `RequestPvpLeaderboardPacket` | `f.Jp0` | 客户端请求指定赛季和分级的排行榜数据 |
| `0x4A` (74) | S2C | `SendPvpInfoPacket` | `f.sa_1` | 下发个人 PvP 基本信息 |
| `0x4B` (75) | C2S | `RequestMatchmakingSpectateListPacket` | `f.ZI` | 客户端在匹配赛界面切换“观战”标签页，请求可观战对局列表 |
| `0x4C` (76) | C2S | `MatchmakingSpectateBattlePacket` | `f.Nx0` | 客户端在观战列表中点击进入观战指定对局 |
| `0x4C` (76) | S2C | `SendPvpLeaderboardPacket` | `f.wf0_0` | 下发指定赛季和分级的排行榜条目 |
| `0x4D` (77) | C2S | `RequestPvpTierStatisticsPacket` | `f.am0_0` | 客户端在 PvP 统计界面请求指定月份和分级的使用率数据 |
| `0x4E` (78) | S2C | `SendMatchmakingSpectateListPacket` | `f.o8_0` | 下发可观战的匹配赛对局列表 |
| `0x4F` (79) | C2S | `RequestPvpPokemonDetailPacket` | `f.Rr0` | 客户端请求指定宝可梦在 PvP 中的详细使用率数据 |
| `0x4F` (79) | S2C | `SendPokemonPvpLevelInfoPacket` | `f.ds0_0` | 下发全宝可梦分级定位与规则数据 |
| `0x5D` (93) | S2C | `SendPvpTierStatisticsPacket` | `f.IR` | 服务端下发指定分级的 PvP 统计使用率数据列表 |
| `0x60` (96) | S2C | `SendPvpStatisticsPacket` | `f.PE0` | 服务端下发 PvP 统计窗口配置（可选月份数与规则列表） |
| `0x75` (117) | C2S | `RequestTournamentListPacket` | `f.am_1` | 客户端请求锦标赛列表数据（当前或历史） |
| `0x75` (117) | S2C | `SendTournamentListPacket` | `f.xf0_0` | 服务端下发锦标赛列表数据 |
| `0x77` (119) | C2S | `RequestTournamentDetailPacket` | `f.bk_1` | 客户端请求指定锦标赛详情（8 字节 tournamentId） |

网络字节序统一遵循 PokeMMO 的 **小端序 (Little-Endian, LE)**。

---

## 2. 封包详细结构

### 2.1 C2S 0x46: OpenMatchmakingFramePacket
客户端菜单“匹配赛报名”（文本 5500）或“锦标赛报名”（文本 9150）触发。

- **字节流**：`46 [isTournament: 1B]`
- **抓包示例**：`46 00`（匹配赛报名）
- **字段说明**：
  - `isTournament` (1 字节布尔值)：`0` = 匹配赛 (`matchmaking`)，`1` = 锦标赛 (`tournament`)。

### 2.2 S2C 0x47: SendMatchmakingFramePacket
服务端向客户端下发匹配赛/锦标赛界面 (`f.Yl`, `matchmakingframe`) 的数据。

- **字段说明**：
  - `open` (1 字节布尔值)：`1` = 打开/更新界面，`0` = 关闭界面。
  - 若 `open == 1`：
    - `isTournament` (1 字节布尔值)：`0` = 匹配赛，`1` = 锦标赛。
    - **匹配赛分支 (`isTournament == 0`)**：
      - `seasonEndSeconds` (4 字节 int LE)：当前赛季结束的绝对时间戳（秒）。
      - `tierCount` (1 字节)：启用的对战分级数量（对应客户端枚举 `f.av_1`）：
        - `0`: OU 单打 (`TC`)
        - `1`: Ubers 单打 (`op`)
        - `2`: OU 双打 (`i40`)
        - `3`: Ubers 双打 (`oq0`)
        - `4`: UU 单打 (`UU_Level`)
        - `5`: UU 双打 (`CJ0`)
        - `6`: NU 单打 (`NU_Level`)
        - `7`: NU 双打 (`G3`)
        - `8`: 随机单打 (`Random_Level`)
        - `9`: 随机双打 (`pRn`)
      - 每个分级状态写入：
        - `tierCode` (1 字节 byte)
        - `isEnabled` (1 字节布尔值, 1 = 启用)
        - `vn` (8 字节 long LE, 0L)
        - `unused` (8 字节 long LE, 0L)
      - `playerStatsCount` (1 字节)：玩家的个人战绩数量（对应 `pz_2[]`）。
        - 每个分级战绩写入：
          - `rating` (2 字节 short LE, 初始 MMR，例如 1000)
          - `rankOrWins` (2 字节 short LE, 胜场或排位)
          - `tierCode` (1 字节 byte)
          - `rewardCount` (1 字节 byte, 阶梯奖励数 0)
          - `recordCount` (1 字节 byte, 记录数 0)
    - **锦标赛分支 (`isTournament == 1`)**：
      - `tournamentCount` (1 字节)：进行中的锦标赛列表数量（暂无填 0）。
    - `rulesCount` (1 字节)：分级规则/禁限表数量（暂无填 0）。

### 2.3 C2S 0x47: CloseMatchmakingFramePacket
客户端关闭匹配赛窗口时触发（`f.SA`）。
- **载荷**：0 字节。

### 2.4 C2S 0x49: CancelPvpQueuePacket
客户端取消匹配赛排队时触发（`f.a8_0`）。
- **载荷**：0 字节。

### 2.5 C2S 0x4A: RequestPvpLeaderboardPacket
客户端打开界面初始化完成后，自动发送此包请求当前选定赛季和分级的排行榜：
- **字节流**：`4A [seasonIndex: 1B] [tierCode: 1B]`
- **字段说明**：
  - `seasonIndex` (1 字节)：赛季索引（通常从 0 开始）。
  - `tierCode` (1 字节)：对战分级代号（`0`..`9`）。

### 2.6 S2C 0x4C: SendPvpLeaderboardPacket
服务端回复排行榜列表数据：
- **字段说明**：
  - `totalCount` (4 字节 int LE)：排行榜总条数（无数据填 0）。
  - `seasonIndex` (1 字节)：赛季索引。
  - `tierCode` (1 字节)：对战分级代码。
  - `entryCount` (2 字节 short LE)：本次下发的条目数（暂无数据填 0）。

### 2.7 C2S 0x4B: RequestMatchmakingSpectateListPacket
客户端在匹配赛窗口切换到“观战”标签页（文本 1127）或输入搜索词时触发（`f.ZI`）：
- **字节流**：`4B [pageIndex: 1B] [searchQuery: UTF-16LE 字符串 以 0x0000 结尾]`
- **抓包示例**：`4b 00 00 00`（请求第 0 页/分级，空搜索词）
- **字段说明**：
  - `pageIndex` (1 字节)：请求的页码或分级索引（对应客户端 `Yl.D10`）。
  - `searchQuery` (UTF-16LE)：玩家搜索关键词。

### 2.8 S2C 0x4E: SendMatchmakingSpectateListPacket
服务端向客户端返回可观战的对局列表数据（`f.o8_0`）：
- **字段说明**：
  - `totalEntries` (2 字节 short LE)：可观战对局总条目数（若无对局填 0）。
  - `pageIndex` (1 字节 byte)：当前页码/分级索引，必须与请求的 `pageIndex` 一致，否则客户端忽略丢弃。
  - `matchCount` (1 字节 byte)：本包所含对局条数（若无对局填 0）。
  - 若 `matchCount > 0`，对局条目依次为：
    - `matchId` (4 字节 int LE)
    - `tierCode` (1 字节 byte)
    - `width` (4 字节 int LE)
    - `order` (4 字节 int LE)
    - `playerCount` (1 字节 byte)
    - 玩家元数据 (`cd0_2[]`)

### 2.9 C2S 0x4C: MatchmakingSpectateBattlePacket
客户端在观战列表中点击进入观战指定对局（`f.Nx0`）：
- **字段说明**：
  - `isPlayerSpectate` (1 字节布尔值)：`1` = 按玩家 ID 观战，`0` = 按对局 matchId 观战。
  - 若 `isPlayerSpectate == 1`：
    - `targetPlayerId` (8 字节 long LE)
    - `subId` (2 字节 short LE)
  - 若 `isPlayerSpectate == 0`：
    - `matchId` (4 字节 int LE)

### 2.10 C2S 0x75: RequestTournamentListPacket
客户端打开锦标赛界面或切换当前/历史标签页时触发（`f.am_1`）：
- **字节流**：`75 [pageSequence: 1B] [isHistory: 1B] [pageIndex: 2B LE]`
- **抓包示例**：`75 01 00 00 00`（第 1 次请求，当前进行中的锦标赛，第 0 页）
- **字段说明**：
  - `pageSequence` (1 字节 byte)：请求序号（从 1 递增），服务端必须在响应中原样返回。
  - `isHistory` (1 字节布尔值)：`0` = 进行中/即将举行的锦标赛，`1` = 历史锦标赛。
  - `pageIndex` (2 字节 short LE)：页码（从 0 开始）。

### 2.11 S2C 0x75: SendTournamentListPacket
服务端向客户端下发锦标赛列表数据（`f.xf0_0`）：
- **字段说明**：
  - `pageSequence` (1 字节 byte)：原样返回客户端请求的序号。
  - `isHistory` (1 字节布尔值)：原样返回客户端请求的类型。
  - `currentPage` (2 字节 short LE)：当前页码。
  - `totalCount` (4 字节 int LE)：锦标赛总条目数（若无锦标赛填 0）。
  - `count` (1 字节 byte)：本次下发的锦标赛数量（当前填 0）。
  - 若 `count > 0`：下发具体锦标赛数据结构 (`zp0_0`)。

### 2.12 C2S 0x77: RequestTournamentDetailPacket
客户端在锦标赛界面点击指定锦标赛条目查看详情时触发（`f.bk_1`）：
- **字节流**：`77 [tournamentId: 8B LE]`
- **字段说明**：
  - `tournamentId` (8 字节 long LE)：目标锦标赛的唯一编号。

### 2.13 C2S 0x44: PvpStatisticsPacket
客户端点击主菜单“PvP 统计”（文本 5670）时触发（`f.sd_2`）：
- **字节流**：`44`
- **抓包示例**：`44`（0 字节载荷）
- **职责**：请求服务端下发 PvP 统计窗口的初始化配置。

### 2.14 S2C 0x60: SendPvpStatisticsPacket
服务端回复 PvP 统计窗口的初始化配置（`f.PE0`）：
- **字段说明**：
  - `monthCount` (1 字节 byte)：可选月份数量。必须 `>= 1`，客户端初始化 `f.kf0_2`（窗口名称 `matchmaking-stats-frame`）并在月份下拉列表中构建最近 `monthCount` 个月，默认选中第 0 项。
  - `rulesCount` (1 字节 byte)：禁限规则列表数量（`HZ[]`，无规则填 0）。

### 2.15 C2S 0x4D: RequestPvpTierStatisticsPacket
客户端在 PvP 统计界面切换分级标签页或更改月份时触发（`f.am0_0`）：
- **字节流**：`4D [month: 1B] [tierId: 1B] [category: 1B]`
- **字段说明**：
  - `month` (1 字节 byte)：目标月份（1..12）。
  - `tierId` (1 字节 byte)：分级代码（对应 `N2.yz`）。
  - `category` (1 字节 byte)：分类（对应 `Cq.WW`）。

### 2.16 S2C 0x5D: SendPvpTierStatisticsPacket
服务端向客户端下发指定分级的使用率统计列表（`f.IR`）：
- **字段说明**：
  - `hasData` (1 字节 byte)：`>= 1` 表示存在有效数据，`< 1` 表示无数据。
  - 若 `hasData >= 1`：
    - `month` (1 字节 byte)：月份。
    - `category` (1 字节 byte)：分类。
    - `tierId` (1 字节 byte)：分级。
    - `updatedTime` (4 字节 int LE)：数据更新绝对时间戳（秒）。
    - `cacheOffsetMs` (8 字节 long LE)：客户端本地缓存超时偏移量（毫秒，例如 3600000L 表示 1 小时）。
    - `totalBattles` (4 字节 int LE)：统计总场次数（0 则显示无对战记录）。
    - `count` (2 字节 short LE)：包含的宝可梦条目数。为 0 时客户端界面显示“暂无数据”（文本 5633）。

### 2.17 C2S 0x4F: RequestPvpPokemonDetailPacket
客户端在 PvP 统计界面点击具体宝可梦条目查看详细配招与特性分布时触发（`f.Rr0`）：
- **字节流**：`4F [month: 1B] [category: 1B] [tierId: 1B] [pokemonId: 2B LE]`
- **字段说明**：
  - `month` (1 字节 byte)：目标月份。
  - `category` (1 字节 byte)：分类。
  - `tierId` (1 字节 byte)：分级代码。
  - `pokemonId` (2 字节 short LE)：宝可梦编号。

---

## 3. 客户端实现参考

- 客户端窗口类：
  - 匹配赛窗口：`f.Yl.java`（窗口名称：`matchmakingframe`）
  - 锦标赛窗口：`f.cb0_1.java`（窗口名称：`mm-stats-window`）
  - PvP 统计窗口：`f.kf0_2.java`（窗口名称：`matchmaking-stats-frame`）
- 触发点：`f.BU.java`
- 封包解析类：
  - S2C 0x47: `f.el_1.java`
  - S2C 0x4C: `f.wf0_0.java`
  - S2C 0x4E: `f.o8_0.java`
  - S2C 0x5D: `f.IR.java`
  - S2C 0x60: `f.PE0.java`
  - S2C 0x75: `f.xf0_0.java`
  - C2S 0x44: `f.sd_2.java`
  - C2S 0x46: `f.b4_0.java`
  - C2S 0x47: `f.SA.java`
  - C2S 0x48: `f.wl_0.java`
  - C2S 0x49: `f.a8_0.java`
  - C2S 0x4A: `f.Jp0.java`
  - C2S 0x4B: `f.ZI.java`
  - C2S 0x4C: `f.Nx0.java`
  - C2S 0x4D: `f.am0_0.java`
  - C2S 0x4F: `f.Rr0.java`
  - C2S 0x75: `f.am_1.java`
  - C2S 0x77: `f.bk_1.java`

