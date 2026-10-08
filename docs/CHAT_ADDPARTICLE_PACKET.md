# `addparticle` 宝可梦粒子命令

## 输入格式

该命令通过 `ChatType.NORMAL` 聊天发送：

```text
//addparticle <party slot 0-5> <particle id 0-38>
```

例如：

```text
//addparticle 1 0
```

参数 `1` 是 PARTY 数组槽位，表示界面中从 1 开始计数的第 2 只宝可梦；参数 `0` 是粒子 ID。

## 客户端语义

客户端 `f.ng_2.Fh0(S, QL)` 使用 `QL.D7` 构造单个 `//addparticle` 命令；`f.ng_2.Qe(S)` 遍历 `QL.j90` 并逐个发送同样的命令，对应调试菜单中的 `<ADD ALL>`。`QL.j90` 只包含非负 ID，当前客户端范围为 `0..38`。

该命令表示把粒子追加到宝可梦已解锁的粒子列表，不会修改 `current_select_particle_effect_type`。重复添加同一 ID 是幂等操作，不会在数组中制造重复值。服务端对客户端新增但名称尚未确认的 `34..38` 使用 `UNKNOWN_34..UNKNOWN_38` 标记。

## 状态同步

处理顺序如下：

```text
校验 PARTY 槽位和粒子 ID
  -> 使用 pokemon.id + trainer_id 更新 pokemon.particle_effects
  -> 更新在线 PokemonData.particleEffects
  -> 发送 0x16 bit 4194304 粒子列表增量
  -> 发送完整 PARTY 容器刷新
```

该命令只追加粒子，不提供删除或选择粒子的行为。

## 客户端证据

结论来自客户端项目 `28887-obf-project` 和 Recaf 中 `28887-renamed.jar` 的只读 JASM：

- `f.ng_2.Fh0(S, QL)` 发送单个 `QL.D7`；
- `f.ng_2.Qe(S)` 遍历 `QL.j90` 实现 `<ADD ALL>`；
- `f.QL` 定义当前非负粒子 ID `0..38`。

没有导出或反编译到临时目录。
