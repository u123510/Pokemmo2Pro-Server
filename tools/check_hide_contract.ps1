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

$command = Read-Source ($base + 'command/commands/HideCommand.java')
$module = Read-Source ($base + 'command/GameCommandModule.java')
$visibility = Read-Source ($base + 'game/character/PlayerVisibilityService.java')
$state = Read-Source ($base + 'game/character/PlayerVisibilityState.java')
$manager = Read-Source ($base + 'game/character/CharacterManagerState.java')
$maps = Read-Source ($base + 'game/character/MapVisibilityService.java')
$wild = Read-Source ($base + 'game/character/WildEncounterService.java')
Assert-That (([regex]::Matches($module,'\.to\(HideCommand\.class\)')).Count -eq 1) 'hide 命令单独注册一次'
Assert-That ($command.Contains('return "hide"') -and $command.Contains('arguments.length != 0')) '无参数切换命令'
Assert-That ((Read-Source ($base + 'command/Command.java')).Contains('return PermissionType.GM') -and
    -not $command.Contains('getRequiredPermission()')) '默认 GM 权限'
Assert-That ($visibility.Contains('permission.getType() >= PermissionType.GM.getType()')) '仅 GM 及以上管理员可看见隐身角色'
Assert-That ($visibility.Contains('return self || !hidden || isAdministrator(viewerPermission)')) '自己与普通可见角色规则'
Assert-That ($state.Contains('private volatile long hiddenCharacterId') -and
    $state.Contains('hiddenCharacterId == characterId')) '隐身属于具体在线角色，不串角色'
Assert-That ($manager.Contains('final PlayerVisibilityState playerVisibility = new PlayerVisibilityState()')) '每个角色上下文拥有独立状态'
Assert-That ($manager.Contains('reconnectedManager.setCharacterSession(session)')) '原上下文重连保留状态'
Assert-That ($visibility.Contains('synchronized (subject.getPlayerVisibility())')) '隐身切换与实体发送串行化'
Assert-That ($visibility.Contains('currentManager(subject.getCharacterSession()) == subject') -and
    $visibility.Contains('manager.getCharacterSession() == session')) '旧会话不继续发实体'
Assert-That ($visibility.Contains('getChannel() == viewer.getCharacterData().getChannel()')) '同频道过滤保留'
Assert-That ($visibility.Contains('source.getEntityGameId() == observer.getEntityGameId()')) '禁止移除自己'
Assert-That ($visibility.IndexOf('new SendSetFollowPokemonPacket(source.getEntityGameId(), 0, 0, true)') -lt
    $visibility.IndexOf('new SendRemoveEntityPacket(source.getEntityGameId())')) '先清除跟随，再移除角色'
Assert-That ($visibility.Contains('new SendLoadPlayerPacket(subject.getCharacterData())') -and
    $visibility.Contains('new SendUpdatePlayerTransportationPacket(player.getEntityGameId(), player.getTransportation())')) '恢复完整角色及交通状态'
Assert-That ($visibility.Contains('visible.equals(sourceMap)') -and
    $visibility.Contains('observingSessions(subject, map)')) '切换覆盖反向相邻地图观察者'
Assert-That ($visibility.Contains('BattleRequestManager.cancelFor(subject)') -and
    $visibility.Contains('TradeManager.cancelPendingFor(subject)')) '清理待处理请求而非强行结算交易'
Assert-That ($visibility.Contains('getInteractType() != InteractType.NONE') -and
    $visibility.Contains('subject.getBattleManager() != null') -and
    $visibility.Contains('loading.isCompletedExceptionally()')) '忙碌和地图加载边界'
Assert-That ($wild.Contains('if (isHidden())') -and
    $wild.Contains('context.getPlayerVisibility().isHidden(') -and
    $wild.Contains('resetState();')) '隐身期间禁止野外遭遇并清空累计状态'
Assert-That ($maps.Contains('PlayerVisibilityService.sendIfVisible((CharacterManager) context, targetSession, packet)')) '移动/方向/位置/交通广播均过滤'
Assert-That ($maps.Contains('PlayerVisibilityService.sendPlayer(targetManager, context.characterSession)') -and
    $maps.Contains('PlayerVisibilityService.sendPlayer((CharacterManager) context, targetSession)')) '进图双向分别判断可见性'
Assert-That (-not $maps.Contains('new SendLoadPlayerPacket(')) '地图同步不绕过统一入口'
Assert-That ((Read-Source ($base + 'game/character/CharacterMovementService.java')).Contains(
    'PlayerVisibilityService.sendPlayer(aroundManager, context.characterSession)')) '相邻地图加载过滤'
Assert-That ((Read-Source ($base + 'protocol/packets/c2s/ChangeFllowPokemonPacket.java')).Contains(
    'PlayerVisibilityService.broadcast(characterManager, packet, true)')) '跟随更新过滤并保留本人'
Assert-That ((Read-Source ($base + 'protocol/packets/c2s/ResetCharacterClothesPacket.java')).Contains(
    'PlayerVisibilityService.sendIfVisible(characterManager, targetSession, packet)')) '换装过滤'
Assert-That ((Read-Source ($base + 'game/battle/BattleManager.java')).Contains('PlayerVisibilityService.sendIfVisible(fighter, recipient,')) '战斗地图状态过滤'
foreach ($file in 'game/battle/BattleRequestManager.java','game/trade/TradeRequestRegistry.java') {
    Assert-That ((Read-Source ($base + $file)).Contains('PlayerVisibilityService.mutuallyVisible(requester, target)')) "$file 的请求与接受校验"
}
Assert-That ((Read-Source ($base + 'game/battle/BattleSpectatingService.java')).Contains(
    'PlayerVisibilityService.canSee(target, spectator)')) '观战目标过滤'
$allLoads = @(Get-ChildItem -LiteralPath (Join-Path $Root ($base)) -Recurse -Filter '*.java' | Where-Object {
    (Get-Content -LiteralPath $_.FullName -Raw).Contains('new SendLoadPlayerPacket(')
})
foreach ($file in $allLoads) {
    Assert-That ($file.Name -in 'PlayerVisibilityService.java','RequestPlayerPacket.java') "完整玩家实体发送未遗漏: $($file.Name)"
}
$protocol = Read-Source ($base + 'protocol/GameProtocol.java')
foreach ($registration in '0x05, SendLoadPlayerPacket.class','0x08, SendRemoveEntityPacket.class',
    '0x28, SendUpdatePlayerTransportationPacket.class','0x2B, SendSetFollowPokemonPacket.class') {
    Assert-That ($protocol.Contains($registration)) "既有响应注册: $registration"
}
$capture = [byte[]](@(0x08,0x00) + [Text.Encoding]::Unicode.GetBytes("//hide`0"))
Assert-That ($capture.Length -eq 16) '用户 NORMAL 聊天抓包为 16 字节'
foreach ($source in $command,$state,$visibility,$wild) {
    Assert-That (($source -split '\r?\n').Count -le 500 -and $source -notmatch '(?m)^import .*\*;') '新类规模和显式导入'
    Assert-That ($source -notmatch 'org\.jooq|org\.pokemmo\.db|Files\.|ProcessBuilder|setPermission\(') '隐身不写数据库/文件，不修改权限'
}
Assert-That (Test-Path -LiteralPath (Join-Path $Root 'docs/CHAT_HIDE_COMMAND.md')) '隐身文档存在'
Write-Output "hide 静态检查通过: $script:Checks 项断言。"
Write-Output '只读源码/协议检查；未编译 Java、未运行 JUnit、未启动服务器或改变任何在线玩家状态。'
