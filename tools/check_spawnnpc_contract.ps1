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

$command = Read-Source ($base + 'command/commands/SpawnNpcCommand.java')
$request = Read-Source ($base + 'game/entity/NpcSpawnRequest.java')
$service = Read-Source ($base + 'game/entity/NpcSpawnService.java')
$visibility = Read-Source ($base + 'game/entity/NpcVisibilityService.java')
$module = Read-Source ($base + 'command/GameCommandModule.java')
$protocol = Read-Source ($base + 'protocol/GameProtocol.java')
$packet = Read-Source ($base + 'protocol/packets/s2c/SendAddGameEntityPacket.java')
Assert-That (([regex]::Matches($module, '\.to\(SpawnNpcCommand\.class\)')).Count -eq 1) '命令恰好注册一次'
Assert-That ($command.Contains('return "spawnnpc"') -and $command.Contains('arguments.length != 6')) '命令名与六参数'
Assert-That ($command.Contains('Integer.parseInt(arguments[i])')) '不静默接受非整数或溢出参数'
Assert-That ((Read-Source ($base + 'command/Command.java')).Contains('return PermissionType.GM')) '默认 GM 权限'
Assert-That (-not $command.Contains('getRequiredPermission()')) '没有放宽默认 GM 权限'
Assert-That ($request.Contains('scriptOffset != 0')) '未接入的脚本偏移必须明确拒绝'
Assert-That ($request.Contains('spriteId > 10000') -and $request.Contains('leashX > 4') -and
    $request.Contains('leashY > 4')) '外观与活动范围上限'
foreach ($expected in 'Set.of(0, 1, 2, 3, 4, 10)',
    'Set.of(0, 1, 2, 3, 6, 8, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24)',
    'Set.of(0, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20)') {
    Assert-That ($request.Contains($expected)) '客户端普通移动值和外观地区一致'
}
foreach ($guard in 'session.isActive()', 'manager.getCharacterSession() != session',
    'MapLoadingOptions.areNpcsEnabled()', 'manager.getBattleManager() != null', 'TradeManager.isInTrade(manager)',
    'InteractType.NONE', 'loading.isCompletedExceptionally()', 'map.checkIsWalkable(x, y)',
    'synchronized (map)', 'MAX_MAP_NPCS', 'map.containsNpcEntity(id)') {
    Assert-That ($service.Contains($guard)) "生成校验: $guard"
}
Assert-That ($service.Contains('entityIdx < CustomNpcDefinition.FIRST_ENTITY_IDX') -and
    $service.Contains('map.getNpcEntityHashMap().containsKey("npc_" + entityIdx)')) '独立序号区间且不覆盖已有 NPC'
Assert-That ($service.IndexOf('Path file = persist.apply(npc)') -lt
    $service.IndexOf('map.addEntity(npc)')) '保存成功才发布 NPC'
Assert-That ($service.Contains('return catalog.save(map, entityIdx, npc, appearance)') -and
    $service.Contains('appearance.applyTo(npc)')) '完整应用外观并持久化，不是空回调'
Assert-That ($service.Contains('map.addEntity(npc)') -and
    $service.Contains('NpcVisibilityService.sendSpawn(viewer, map, result.entity())') -and
    $visibility.Contains('session.send(new SendAddGameEntityPacket(npc))')) '在线地图与客户端都更新'
Assert-That ($visibility.Contains('synchronized (map)') -and
    $visibility.Contains('map.getNpcEntityHashMap().get(npc.getNpcName()) == npc')) '已删除的生成回调不得重新添加客户端实体'
Assert-That ($protocol.Contains('DataFlow.SERVER_TO_CLIENT, (byte) 0x12, SendAddGameEntityPacket.class')) '复用已注册 S2C 0x12'

$position = -1
foreach ($field in 'getEntityGameId', 'getNpcModelRegionIndexId', 'getNpcModelIndexId',
    'getDefaultToward', 'getMoveMentType', 'getMovementLeashX', 'getMovementLeashY',
    'getRegionIndexId', 'MapProtocolIds.first', 'MapProtocolIds.second', 'getX()', 'getY()', 'getZ()', 'getToward()') {
    $next = $packet.IndexOf($field, $position + 1)
    Assert-That ($next -gt $position) "S2C NPC 字段顺序: $field"
    $position = $next
}

$captured = [byte[]](@(0x08, 0x00) + [Text.Encoding]::Unicode.GetBytes("//spawnnpc 0 0 0 0 0 0`0"))
Assert-That ($captured.Length -eq 48) '用户抓包总长度 48 字节'
Assert-That ([Text.Encoding]::Unicode.GetString($captured, 2, 44) -ceq '//spawnnpc 0 0 0 0 0 0') 'NORMAL UTF-16LE 命令内容'
Assert-That ($captured[-1] -eq 0 -and $captured[-2] -eq 0) '命令零终止符'

$mapSource = Read-Source ($base + 'game/map/MapData.java')
Assert-That ($mapSource.Contains('volatile HashMap<String, NpcEntity>') -and
    $mapSource.Contains('new HashMap<>(npcEntityHashMap)')) '新增 NPC 使用可见的复制快照'
foreach ($name in 'KantoregionMapData.java', 'NdsMapData.java') {
    $source = Read-Source ($base + "game/map/$name")
    Assert-That ($source.Contains('public synchronized void loadArroundEntity')) "$name 初始化串行化"
    Assert-That ($source -match 'if \((npcEntity|entity)\.getEntityGameId\(\) <= 0\)') "$name 保留已有 NPC 编号"
    Assert-That ($source.Contains('getSnowflakeIdGenerator().nextId()')) "$name 通过统一生成器分配编号"
}

$files = @('command/commands/SpawnNpcCommand.java', 'command/GameCommandModule.java',
    'game/entity/NpcSpawnRequest.java', 'game/entity/NpcSpawnService.java',
    'game/map/MapData.java', 'game/map/KantoregionMapData.java', 'game/map/NdsMapData.java')
foreach ($file in $files) {
    $source = Read-Source ($base + $file)
    Assert-That (($source -split '\r?\n').Count -le 500) "$file 不超过 500 行"
    Assert-That ($source -notmatch '(?m)^import .*\*;') "$file 无通配导入"
}
foreach ($source in $command, $request, $service) {
    Assert-That ($source -notmatch 'org\.jooq|org\.pokemmo\.db|Files\.write|FileWriter|ProcessBuilder') '命令/生成器不直接写磁盘或数据库，保存转交独立目录组件'
}
Assert-That (Test-Path -LiteralPath (Join-Path $Root 'docs/CHAT_SPAWNNPC_COMMAND.md')) '命令文档存在'
Write-Output "spawnnpc 静态检查通过: $script:Checks 项断言。"
Write-Output '仅检查源码、注册与协议合同；未编译 Java，未运行 JUnit、客户端或服务器。'
