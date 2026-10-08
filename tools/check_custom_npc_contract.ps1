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

$store = Read-Source ($base + 'game/npc/CustomNpcStore.java')
$catalog = Read-Source ($base + 'game/npc/CustomNpcCatalog.java')
$codec = Read-Source ($base + 'game/npc/CustomNpcCodec.java')
$definition = Read-Source ($base + 'game/npc/CustomNpcDefinition.java')
$spawn = Read-Source ($base + 'game/entity/NpcSpawnService.java')
$scripts = Read-Source ($base + 'script/ScriptManager.java')
Assert-That ($scripts.Contains('.resolve("npc").resolve("custom")')) '使用独立自定义 NPC 目录'
Assert-That ($scripts.IndexOf('new CustomNpcCatalog(') -lt $scripts.IndexOf('new ShopCatalog(')) '自定义 NPC 先于商店恢复'
Assert-That ($definition.Contains('FIRST_ENTITY_IDX = 100000')) '序号区间与原生 NPC 分离'
Assert-That (-not $definition.Contains('record CustomNpcDefinition(long')) '不将运行时 Object ID 当持久化主键'
Assert-That ($catalog.Contains('(long) definition.entityIdx() + 1')) '新序号避开包括已停用文件在内的保存序号'
Assert-That ($catalog.Contains('if (!definition.enabled()) continue')) '停用文件保留身份但不发布'
Assert-That ($catalog.Contains('map.getNpcEntityHashMap().containsKey(definition.npcName())')) '保护原地图同序号实体'
Assert-That ($spawn.IndexOf('Path file = persist.apply(npc)') -lt $spawn.IndexOf('map.addEntity(npc)')) '先保存后发布'
Assert-That ($spawn.Contains('return catalog.save(map, entityIdx, npc, appearance)')) '保存组件接入真实生成调用'
foreach ($guard in 'output.force(true)', 'StandardCopyOption.ATOMIC_MOVE', 'lockChannel.tryLock()',
    'Files.exists(target, LinkOption.NOFOLLOW_LINKS)', 'Files.isSymbolicLink(current)', 'startsWith(directory)',
    'current.toRealPath().startsWith(realRoot)', 'Files.deleteIfExists(temporary)') {
    Assert-That ($store.Contains($guard)) "持久化边界: $guard"
}
Assert-That ($store.Contains('if (expected == null)') -and
    $store.Contains('if (!current.equals(expected))') -and
    $store.Contains('return write(expected.disabled(), expected)')) '新增不覆盖，停用仅允许比较已知内容后更新 enabled'
Assert-That ($catalog.Contains('new IdentityHashMap<>()') -and
    $catalog.Contains('liveDefinitions.get(npc)')) '停用检查实际自定义实体身份，而非仅凭高序号'
Assert-That ($codec.Contains('value.has(field)') -and $codec.Contains('intValueExact()') -and
    $codec.Contains('JsonToken.END_DOCUMENT')) '严格字段、整数及尾部校验'
foreach ($source in $store, $catalog, $codec, $definition) {
    Assert-That ($source -notmatch 'org\.jooq|org\.pokemmo\.db|protocol\.packets|ProcessBuilder') '持久化模块不越界到 SQL/发包/启动进程'
    Assert-That (($source -split '\r?\n').Count -le 500) '每个 Java 文件不超过 500 行'
    Assert-That ($source -notmatch '(?m)^import .*\*;') '无通配导入'
}

$fields = @('version','enabled','map','regionId','entityIdx','spriteId','spriteRegion',
    'movementType','leashX','leashY','x','y','z','toward')
$appearanceFields = @('eventId','sparkles','spriteScale')
$directory = Join-Path $Root 'resource/npc/custom'
$files = @(Get-ChildItem -LiteralPath $directory -Recurse -File -Filter '*.jsonc')
$maps = @(Get-ChildItem -LiteralPath (Join-Path $Root 'resource/map') -Recurse -File -Filter '*.json')
Assert-That ($files.Count -le 10000) '最多 10000 个自定义 NPC 文件'
$seen = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
foreach ($file in $files) {
    Assert-That ($file.Length -le 16384) '每个自定义 NPC 配置不超过 16 KiB'
    $npc = Get-Content -LiteralPath $file.FullName -Raw | ConvertFrom-Json -AsHashtable
    Assert-That ($npc.version -in 1,2) '自定义 NPC 版本必须为 1 或 2'
    $expectedFields = if ($npc.version -eq 2) { $fields + $appearanceFields } else { $fields }
    Assert-That ($npc.Count -eq $expectedFields.Count) '自定义 NPC 字段数量与版本一致'
    foreach ($field in $expectedFields) { Assert-That ($npc.ContainsKey($field)) "必填字段: $field" }
    Assert-That ($npc.enabled -is [bool] -and $npc.map -is [string] -and
        $npc.map -cmatch '^[A-Za-z0-9][A-Za-z0-9_-]{0,127}$') '启用开关和地图名格式'
    foreach ($field in $fields | Where-Object { $_ -notin 'enabled','map' }) {
        Assert-That (($npc[$field] -is [int] -or $npc[$field] -is [long]) -and
            $npc[$field] -ge [int]::MinValue -and $npc[$field] -le [int]::MaxValue) "$field 必须是整数"
    }
    Assert-That ($npc.regionId -in 0,1,3 -and $npc.entityIdx -ge 100000) '地区和自定义序号'
    if ($npc.version -eq 2) {
        Assert-That (($npc.eventId -is [long] -or $npc.eventId -is [int]) -and
            $npc.eventId -ge -1 -and $npc.eventId -le 6 -and $npc.sparkles -is [bool]) '事件分类和闪光开关'
        Assert-That (($npc.spriteScale -is [double] -or $npc.spriteScale -is [long] -or $npc.spriteScale -is [int]) -and
            $npc.spriteScale -ge 0.25 -and $npc.spriteScale -le 4.0) 'NPC 缩放范围'
    }
    Assert-That ($npc.spriteId -ge 0 -and $npc.spriteId -le 10000 -and $npc.spriteRegion -in 0,1,2,3,4,10) '外观范围'
    Assert-That ($npc.leashX -ge 0 -and $npc.leashX -le 4 -and $npc.leashY -ge 0 -and $npc.leashY -le 4) '活动范围'
    Assert-That ($npc.x -ge 0 -and $npc.x -le 32767 -and $npc.y -ge 0 -and $npc.y -le 32767 -and
        $npc.z -ge -128 -and $npc.z -le 127 -and $npc.toward -in 0,1,2,3) '坐标与朝向范围'
    $movement = if ($npc.regionId -in 0,1) { @(0,1,2,3,6,8,13,14,15,16,17,18,19,20,21,22,23,24) } else { @(0,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20) }
    Assert-That ($npc.movementType -in $movement) '当前地区移动类型'
    $regionName = @{0='kanto';1='hoenn';3='sinnoh'}[[int]$npc.regionId]
    $expected = Join-Path $directory "$regionName/$($npc.map)/npc_$($npc.entityIdx).jsonc"
    Assert-That ($file.FullName -eq [IO.Path]::GetFullPath($expected)) '路径与地区/地图/序号一致'
    Assert-That ($seen.Add("$($npc.regionId)/$($npc.map)/$($npc.entityIdx)")) '不重复固定身份'
    $candidates = @($maps | Where-Object { $_.BaseName -ceq $npc.map } | ForEach-Object {
        $map = Get-Content -LiteralPath $_.FullName -Raw | ConvertFrom-Json -AsHashtable
        if ($map.regionId -eq $npc.regionId) { $map }
    })
    Assert-That ($candidates.Count -eq 1) '地图资源存在且唯一'
    Assert-That (@($candidates[0].npcs | Where-Object { $_.entityIdx -eq $npc.entityIdx }).Count -eq 0) '不与原生 NPC 序号冲突'
}

Write-Output "自定义 NPC 持久化静态检查通过: $script:Checks 项断言，$($files.Count) 个已保存 NPC 文件。"
Write-Output '只读检查，不生成 NPC、不写配置、不编译 Java、不运行 JUnit；原子保存、重启恢复与地形占位仍需运行验证。'
