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

$catalog = Read-Source ($base + 'game/story/ViridianCatchCatalog.java')
$flow = Read-Source ($base + 'game/story/ViridianCatchStory.java')
$state = Read-Source ($base + 'game/story/PalletStoryState.java')
$router = Read-Source ($base + 'game/story/StoryService.java')
$store = Read-Source ($base + 'services/story/ViridianCatchStore.java')
$manager = Read-Source ($base + 'script/ScriptManager.java')
$service = Read-Source ($base + 'services/GameServerService.java')
$battle = Read-Source ($base + 'game/battle/BattleOutcomeResolver.java')
$finish = Read-Source ($base + 'protocol/packets/c2s/BattleFinishSuccessPacket.java')
$resource = Read-Source 'resource/story/kanto/viridian_city/catch_tutorial/chapter.jsonc' | ConvertFrom-Json -AsHashtable

Assert-That ($resource.enabled -is [bool] -and $resource.resources.wildSpecies -eq 13 -and
    $resource.resources.wildLevel -eq 5 -and $resource.id -eq 'kanto.viridian_city.catch_tutorial') '章节配置及固定独角虫战斗'
Assert-That ($resource.text.introduction -eq 1563982 -and $resource.text.completed -eq 1564315) '原生教学文本引用'
Assert-That ($catalog.Contains('kanto/viridian_city/catch_tutorial/chapter.jsonc') -and
    $catalog.Contains('PokemonManager.getPokemonoexData') -and $catalog.Contains('isCaptureBall()')) '配置加载和资源校验'
Assert-That ($manager.Contains('ViridianCatchCatalog') -and $manager.Contains('viridianCatchStory = new ViridianCatchCatalog')) 'ScriptManager加载章节'
Assert-That ($service.Contains('new ViridianCatchStore(database)') -and $service.Contains('getViridianCatchStore()')) 'GameServerService组装持久化服务'

Assert-That ($router.Contains('StoryRuntime.onLogin(manager)') -and
    $router.Contains('StoryRuntime.onMapReady(manager)') -and
    $router.Contains('StoryRuntime.onNpc(manager, npc)')) '章节接入统一通用路由'
Assert-That ($router.Contains('StoryRuntime.onBattleFinished(manager, battle)') -and
    $router.Contains('StoryRuntime.onFinishReply(manager)')) '战斗结果和结束回执接入通用路由'
Assert-That ($battle.Contains('StoryService.onBattleFinished(player, manager)') -and
    $finish.Contains('StoryService.onFinishReply(characterManager)')) '既有战斗链路转交剧情'

Assert-That ($flow.Contains('MAP = "ViridianCity"') -and $flow.Contains('NPC = "npc_3"') -and
    $flow.Contains('stage() >= 6') -and $flow.Contains('viridianCatchComplete == null')) '只允许完成大木章节且进度加载完成'
Assert-That ($flow.Contains('static void onMapReady') -and
    $flow.Contains('play(manager, "introduction", () -> startBattle(manager))') -and
    $flow.Contains('hasCaptureBall(accountId(manager), characterId(manager))')) '地图确认后自动启动教学且要求有球'
Assert-That ($flow.Contains('scheduleAutomaticCapture') -and
    $flow.Contains('handlePlayerUseItem(ViridianCatchCatalog.BALL_ITEM_ID') -and
    $flow.Contains('800, TimeUnit.MILLISECONDS')) '教学战斗由服务端自动提交普通精灵球'
Assert-That ($flow.Contains('hasCaptureBall') -and $flow.Contains('没有精灵球')) '无球时不启动教学战斗'
Assert-That ($flow.Contains('new PokemonData') -or $flow.Contains('createWildPokemon') ) '教学战斗使用服务端野生宝可梦'
Assert-That ($flow.Contains('PokemonContainerType.EVENT') -and
    $flow.Contains('BattleGenerator.generatorWildBattle') -and $flow.Contains('battle.handleBattleBegin')) '复用EVENT野生战斗'
Assert-That ($flow.Contains('viridianCatchBattle = battle') -and
    $flow.Contains('GameSessionPool.addBattleManagerInPool') -and
    $flow.Contains('GameSessionPool.removeBattleManagerInPool')) '教学战斗引用和战斗池清理'
Assert-That ($flow.Contains('FactionResultType.CATCH_POKEMON') -and
    $flow.Contains('store(manager).complete') -and $flow.Contains('getStoryLineFlag()[0]')) '只在捕获成功后写完成状态'
Assert-That ($flow.Contains('result == FactionResultType.IN_BATTLE') -and
    $flow.Contains('if (!caught)') -and $flow.Contains('completed')) '结束回执校验和失败可重试'
Assert-That ($flow.Contains('previousComplete') -and $flow.Contains('reconnectingBattle')) '重连教学战斗保留临时状态'
Assert-That ($state.Contains('viridianCatchComplete') -and $state.Contains('viridianCatchBattle')) '角色独立教学状态'

Assert-That ($store.Contains('STORY_LINE_MASK = 1 << 4') -and
    $store.Contains('flags[0] = (short) (current | STORY_LINE_MASK)')) '只设置关都独立bit且保留其他位'
Assert-That ($store.Contains('CHARACTER.ACCOUNT_ID.eq(accountId)') -and
    $store.Contains('forUpdate()') -and $store.Contains('transactionResult')) '角色账号行锁和事务'
Assert-That ($store.Contains('OWNED_ITEM.OWNER_ID.eq(characterId)') -and
    $store.Contains('INVENTORY.NAME.eq("inventory")') -and $store.Contains('isCaptureBall()')) '捕获球查询限定角色主背包'
Assert-That ($store.Contains('if (isComplete(current))') -and
    $store.Contains('return new Result(true, current, false)')) '完成标记幂等'
Assert-That (-not $store.Contains('Session') -and -not $store.Contains('.send(')) '持久化服务不持有Session或发包'

$map = Read-Source 'resource/map/kanto/viridian_city/ViridianCity.json' | ConvertFrom-Json
$npc = @($map.npcs | Where-Object entityIdx -eq 3)
Assert-That ($map.regionId -eq 0 -and $map.bankId -eq 3 -and $map.mapId -eq 1) '常磐市地图标识'
Assert-That ($npc.Count -eq 1 -and $npc[0].graphicsId -eq 32 -and
    $npc[0].script -eq 'ViridianCity_EventScript_TutorialOldMan' -and
    $npc[0].x -eq 21 -and $npc[0].y -eq 6) '教学老人固定NPC'

$clientText = 'C:\Users\z3407\Desktop\28887-obf-project\data\strings\strings_zh_dump.xml'
Assert-That (Test-Path -LiteralPath $clientText -PathType Leaf) '客户端中文文本目录'
$xml = [xml](Get-Content -LiteralPath $clientText -Raw -Encoding UTF8)
foreach ($id in 1563982, 1564315) {
    Assert-That ($null -ne $xml.SelectSingleNode("//string[@id='$id']")) "客户端文本存在: $id"
}

foreach ($path in 'game/story/ViridianCatchCatalog.java','game/story/ViridianCatchStory.java',
    'services/story/ViridianCatchStore.java','game/story/PalletStoryState.java',
    'game/story/StoryService.java','services/GameServerService.java') {
    $source = Read-Source $base$path
    Assert-That (($source -split '\r?\n').Count -le 500 -and $source -notmatch '(?m)^import .*\*;') "规模及显式导入: $path"
}
foreach ($path in 'game/battle/BattleOutcomeResolver.java','protocol/packets/c2s/BattleFinishSuccessPacket.java') {
    $source = Read-Source $base$path
    Assert-That (($source -split '\r?\n').Count -le 500) "必要接入文件规模: $path"
}
foreach ($path in 'game/story/ViridianCatchStoryTest.java','services/story/ViridianCatchStoreTest.java') {
    $test = Read-Source ('server.game/src/test/java/org/pokemmo/gameserver/' + $path)
    Assert-That (($test -split '\r?\n').Count -le 500 -and $test -notmatch '(?m)^import .*\*;') "测试规模及显式导入: $path"
}

Assert-That ((Read-Source 'resource/story/README.md').Contains('catch_tutorial')) '资源目录已登记章节'
Assert-That ((Read-Source 'docs/README.md').Contains('VIRIDIAN_CATCH_TUTORIAL.md')) '文档入口已登记章节'
Write-Output "常磐市捕获教学静态检查通过: $script:Checks 项断言。"
Write-Output '未编译、未运行 JUnit、未启动服务端或客户端、未连接数据库。'
