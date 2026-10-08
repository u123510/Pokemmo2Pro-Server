# `//moveto` 地图坐标移动命令

客户端使用以下格式发送地图调试移动：

```text
//moveto <region> <mapHeader/group> <gbaMap> <x> <y>
```

例如 `//moveto 0 3 0 11 9` 表示 Kanto 地区的地图 `(3,0)`，目标坐标为 `(11,9)`。服务端按资源中的地区和地图索引查找目标地图，更新角色的地图标识、坐标和目标地图起始 Z 值，然后复用 `handleReLoadMap` 发送完整地图包，并同步旧地图的移除和新地图的可见玩家加载。

命令要求角色已加载，且不在战斗、交互或交易中；未知地区、未知地图和超出 Java `short` 坐标范围的参数会被拒绝。

神奥 NDS 客户端使用 `//moveto2`，第二个参数是打包后的 bank/map：

```text
//moveto2 <region> <packedBankMap> <x> <y> <z> <ng|true|false>
```

其中 `packedBankMap = (bank << 8) | mapId`。例如 `bank=1`、`mapId=155`
会变成 `411`：

```text
//moveto2 3 411 116 886 0 false
```

服务端内部仍按 `bank=1`、`mapId=155` 查找地图；发送给 NDS 客户端时，
协议要求先写 `mapId`、再写 `bank`，客户端会按 little-endian 组合回地图头
`0x019B`（十进制 `411`）。GBA 地区的两个地图字节顺序不变。
