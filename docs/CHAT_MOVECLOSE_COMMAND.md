# `//moveclose` 相邻格移动命令

客户端 `f.o40_0` 的 Teleport 调试菜单通过 `ChatType.NORMAL` 发送八方向命令：

```text
//moveclose <dx> <dy>
```

其中 `dx`、`dy` 必须分别为 `-1`、`0` 或 `1`，且不能同时为 `0`。例如客户端向上发送
`//moveclose 0 -1`，向右上发送 `//moveclose 1 -1`。

服务端在 `MoveCloseCommand` 中完成参数校验，并由角色移动服务检查角色已加载、没有战斗/交互/交易，同时拒绝已被其他玩家占用的格子。该命令有意忽略地图边界和瓦片可行走性，允许把坐标移动到地图边界之外。成功后更新在线 `PlayerEntity` 的坐标和朝向，发送 `SendSetEntityPosPacket` 给本人及当前可见的其他玩家；该调试移动不跨地图、不触发普通移动事件或野外遭遇。
