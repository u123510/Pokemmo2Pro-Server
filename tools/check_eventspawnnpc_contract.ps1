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

$command = Read-Source ($base + 'command/commands/EventSpawnNpcCommand.java')
$parser = Read-Source ($base + 'game/entity/EventNpcSpawnRequest.java')
$appearance = Read-Source ($base + 'game/entity/NpcSpawnAppearance.java')
$module = Read-Source ($base + 'command/GameCommandModule.java')
$spawn = Read-Source ($base + 'game/entity/NpcSpawnService.java')
$catalog = Read-Source ($base + 'game/npc/CustomNpcCatalog.java')
$definition = Read-Source ($base + 'game/npc/CustomNpcDefinition.java')
$codec = Read-Source ($base + 'game/npc/CustomNpcCodec.java')
Assert-That (([regex]::Matches($module,'\.to\(EventSpawnNpcCommand\.class\)')).Count -eq 1) '事件命令单独注册一次'
Assert-That ($command.Contains('return "eventspawnnpc"') -and $command.Contains('EventNpcSpawnRequest.parse(arguments)')) '注册名及解析入口'
Assert-That ((Read-Source ($base + 'command/Command.java')).Contains('return PermissionType.GM') -and
    -not $command.Contains('getRequiredPermission()')) '默认 GM 权限'
Assert-That ($parser.Contains('BASE_ARGUMENTS = 14') -and $parser.Contains('MAX_CONDITIONS = 64') -and
    $parser.Contains('args.length != BASE_ARGUMENTS + count * 2')) '长度和条件数量校验'
$argumentNames = @('事件编号','外观编号','外观地区','移动类型','横向范围','纵向范围','脚本偏移','标志编号','标志值')
for ($i = 0; $i -lt $argumentNames.Count; $i++) {
    Assert-That ($parser.Contains("integer(args[$i], `"$($argumentNames[$i])`")")) "参数 $i 的含义不移位"
}
foreach ($guard in 'bool(args[9], "更新已有 NPC")','bool(args[10], "闪光")',
    'bool(args[11], "忽略重复检查")','Float.parseFloat(args[12])','integer(args[13], "条件数量")',
    'flagId != -1 || flagValue != -1','if (updateExisting)','if (ignoreDuplicates)','if (count != 0)') {
    Assert-That ($parser.Contains($guard)) "参数与未支持行为边界: $guard"
}
Assert-That (-not $parser.Contains('Boolean.parseBoolean')) '非法布尔值不静默转成 false'
Assert-That ($appearance.Contains('Float.isFinite(spriteScale)') -and
    $appearance.Contains('spriteScale < 0.25f') -and $appearance.Contains('spriteScale > 4.0f')) '缩放非有限值与边界'
Assert-That ($appearance.Contains('npc.setUnk6(sparkles)') -and
    $appearance.Contains('npc.setIsSpriteScaleOverride(spriteScale != 1.0f, spriteScale)')) '使用既有原生视觉字段'
Assert-That ($spawn.Contains('request, NpcSpawnAppearance.DEFAULT') -and
    $spawn.Contains('appearance.applyTo(npc)') -and $spawn.Contains('catalog.save(map, entityIdx, npc, appearance)')) '普通默认外观及事件外观进入同一保存链'
Assert-That ($catalog.Contains('CustomNpcDefinition.from(map, entityIdx, npc, appearance)')) '事件分类与外观实际落盘'
Assert-That ($definition.Contains('version != 1 && version != 2') -and
    $codec.Contains('if (definition.version() == 1) APPEARANCE_FIELDS.forEach(value::remove)')) '版本 1 保持旧字段格式'
Assert-That ($codec.Contains('version == 2 && !value.keySet().containsAll(APPEARANCE_FIELDS)')) '版本 2 必须完整包含外观字段'
Assert-That ($definition.Contains('new NpcSpawnAppearance(eventId, sparkles, spriteScale).applyTo(npc)')) '重启恢复真实外观'
Assert-That ($definition.Contains('movementType, leashX, leashY, x, y, z, toward, eventId, sparkles, spriteScale)')) '停用保留扩展字段'

$text = '//eventspawnnpc 0 248 10 0 0 0 0 -1 -1 false false false 1.0 0'
$capture = [byte[]](@(0x08,0x00) + [Text.Encoding]::Unicode.GetBytes("$text`0"))
Assert-That ($capture.Length -eq 128) '用户抓包为 128 字节'
Assert-That ([Text.Encoding]::Unicode.GetString($capture,2,124) -ceq $text) 'NORMAL UTF-16LE 命令文本'
Assert-That (($text.Split(' ').Count - 1) -eq 14) '捕获请求含 14 个参数'

foreach ($source in $command,$parser,$appearance,$spawn,$catalog,$definition,$codec) {
    Assert-That (($source -split '\r?\n').Count -le 500) 'Java 文件不超过 500 行'
    Assert-That ($source -notmatch '(?m)^import .*\*;') '没有通配导入'
}
Assert-That ($command.Contains('不会自动开启节日活动或按日期显隐')) '明确区分保存分类与开启节日活动'
Assert-That (Test-Path -LiteralPath (Join-Path $Root 'docs/CHAT_EVENTSPAWNNPC_COMMAND.md')) '事件生成文档存在'
Write-Output "eventspawnnpc 静态检查通过: $script:Checks 项断言。"
Write-Output '不编译、不启动服务器、不生成 NPC、不修改现有配置；运行回归仍需验证。'
