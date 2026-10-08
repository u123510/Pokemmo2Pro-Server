# 宝可梦重命名封包

## 1. 协议概览

客户端在宝可梦确认窗口中发送 C2S `0x0E`，用于修改一只 PARTY 或 PC 宝可梦的昵称。客户端参考实现为 `f.j4_0`，构造函数使用 `super(14)`，字段顺序为 Object ID 和 UTF-16LE 昵称。

| 偏移 | 长度 | 字段 | 编码 |
| ---: | ---: | --- | --- |
| `0` | `8` | 宝可梦 Object ID | `long LE`，必须大于 0 |
| `8` | 可变 | 新昵称 | UTF-16LE 字符序列，以 `0x0000` 终止 |

客户端输入框限制昵称最多 16 个字符，并只接受 ASCII 字母/数字、空格、`,.!?"'-~` 和 Latin-1 重音字符。空昵称允许恢复种族默认名称。

## 2. 服务端处理

```text
RenamePokemonPacket.decode
  -> 校验长度、Object ID、终止符、昵称字符和尾部数据
  -> 找到当前角色所属 PARTY/PC 宝可梦
  -> PokemonService / PokemonAttributeService 按 pokemon.id + trainer_id 更新 name
  -> 更新在线 partyPokemons
  -> 发送 S2C 0x14 SendAddPokemonPacket
```

交易期间或已报价的宝可梦不能重命名；角色上下文缺失、目标不存在或数据库更新失败时只记录日志，不发送伪造成功包。业务处理异常会被捕获并保留 Session；若封包本身截断、缺少终止符或含非法字节，则按统一协议解码器的错误策略处理。

S2C `0x14` 使用完整 `PokemonCodec` 记录。客户端会按 Object ID 覆盖已有容器条目，因此不会替换已打开 PC 窗口使用的 `0x13` 容器控制器。

## 3. 抓包示例

将昵称改为 `a`：

```text
0e 00 10 42 6a bb 01 00 00 61 00 00 00
```

其中 `00 10 42 6a bb 01 00 00` 是小端 Object ID，`61 00 00 00` 是 UTF-16LE 的 `a` 和终止符。

## 4. 验证状态

- 已由客户端 `f.j4_0` 和 `f.as0_0` 源码确认 opcode、字段顺序和 16 字符限制。
- 已静态确认 `GameProtocol` 注册 C2S `0x0E`，数据库更新带角色所有权条件，S2C `0x14` 会按 Object ID 更新客户端容器。
- 未启动服务器或连接真实客户端进行运行时验证。
