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
    Get-Content -LiteralPath (Join-Path $Root $Path) -Raw -Encoding UTF8
}

$command = Read-Source ($base + 'command/commands/HealCommand.java')
$module = Read-Source ($base + 'command/GameCommandModule.java')
$service = Read-Source ($base + 'services/pokemon/PokemonHealingService.java')
$facade = Read-Source ($base + 'services/GameServerService.java')
$dispatcher = Read-Source ($base + 'command/CommandDispatcher.java')
$docs = Read-Source 'docs/CHAT_HEAL_COMMAND.md'

Assert-That (([regex]::Matches($module, '\.to\(HealCommand\.class\)')).Count -eq 1) 'heal 命令单独注册一次'
Assert-That ($command.Contains('return "heal"') -and $command.Contains('arguments.length != 0')) 'heal 是无参数命令'
Assert-That (-not $command.Contains('getRequiredPermission()')) 'heal 使用默认 GM 权限'
Assert-That ($dispatcher.Contains('权限不足，无法使用 //') -and $command.Contains('PermissionType.GM.getType()')) '命令权限反馈和服务层权限校验为中文且双重存在'
Assert-That ($service.Contains('POKEMON.TRAINER_ID.eq(characterId)') -and
    $service.Contains('POKEMON.CONTAINER_ID.eq(PARTY_ID)')) '只锁定当前角色 PARTY'
Assert-That ($service.Contains('.forUpdate()') -and $service.Contains('transactionResult')) '整队恢复使用数据库事务和行锁'
Assert-That ($service.Contains('matchesPartyMember') -and $service.Contains('getTrainerId() != characterId')) '在线队伍与数据库队伍一致性校验'
Assert-That ($service.Contains('pokemon.setCurrentHp(pokemon.getMaxHp())') -and
    $service.Contains('getPokemonMoveMaxPp(slot)')) '恢复最大 HP 和含 PP Up 的最大 PP'
Assert-That ($service.Contains('(pokemon.getEggValue() & 1) != 0')) '蛋不会作为可恢复宝可梦写入'
Assert-That ($service.Contains('POKEMON.CURRENT_HP') -and $service.Contains('POKEMON.MOVES_PP')) '只写入 HP 和 PP 字段'
Assert-That ($facade.Contains('new PokemonHealingService(database)') -and
    $facade.Contains('PokemonHealingService.Result healParty')) 'GameServerService 暴露恢复门面'
Assert-That ($command.Contains('setIsReloadPokemonMove(true)') -and
    $command.Contains('setIsReloadPokemonCurrentHp(true)')) '成功后发送既有 0x16 HP/PP 刷新'
Assert-That ($command.Contains('manager.getBattleManager() != null') -and
    $command.Contains('TradeManager.isInTrade(manager)') -and
    $command.Contains('getInteractType() != InteractType.NONE')) '战斗交易交互期间拒绝'
Assert-That ($docs.Contains('//heal') -and $docs.Contains('GM 及以上') -and
    $docs.Contains('PokemonHealingService')) '中文命令文档存在并覆盖权限'

foreach ($source in $command, $service) {
    Assert-That (($source -split '\r?\n').Count -le 500 -and $source -notmatch '(?m)^import .*\*;') '新增 Java 文件规模和显式导入'
}

Write-Output "heal 静态检查通过: $script:Checks 项断言。"
Write-Output '只读源码/文档检查；未编译 Java、未运行 JUnit、未启动服务器或修改数据库。'
