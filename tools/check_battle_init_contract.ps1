param(
    [string]$Root = (Split-Path -Parent $PSScriptRoot),
    [string]$ClientRoot = 'C:/Users/z3407/Desktop/28887-obf-project'
)

$ErrorActionPreference = 'Stop'
$script:Checks = 0
function Assert-That([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw "检查失败: $Message" }
    $script:Checks++
}
$clientPath = Join-Path $ClientRoot 'src/main/java/f/iv_1.java'
$client = if (Test-Path -LiteralPath $clientPath) {
    Get-Content -LiteralPath $clientPath -Raw -Encoding UTF8
} else {
    ''
}
if ($client.IndexOf('public final O8 LPt4') -lt 0) {
    $clientPath = Join-Path $ClientRoot 'src/main/java/cn/pokemmo/net/packet/inbound/BattleActiveMonsterUpdatePacket.java'
    $client = Get-Content -LiteralPath $clientPath -Raw -Encoding UTF8
}
$method = $client.Substring($client.IndexOf('public final O8 LPt4'))
$trainer = [regex]::Match($method, '(?s)case 2:(.*?)case 3:').Groups[1].Value
Assert-That ($trainer.Contains('new MC(b0, super.Rj.get(), super.Rj.getShort(), b1, super.Rj.get())')) 'TRAINER 参数求值顺序与 JASM W..AA 一致'
Assert-That (([regex]::Matches($trainer, 'super\.Rj\.get\(\)')).Count -eq 2) 'TRAINER 分支独立读取地区和道具使用次数两个字节'
Assert-That (([regex]::Matches($trainer, 'super\.Rj\.getShort\(\)')).Count -eq 1) 'TRAINER 分支读取两字节训练家引用'
$base = 'server.game/src/main/java/org/pokemmo/gameserver/'
$server = Get-Content -LiteralPath (Join-Path $Root ($base + 'codecs/BattleTeamInfoCodec.java')) -Raw -Encoding UTF8
$branch = [regex]::Match($server, '(?s)case TRAINER:(.*?)case CUSTOM_TRAINER:').Groups[1].Value
Assert-That ($branch.Contains('getRemainItemUseTimes()')) '服务端保留原协议最后一个字节'
$battle = Get-Content -LiteralPath (Join-Path $Root ($base + 'game/story/PalletStoryBattle.java')) -Raw -Encoding UTF8
Assert-That ($battle.IndexOf('validateInitialPacket(manager, battle)') -lt $battle.IndexOf('manager.setBattleManager(battle)')) '编码检查先于发布移动锁'
Assert-That ($battle.Contains('buffer.release()')) '编码检查释放临时缓冲区'
Assert-That ($battle.Contains('if (manager.getBattleManager() == battle) manager.setBattleManager(null)') -and
    $battle.Contains('if (manager.getPalletStory().battle == battle) manager.getPalletStory().battle = null')) '启动失败按实例身份清理角色和剧情引用'
Assert-That ($battle.Contains('GameSessionPool.removeBattleManagerInPool(battle)')) '启动失败清理战斗池'
Assert-That (-not $battle.Contains('Thread.sleep(')) '不使用固定延迟掩盖协议错位'
$tests = Get-Content -LiteralPath (Join-Path $Root 'server.game/src/test/java/org/pokemmo/gameserver/game/story/PalletStoryTest.java') -Raw -Encoding UTF8
foreach ($name in 'trainerHeaderIncludesItemLimitBeforeFactionData','encodingFailureDoesNotPublishABattle',
    'startupExceptionReleasesBattleAndStoryLocksWithoutResettingProgress') {
    Assert-That ($tests.Contains($name)) "回归用例源码存在: $name"
}

# In-memory fixture: team header, field flags, faction-team count, Pokemon count.
# This models the confirmed byte reads, not a run of either Java application.
foreach ($region in 0,3) {
    foreach ($capacity in 1,6) {
        foreach ($trainerId in 328,450) {
            foreach ($itemLimit in 0,4,7) {
                $stream = [IO.MemoryStream]::new()
                $writer = [IO.BinaryWriter]::new($stream)
                try {
                    $writer.Write([byte]2)
                    $writer.Write([byte]$capacity)
                    $writer.Write([byte]$region)
                    $writer.Write([short]$trainerId)
                    $writer.Write([byte]$itemLimit)
                    $writer.Write([int]0)
                    $writer.Write([byte]1)
                    $writer.Write([byte]1)
                    $bytes = $stream.ToArray()
                } finally { $writer.Dispose(); $stream.Dispose() }
                $input = [IO.MemoryStream]::new($bytes)
                $reader = [IO.BinaryReader]::new($input)
                try {
                    Assert-That ($reader.ReadByte() -eq 2 -and $reader.ReadByte() -eq $capacity) '公共类型/容量字段'
                    Assert-That ($reader.ReadByte() -eq $region -and $reader.ReadInt16() -eq $trainerId -and
                        $reader.ReadByte() -eq $itemLimit) 'JASM 三次读取与 MC 参数顺序'
                    Assert-That ($reader.ReadInt32() -eq 0 -and $reader.ReadByte() -eq 1 -and $reader.ReadByte() -eq 1) '修复后场地和队伍数量保持对齐'
                    Assert-That ($input.Position -eq $input.Length) '修复后完整消费样例'
                    $input.Position = 5
                    $wrongFlag = $reader.ReadInt32()
                    $wrongTeamCount = $reader.ReadByte()
                    Assert-That ($wrongFlag -eq $itemLimit -and $wrongTeamCount -eq 0) '旧漏读逻辑可重现错误场地标志和空队伍计数'
                } finally { $reader.Dispose(); $input.Dispose() }
            }
        }
    }
}
Write-Output "战斗初始化静态合同检查通过: $script:Checks 项断言，24 组字节对齐样例。"
Write-Output '未编译 Java、未运行 JUnit/客户端/服务器、未连接数据库；实机效果需由用户重新编译双方后验证。'
