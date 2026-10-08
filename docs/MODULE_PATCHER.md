# `patcher` 客户端补丁模块

## 1. 作用

`patcher` 是 Java Agent，通过 `Premain-Class` 在客户端启动时注入 `ClassFileTransformer`，把客户端内嵌的旧证书字符串替换成项目资源中的新证书。

## 2. 工作流程

```text
PokeMMO JVM 启动
  -> PatcherAgent.premain
  -> 从资源加载 game.public / chat.public
  -> 注册两个 StringTransformer
  -> ASM 读取 class bytecode
  -> StringClassVisitor / StringMethodVisitor 替换常量
```

当前替换两组证书：游戏服务器和聊天服务器。资源路径为 `/game.public` 与 `/chat.public`，构建时必须确保它们存在于 `patcher/src/main/resources`。
缺少任一证书资源时，`PatcherAgent` 会抛出 `IOException`，阻止客户端在缺失补丁的状态下继续启动。

## 3. 构建

`shadowJar` 任务生成无版本名的 `patcher.jar`，manifest 写入 `Premain-Class: org.patcher.PatcherAgent`。启动客户端时通过 `-javaagent:<path-to-patcher.jar>` 加载。

## 4. 风险

- `StringTransformer` 默认对每个 class 都创建 ASM reader/writer，性能和兼容性取决于客户端字节码。
- 证书字符串变化时必须同步更新 `PatcherAgent` 中的旧证书值。
- Patcher 是客户端版本相关的，客户端升级后需要重新验证替换目标。
