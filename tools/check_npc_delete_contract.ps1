param([string]$Root = (Split-Path -Parent $PSScriptRoot))

$ErrorActionPreference = 'Stop'
$Root = [IO.Path]::GetFullPath($Root)
$script:Checks = 0
$base = 'server.game/src/main/java/org/pokemmo/gameserver/'

function Assert-That([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw "检查失败: $Message" }
    $script:Checks++
}

function Read-Source([string]$Path) {
    Get-Content -LiteralPath (Join-Path $Root $Path) -Raw
}

$command = Read-Source ($base + 'command/commands/EventDeleteNpcCommand.java')
$service = Read-Source ($base + 'game/entity/NpcDeleteService.java')
$visibility = Read-Source ($base + 'game/entity/NpcVisibilityService.java')
$store = Read-Source ($base + 'game/npc/CustomNpcStore.java')
$custom = Read-Source ($base + 'game/npc/CustomNpcCatalog.java')
$module = Read-Source ($base + 'command/GameCommandModule.java')
$protocol = Read-Source ($base + 'protocol/GameProtocol.java')
$shops = Read-Source ($base + 'game/shop/ShopCatalog.java')
$sessions = Read-Source ($base + 'game/shop/ShopSessions.java')
$map = Read-Source ($base + 'game/map/MapData.java')
Assert-That (([regex]::Matches($module, '\.to\(EventDeleteNpcCommand\.class\)')).Count -eq 1) '删除命令注册一次'
Assert-That ($command.Contains('return "eventdeletenpc"') -and
    $command.Contains('arguments.length != 1') -and $command.Contains('Long.parseLong(arguments[0])')) '精确命令名与单个 64 位参数'
Assert-That ((Read-Source ($base + 'command/Command.java')).Contains('return PermissionType.GM') -and
    -not $command.Contains('getRequiredPermission()')) '继承默认 GM 权限'
foreach ($guard in 'npcId <= 0', 'session.isActive()', 'manager.getCharacterSession() != session',
    'manager.getBattleManager() != null', 'TradeManager.isInTrade(manager)', 'InteractType.NONE',
    'loading.isCompletedExceptionally()', 'maps[0].equals(', 'map.getNpcEntityByGameId(npcId)') {
    Assert-That ($service.Contains($guard)) "删除边界: $guard"
}
Assert-That ($service.IndexOf('CustomNpcCatalog.Disabled saved = persist.apply(npc)') -lt
    $service.IndexOf('npc.setLoad(false)')) '停用落盘先于在线状态修改'
Assert-That ($service.IndexOf('npc.setCanInteract(false)') -lt $service.IndexOf('map.removeEntity(npc)')) '旧引用先停止交互'
Assert-That ($custom.Contains('liveDefinitions.get(npc)') -and
    $custom.Contains('map.getNpcEntityHashMap().get(definition.npcName()) != npc')) '只允许实际受目录管理的自定义实体'
Assert-That ($store.Contains('if (!current.equals(expected))') -and
    $store.Contains('if (current.equals(definition)) return target')) '外部变更保护与已提交停用的幂等重试'
Assert-That ($store.Contains('return write(expected.disabled(), expected)') -and
    $store.Contains('StandardCopyOption.REPLACE_EXISTING') -and $store.Contains('output.force(true)')) '停用原子替换已核对的 JSONC'
Assert-That ($store -notmatch 'Files\.delete(?:IfExists)?\(target\)') '不物理删除配置文件或释放固定序号'
Assert-That ($custom.Contains('definitions.put(definition.key(), definition.disabled())')) '内存目录保留停用定义'
Assert-That ($service.Contains('shops.withNpcMutation(mutation)')) '删除与商店事务互斥'
Assert-That ($shops -match 'withNpcMutation[\s\S]*?lock\.writeLock\(\)\.lock\(\)[\s\S]*?finally[\s\S]*?lock\.writeLock\(\)\.unlock\(\)') '写锁有明确释放路径'
Assert-That ($service.Contains('ShopSessions.closeForNpc(npcId)') -and
    $sessions.Contains('quote.npcId != npcId') -and $sessions.Contains('get(session) == quote')) '仅关闭该 NPC 的匹配报价'
Assert-That ($map.Contains('npcEntityHashMap.values()') -and
    $map.Contains('npcEntityHashMap.get(expected.getNpcName()) != expected')) '查找使用稳定快照且移除核对对象身份'
Assert-That ($visibility.Contains('public static void sendMapSnapshot') -and
    $visibility.Contains('synchronized (map)')) 'NPC 快照发送与移除串行化'
foreach ($file in 'game/character/CharacterWorldLoader.java', 'game/character/CharacterMovementService.java') {
    $text = Read-Source ($base + $file)
    Assert-That ($text.Contains('NpcVisibilityService.sendMapSnapshot')) "$file 使用受保护的 NPC 快照发送"
    Assert-That (-not $text.Contains('new SendAddGameEntityPacket(')) "$file 无绕过删除锁的 NPC 添加"
}
Assert-That ($service.Contains('viewer.send(new SendRemoveEntityPacket(npcId))')) '客户端实体移除通知'
Assert-That ($protocol.Contains('DataFlow.SERVER_TO_CLIENT, (byte) 0x08, SendRemoveEntityPacket.class')) '复用已注册的 S2C 0x08'
Assert-That ((Read-Source ($base + 'protocol/packets/s2c/SendRemoveEntityPacket.java')).Contains('buffer.writeLongLE(characterId)')) '移除字段是 long LE'

$text = '//eventdeletenpc 553459519488'
$capture = [byte[]](@(0x08, 0x00) + [Text.Encoding]::Unicode.GetBytes("$text`0"))
Assert-That ($capture.Length -eq 62) '用户抓包共 62 字节'
Assert-That ([Text.Encoding]::Unicode.GetString($capture, 2, 58) -ceq $text) 'NORMAL UTF-16LE 文本匹配'
Assert-That ([long]553459519488 -gt [int]::MaxValue) '用户 Object ID 不能用 int 解析'

foreach ($name in 'command/commands/EventDeleteNpcCommand.java','game/entity/NpcDeleteService.java',
    'game/entity/NpcVisibilityService.java','game/npc/CustomNpcStore.java','game/npc/CustomNpcCatalog.java',
    'game/npc/CustomNpcDefinition.java','game/map/MapData.java','game/entity/NpcEntity.java',
    'game/shop/ShopCatalog.java','game/shop/ShopSessions.java','game/entity/NpcSpawnService.java',
    'game/character/CharacterWorldLoader.java','game/character/CharacterMovementService.java') {
    Assert-That (((Read-Source ($base + $name)) -split '\r?\n').Count -le 500) "$name 行数不超过 500"
}
foreach ($source in $command,$service,$visibility) {
    Assert-That ($source -notmatch '(?m)^import .*\*;') '新增类无通配导入'
    Assert-That ($source -notmatch 'org\.jooq|org\.pokemmo\.db|Files\.|FileWriter|ProcessBuilder') '新增删除业务不直接访问 SQL/文件或启动进程'
}
Assert-That (Test-Path -LiteralPath (Join-Path $Root 'docs/CHAT_EVENTDELETENPC_COMMAND.md')) '删除命令文档存在'
Write-Output "eventdeletenpc 静态检查通过: $script:Checks 项断言。"
Write-Output '未编译或执行 JUnit/客户端/服务器；脚本不删除 NPC，也不改写任何已有 NPC 配置。'
