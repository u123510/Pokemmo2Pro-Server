param([string]$Root = (Split-Path -Parent $PSScriptRoot))

$ErrorActionPreference = 'Stop'
$Root = [IO.Path]::GetFullPath($Root)
$script:Checks = 0
$base = 'server.game/src/main/java/org/pokemmo/gameserver/'
function Assert-That([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw "检查失败: $Message" }
    $script:Checks++
}
function Read-Source([string]$Path) { Get-Content -LiteralPath (Join-Path $Root $Path) -Raw -Encoding UTF8 }

$flow = Read-Source ($base + 'game/story/PalletOpeningService.java')
$router = Read-Source ($base + 'game/story/StoryService.java')
$runtime = Read-Source ($base + 'game/story/StoryRuntime.java')
$executor = Read-Source ($base + 'game/story/StoryActionExecutor.java')
$catalog = Read-Source ($base + 'game/story/StoryCatalog.java')
$lifecycle = Read-Source ($base + 'game/story/PalletStoryLifecycle.java')
$scene = Read-Source ($base + 'game/story/PalletStoryScene.java')
$state = Read-Source ($base + 'game/story/PalletStoryProgress.java')
$npcs = Read-Source ($base + 'game/story/PalletStoryNpcs.java')
$store = Read-Source ($base + 'services/story/PalletStoryStore.java')
$battle = Read-Source ($base + 'game/story/PalletStoryBattle.java')
$loader = Read-Source ($base + 'script/ScriptManager.java')
$oldMap = Read-Source ($base + 'game/map/KantoregionMapData.java')
$oldEvents = Read-Source ($base + 'game/character/CharacterEventService.java')
$resources = Read-Source 'resource/story/kanto/pallet_town/opening.jsonc' | ConvertFrom-Json
$oakResource = Read-Source 'resource/story/kanto/oak_parcel/chapter.jsonc' | ConvertFrom-Json
$catchResource = Read-Source 'resource/story/kanto/viridian_city/catch_tutorial/chapter.jsonc' | ConvertFrom-Json
$nurseResource = Read-Source 'resource/story/kanto/pokemon_center_nurse/chapter.jsonc' | ConvertFrom-Json
$earlyResource = Read-Source 'resource/story/kanto/early_route_to_vermilion/chapter.jsonc' | ConvertFrom-Json

foreach ($directory in 'resource/interact','resource/event') {
    $files = @(Get-ChildItem -LiteralPath (Join-Path $Root $directory) -Recurse -File -ErrorAction SilentlyContinue)
    Assert-That ($files.Count -eq 0) "$directory 旧剧情内容已删除"
}
Assert-That (-not $loader.Contains('loadInteractScriptsData') -and -not $loader.Contains('loadEventScriptsData')) '不再加载旧脚本目录'
Assert-That (-not $oldMap.Contains('getEventScript(') -and -not $oldMap.Contains('getFirstPartnerStatus(')) '地图不再保存每玩家剧情逻辑'
Assert-That (-not $oldEvents.Contains('getInteractScriptByEntityName') -and $oldEvents.Contains('StoryService.onStep') -and
    $router.Contains('StoryRuntime.onStep') -and $router.Contains('StoryRuntime.onNpc')) '角色入口经通用剧情路由转交'
Assert-That ($state.Contains('stage >= COMPLETE') -and $state.Contains('character.getOakLabStatus()')) '使用数据库已有完成标记且保留后续旧阶段'
Assert-That ($store.Contains('transactionResult') -and $store.Contains('.forUpdate()')) '持久化使用事务行锁'
Assert-That ($store.Contains('CHARACTER.ACCOUNT_ID.eq(accountId)') -and $store.Contains('POKEMON.TRAINER_ID.eq(characterId)')) '角色和宝可梦归属约束'
Assert-That ($store.Contains('tx.insertInto(POKEMON).set(candidate.toPokemonRecord())') -and $store.Contains('.set(CHARACTER.FIRST_PARTNER_STATUS, partners)')) '发放和初始选择共同持久化'
Assert-That ($store.Contains('if (progress.stage() >= PalletStoryProgress.RIVAL) return') -and $store.Contains('if (progress.completed()) return')) '领取和结算重复调用幂等'
Assert-That (-not $store.Contains('.set(CHARACTER.OAK_PARCEL_STATUS') -and -not $store.Contains('deleteFrom(')) '不清空存档或改包裹剧情'
Assert-That ($scene.Contains('sequence != state.expectedReply') -and $scene.Contains('state.cancelWait();')) '序号校验及一次性回调'
Assert-That ($scene.Contains('generation == state.generation') -and $flow.Contains('manager.getCharacterSession() != session')) '过期定时回调及会话保护'
Assert-That ($scene.Contains('SportType.getActionTimeConsuming') -and -not $scene.Contains('Thread.sleep(')) '动画异步按时长推进，不阻塞线程'
Assert-That ($executor.Contains('new SendPlayMusicPacket((byte) 0, (short) musicId, false)') -and
    $scene.Contains('new SendPlayMusicPacket((byte) 0, (short) 0')) '音乐包构造参数匹配现有 byte/short 签名'
Assert-That ($runtime.Contains('state.awaitingMap = true') -and $runtime.Contains('MAP_READY')) '换图等待客户端地图确认'
Assert-That ($battle.Contains('result != FactionResultType.VICTORY && result != FactionResultType.DEFEAT')) '客户端不能提前宣告战斗完成'
Assert-That ((Read-Source ($base + 'game/battle/BattleOutcomeResolver.java')).Contains('StoryService.onBattleFinished(player, manager)')) '服务端战斗结束触发存档'
Assert-That ((Read-Source ($base + 'protocol/packets/c2s/BattleFinishSuccessPacket.java')).Contains('StoryService.onFinishReply')) '战斗结束回执接入'
Assert-That ($lifecycle.Contains('PalletStoryBattle.resumeAfterLogin(manager)') -and
    $battle.Contains('static void resumeAfterLogin')) '已结束教学战斗重连后不重新初始化'
Assert-That ($battle.Contains('开场已保存但客户端队伍刷新失败') -and $battle.Contains('finally {')) '提交后的显示刷新失败不伪装为未保存'
Assert-That ($npcs.Contains('entity.clone()') -and $npcs.Contains('Map.copyOf(actors)')) '每玩家独立 NPC 投影'
Assert-That ((Read-Source ($base + 'game/entity/NpcVisibilityService.java')).Contains('PalletStoryNpcs.project')) '地图快照使用剧情可见性'
Assert-That ((Read-Source ($base + 'game/interact/SceneInteractionService.java')).Contains('StoryService.onNpc') -and $router.Contains('StoryRuntime.onNpc')) 'NPC 交互接入'
Assert-That ((Read-Source ($base + 'protocol/packets/c2s/InteractPacket.java')).Contains('StoryService.onReply') -and $router.Contains('StoryRuntime.onReply')) '原生交互回执接入'
Assert-That ((Read-Source ($base + 'protocol/packets/c2s/RequestPlayerPacket.java')).Contains('StoryService.onMapReady') -and $router.Contains('StoryRuntime.onMapReady')) '地图确认接入'
Assert-That ((Read-Source ($base + 'protocol/GameProtocol.java')).Contains('StoryService.onDisconnect') -and $router.Contains('StoryRuntime.onDisconnect')) '断线清理不清除存档'
Assert-That ($runtime.Contains('PalletStoryLifecycle.onLogin(manager)') -and $lifecycle.Contains('.loadProgress(')) '登录及重连使用独立存档读取入口'
Assert-That ($store.Contains('select(CHARACTER.OAK_LAB_STATUS, CHARACTER.FIRST_PARTNER_STATUS)') -and
    $store.Contains('return progress(stored.value1(), stored.value2())')) '只读取当前角色的两项剧情字段'
Assert-That ($lifecycle.Contains('manager.getBattleManager() != null') -and
    $lifecycle.Contains('保留当前战斗进度')) '不把外部重置强行应用到进行中的战斗'
Assert-That ($lifecycle.Contains('state.progressLoadFailed = true') -and
    $flow.Contains('if (manager.getPalletStory().progressLoadFailed)')) '查询失败暂停触发，不回退旧缓存'
Assert-That ($runtime.Contains('runFirst(manager, "MAP_READY"') -and
    $runtime.Contains('runFirst(manager, "COORDINATE"')) '登录或传送到触发格时补查通用节点'
Assert-That ($flow.Contains('真新镇剧情触发检查') -and $lifecycle.Contains('真新镇剧情进度已加载')) '中文日志输出真实进度和触发条件'
Assert-That ($state.Contains('stage < RIVAL && starter != 3') -and $state.Contains('validateOpeningCheckpoint()')) '只重置阶段但保留初始选择时拒绝重播，完成存档不受影响'
Assert-That ($state.Contains('oak_lab_status=') -and $state.Contains('first_partner_status[1]=') -and
    $state.Contains('3（未选择）')) '冲突消息明确实际字段、SQL数组下标和未选择编码'
Assert-That ($runtime.Contains('matchesCondition') -and $runtime.Contains('STORY_STAGE_EQUALS') -and
    $runtime.Contains('STARTER_EQUALS')) '通用触发器按存档条件筛选'
Assert-That ($store.Contains('progress.validateOpeningCheckpoint()') -and $store.Contains('数据库阶段=') -and
    $store.Contains('数据库初始选择=')) '事务中重查数据库状态，错误包含期望与实际阶段'
$storyTests = Read-Source 'server.game/src/test/java/org/pokemmo/gameserver/game/story/PalletStoryTest.java'
Assert-That ($storyTests.Contains('partialResetRequiresBothCheckpointFieldsWithoutChangingLegacyData') -and
    $storyTests.Contains('conflictingStepAndMapEntryDoNotStartSceneOrResetAssets')) '部分重置与无剧情锁定回归源码存在（未执行）'
Assert-That ($resources.enabled -is [bool] -and $resources.resources.starters.Count -eq 3 -and
    $resources.id -eq 'kanto.pallet_town.opening' -and $resources.nodes.Count -gt 0) '通用配置结构完整'
for ($i = 0; $i -lt 3; $i++) {
    $starter = $resources.resources.starters[$i]
    Assert-That ($starter.choice -eq $i -and $starter.species -eq @(1,4,7)[$i] -and $starter.rivalSpecies -eq @(4,7,1)[$i]) '初始选择和克制队伍映射'
    Assert-That ($starter.moves.Count -eq 4 -and $starter.prompt -gt 0) '招式槽和原生文本引用'
}
foreach ($chapter in $resources, $oakResource, $catchResource, $nurseResource, $earlyResource) {
    Assert-That ($chapter.id -and $chapter.version -ge 1 -and $chapter.enabled -is [bool] -and
        $chapter.triggers.Count -gt 0 -and $chapter.nodes.Count -gt 0 -and $chapter.text) '四章均使用通用节点格式'
}
Assert-That ($runtime.Contains('pokemon_center_nurse') -or $nurseResource.id -eq 'kanto.common.pokemon_center_nurse') '护士章节已纳入通用目录'
Assert-That ($earlyResource.id -eq 'kanto.early_route_to_vermilion' -and
    $earlyResource.text.brockIntro -eq 1641684 -and
    $earlyResource.text.surgeBadge -eq 1658371) '尼比至枯叶主线文本配置'
Assert-That ($executor.Contains('START_TRAINER_BATTLE') -and
    $executor.Contains('setKantoStoryBit') -and $executor.Contains('setKantoBadgeBit')) '早期主线动作适配器'

function Check-Route($Map, [int]$X, [int]$Y, [int]$TargetX, [int]$TargetY) {
    $bytes = [Convert]::FromBase64String($Map.blockData)
    $width = [int]$Map.width
    $height = [int]$Map.height
    $visited = [Collections.Generic.HashSet[int]]::new()
    $queue = [Collections.Generic.Queue[int]]::new()
    $start = $Y * $width + $X
    $target = $TargetY * $width + $TargetX
    Assert-That (([BitConverter]::ToUInt16($bytes, 2 * $start) -band 1024) -eq 0) '剧情移动起点可走'
    $queue.Enqueue($start)
    [void]$visited.Add($start)
    while ($queue.Count -gt 0 -and -not $visited.Contains($target)) {
        $position = $queue.Dequeue()
        foreach ($delta in @(@(0,1),@(0,-1),@(1,0),@(-1,0))) {
            $nx = $position % $width + $delta[0]
            $ny = [int][Math]::Floor($position / $width) + $delta[1]
            if ($nx -lt 0 -or $ny -lt 0 -or $nx -ge $width -or $ny -ge $height) { continue }
            $next = $ny * $width + $nx
            if (([BitConverter]::ToUInt16($bytes, 2 * $next) -band 1024) -eq 0 -and $visited.Add($next)) { $queue.Enqueue($next) }
        }
    }
    Assert-That ($visited.Contains($target)) "剧情路线可达: ($X,$Y) -> ($TargetX,$TargetY)"
}
$town = Read-Source 'resource/map/kanto/pallet_town/PalletTown.json' | ConvertFrom-Json
$lab = Read-Source 'resource/map/kanto/pallet_town_professor_oaks_lab/PalletTown_ProfessorOaksLab.json' | ConvertFrom-Json
Check-Route $town 10 8 12 2
Check-Route $town 10 8 13 2
Check-Route $town 12 2 16 14
Check-Route $town 12 1 16 15
Check-Route $town 13 1 16 15
Check-Route $lab 6 11 6 4
foreach ($x in 5,6,7) { Check-Route $lab $x 8 $x 7 }
Assert-That (($town.npcs | Where-Object entityIdx -eq 2).graphicsId -eq 71) '大木原生 NPC 绑定'
Assert-That (($lab.npcs | Where-Object entityIdx -eq 7).graphicsId -eq 72) '劲敌原生 NPC 绑定'

$javaFiles = @(Get-ChildItem -LiteralPath (Join-Path $Root ($base + 'game/story')) -Filter '*.java') +
    @(Get-ChildItem -LiteralPath (Join-Path $Root ($base + 'services/story')) -Filter '*.java') +
    @(Get-ChildItem -LiteralPath (Join-Path $Root 'server.game/src/test/java/org/pokemmo/gameserver/game/story') -Filter '*.java') +
    @(Get-ChildItem -LiteralPath (Join-Path $Root 'server.game/src/test/java/org/pokemmo/gameserver/services/story') -Filter '*.java')
foreach ($file in $javaFiles) {
    $source = Get-Content -LiteralPath $file.FullName -Raw -Encoding UTF8
    Assert-That (($source -split '\r?\n').Count -le 500 -and $source -notmatch '(?m)^import .*\*;') "规模及显式导入: $($file.Name)"
    $code = [regex]::Replace($source, '(?s)/\*.*?\*/|"(?:\\.|[^"\\])*"|''(?:\\.|[^''\\])*''|//[^\r\n]*', '')
    $stack = [Collections.Generic.Stack[char]]::new()
    foreach ($character in $code.ToCharArray()) {
        if ($character -in '{','(','[') { $stack.Push($character) }
        if ($character -in '}',')',']') {
            $expected = @{'}'='{';')'='(';']'='['}[[string]$character]
            if ($stack.Count -eq 0 -or $stack.Pop() -ne $expected) { throw "括号不平衡: $($file.Name)" }
        }
    }
    Assert-That ($stack.Count -eq 0) "括号结构: $($file.Name)"
}
Write-Output "剧情静态检查通过: $script:Checks 项断言，9 条地图路线。"
Write-Output '未编译、未运行 JUnit、未启动服务器或客户端、未连接数据库。事务并发与界面效果仍需实机验证。'
