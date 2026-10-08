# 剧情工具与通用剧情格式

`validate_story.py` 是离线剧情校验工具。它不修改资源，也不参与游戏服启动，
用于在服务端加载前检查通用剧情 JSONC。

## 用法

在项目根目录执行：

```powershell
python tools/story/validate_story.py
```

指定文件：

```powershell
python tools/story/validate_story.py tools/story/examples/rival_intro.jsonc
```

只允许新通用格式：

```powershell
python tools/story/validate_story.py --generic-only tools/story/examples/rival_intro.jsonc
```

省略文件时会扫描 `resource/story/**/*.jsonc`。当前四章配置都应被识别为 `generic`；
如果出现 `legacy`，说明有旧章节配置尚未迁移。

## 当前通用格式

通用章节至少包含：

- `id`、`version`、`enabled`
- `triggers`
- `actors`
- `startNode`
- `nodes`
- `text`

节点通过 `next`、`YES_NO.yes/no` 和 `MULTI_CHOICE.options[].next` 连接。
动作使用有限的 `type` 集合，不能从 JSONC 调用任意 Java 方法、反射或 SQL。

服务端由 `StoryRuntime` 解释这些配置，校验器会检查：

- 顶层、触发器、节点和动作的字段白名单；
- 章节 ID、版本、文本引用和节点引用；
- NPC/坐标/地图就绪/登录触发器；
- 地图文件和 NPC `entityIdx` 是否存在；
- 对话角色、文本键、分支节点和选项数量；
- 道具数量、宝可梦等级等基础范围；
- 不可达节点；
- 战斗结束节点；
- 当前章节是否仍是 `legacy`。

目前仍未实现 ROM 指令自动转换器；ROM 解析工具的输出目标就是这个格式。
