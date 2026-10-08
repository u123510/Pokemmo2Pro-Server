param(
    [string]$Root = (Split-Path -Parent $PSScriptRoot),
    [string]$ClientRoot = (Join-Path (Split-Path -Parent (Split-Path -Parent $PSScriptRoot)) '28887-obf-project')
)

$ErrorActionPreference = 'Stop'
$Root = [IO.Path]::GetFullPath($Root)
$script:Checks = 0
$base = 'server.game/src/main/java/org/pokemmo/gameserver/'
function Assert-That([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw "检查失败: $Message" }
    $script:Checks++
}
function Read-Source([string]$Path) { Get-Content -LiteralPath (Join-Path $Root $Path) -Raw -Encoding UTF8 }

$router = Read-Source ($base + 'game/story/StoryService.java')
$flow = Read-Source ($base + 'game/story/OakParcelStory.java')
$runtime = Read-Source ($base + 'game/story/StoryRuntime.java')
$progress = Read-Source ($base + 'game/story/OakParcelProgress.java')
$catalog = Read-Source ($base + 'game/story/OakParcelCatalog.java')
$store = Read-Source ($base + 'services/story/OakParcelStore.java')
$inventory = Read-Source ($base + 'services/story/StoryInventory.java')
$scene = Read-Source ($base + 'game/story/PalletStoryScene.java')
$npcs = Read-Source ($base + 'game/story/PalletStoryNpcs.java')
$interact = Read-Source ($base + 'game/interact/SceneInteractionService.java')
$loader = Read-Source ($base + 'script/ScriptManager.java')
$service = Read-Source ($base + 'services/GameServerService.java')
$chapter = Read-Source 'resource/story/kanto/oak_parcel/chapter.jsonc' | ConvertFrom-Json -AsHashtable

Assert-That ($chapter.enabled -is [bool] -and $chapter.text.Count -eq 18) '独立章节开关及十八条文本引用'
$texts = @{
    martGreeting=1638938; martRequest=1638970; parcelReceived=1639049; martThanks=1639103
    oakGreeting=1631237; parcelDelivered=1631407; oakThanks=1631434; dexRequest=1631496
    dexDescription=1631542; dexOffer=1631685; dexReceived=1631722; ballIntroduction=1631762
    ballsReceived=1631923; catchAdvice=1631952; oakDream=1632132; depart=1632369
    goToCity=1631024; returnAdvice=1632654
}
$xmlPath = Join-Path $ClientRoot 'data/strings/strings_zh_dump.xml'
Assert-That (Test-Path -LiteralPath $xmlPath -PathType Leaf) '只读客户端文本目录存在'
$xml = [xml](Get-Content -LiteralPath $xmlPath -Raw -Encoding UTF8)
foreach ($key in $texts.Keys) {
    Assert-That ($chapter.text[$key] -eq $texts[$key] -and $catalog.Contains('"' + $key + '"')) "原生文本与加载校验: $key"
    Assert-That ($null -ne $xml.SelectSingleNode("//string[@id='$($texts[$key])']")) "客户端包含文本: $key"
}
Assert-That ($catalog.Contains('BALL_ITEM_ID = 5004') -and $catalog.Contains('BALL_COUNT = 5')) '精灵球奖励匹配原生五球对白'
Assert-That ($catalog.Contains('getItemMaxStackSize() < needed') -and $catalog.Contains('Map.copyOf(text)')) '道具资源检查及不可变内容'
Assert-That ($loader.Contains('oakParcelStory = new OakParcelCatalog') -and $loader.IndexOf('new OakParcelCatalog') -gt $loader.IndexOf('new ItemManager')) '道具加载后加载独立章节'
Assert-That ($service.Contains('new OakParcelStore(database)') -and $service.Contains('getOakParcelStore()')) '持久化服务已组装且入口存在'

Assert-That ($progress.Contains('labStage < PalletStoryProgress.COMPLETE') -and $progress.Contains('labStage >= 6')) '开场前禁止任务，完成和旧后续阶段不重播'
Assert-That ($progress.Contains('labStage == 5 || parcelStatus == 2') -and $progress.Contains('parcelStatus == 1')) '旧交付状态与待交付状态区分'
Assert-That ($progress.Contains('parcelStatus > 3') -and $progress.Contains('labStage > 9')) '拒绝无效阶段而非默认为新玩家'
Assert-That ($router.Contains('StoryRuntime.onLogin(manager)') -and $runtime.Contains('OakParcelStory.onLogin(manager)')) '同一角色加载两章进度'
Assert-That ($flow.Contains('parcelStatus = null') -and $flow.Contains('store(manager).loadProgress') -and
    $flow.Contains('manager.getBattleManager() == null')) '重登重读包裹进度，战斗中不外部重置'
Assert-That ($flow.Contains('if (parcel == null) throw') -and $flow.Contains('PalletOpeningService.progress(manager).stage(), parcel')) '读取失败暂停，当前登录结束开场后可直接接任务'
Assert-That ($flow.Contains('progress.phaseName()') -and $flow.Contains('大木包裹剧情操作已提交')) '中文阶段与提交日志'
Assert-That ($router.Contains('StoryRuntime.onMapReady(manager)') -and $runtime.Contains('MAP_READY') -and
    $flow.Contains('if (!idle(manager)) return')) '真实地图确认后触发，锁内重查忙碌状态'
Assert-That ($flow.Contains('manager.getBattleManager() == null && !TradeManager.isInTrade(manager)') -and
    $flow.Contains('getInteractType() == InteractType.NONE')) '战斗、交易或其他交互中不能开启章节'
Assert-That ($interact.IndexOf('StoryService.beforeShop(manager, npc)') -ge 0 -and
    $interact.IndexOf('StoryService.beforeShop(manager, npc)') -lt $interact.IndexOf('shopService.tryOpen(session, manager, npc)')) '仅待办剧情优先于购物'
Assert-That ($flow.Contains('if (!needsParcel(manager, progress)) return false') -and $flow.Contains('store(manager).hasParcel')) '持有包裹及完成玩家正常购物，缺失包裹可补领'
Assert-That ($flow.Contains('requireMap(manager, map)') -and $flow.Contains('"npc_0".equals(npc.getNpcName())') -and
    $flow.Contains('"npc_3".equals(npc.getNpcName())')) '限定地图与固定店员/博士，提交前重查地图'

$mart = Read-Source 'resource/map/kanto/viridian_city_mart/ViridianCity_Mart.json' | ConvertFrom-Json
$lab = Read-Source 'resource/map/kanto/pallet_town_professor_oaks_lab/PalletTown_ProfessorOaksLab.json' | ConvertFrom-Json
$clerk = @($mart.npcs | Where-Object entityIdx -eq 0)
$oak = @($lab.npcs | Where-Object entityIdx -eq 3)
Assert-That ($mart.regionId -eq 0 -and $mart.bankId -eq 5 -and $mart.mapId -eq 3) '常磐市商店地图标识'
Assert-That ($clerk.Count -eq 1 -and $clerk[0].script -eq 'ViridianCity_Mart_EventScript_Clerk') '任务仅绑定原店员'
Assert-That ($lab.regionId -eq 0 -and $lab.bankId -eq 4 -and $lab.mapId -eq 3 -and $oak.Count -eq 1) '大木研究所及博士存在'
Assert-That ($npcs.Contains('OakParcelStory.showRival(manager)') -and $npcs.Contains('progress.stage() < 6') -and
    $npcs.Contains('entity.clone()')) '图鉴和劲敌按玩家阶段投影，不更改共享地图'

Assert-That ($store.Contains('transactionResult') -and $store.Contains('.forUpdate().fetchOne()')) '事务内先锁定角色'
Assert-That ($store.Contains('CHARACTER.ACCOUNT_ID.eq(accountId)') -and $store.Contains('CHARACTER.ID.eq(characterId)')) '读取/提交限制角色与账号'
Assert-That ($store.Contains('CHARACTER.OAK_LAB_STATUS.eq(progress.labStage())') -and
    $store.Contains('CHARACTER.OAK_PARCEL_STATUS.eq(progress.parcelStatus())')) '检查点比较更新及事务回滚'
Assert-That ($store.Contains('if (phase == OakParcelProgress.Phase.COMPLETE) return false') -and
    $store.Contains('if (!change) return new Result')) '完成和重复回执不再发奖'
Assert-That ($store.Contains('inventory.ensureParcel(newItemId)') -and $inventory.Contains('if (!hasParcel()) addStack')) '领取可采用已有包裹，不重复创建'
Assert-That ($store.Contains('phase == OakParcelProgress.Phase.DELIVER && !hasParcel') -and
    $store.Contains('尚未领取大木的包裹') -and $store.Contains('请先把包裹交给大木博士')) '禁止越过领取/交付检查点'
Assert-That ($store.Contains('inventory.consumeParcel()') -and $store.Contains('new OakParcelProgress((short) 5, (short) 2)')) '交付与阶段5原子提交'
Assert-That ($store.Contains('inventory.grant(OakParcelCatalog.BALL_ITEM_ID, OakParcelCatalog.BALL_COUNT, newItemId)') -and
    $store.Contains('new OakParcelProgress((short) 6, (short) 2)')) '五球奖励与阶段6原子提交'
Assert-That ($inventory.Contains('OWNED_ITEM.OWNER_ID.eq(ownerId)') -and $inventory.Contains('OWNED_ITEM.INVENTORY_ID.eq(inventory.getId())') -and
    $inventory.Contains('.orderBy(OWNED_ITEM.ITEM_ID)') -and $inventory.Contains('.forUpdate().fetch()')) '主背包按编号锁定并限制归属'
Assert-That ($inventory.Contains('amount - 1') -and $inventory.Contains('OWNED_ITEM.ITEM_AMOUNT.eq(amount)')) '交付只消耗一件已拥有包裹'
Assert-That ($inventory.Contains('row.getItemId() <= 0') -and $inventory.Contains('objectId <= 0') -and
    $inventory.Contains('row.getItemAmount() <= 0')) '正数道具编号及合法数量'
Assert-That ($inventory.Contains('bytes > 65000') -and $inventory.Contains('rows.size() > 65535') -and
    $inventory.Contains('reward && row.getPvpRewardTime() == null')) '提交前检查完整背包容量及可编码性'
Assert-That ($inventory.Contains('metadata.getItemMaxStackSize() - row.getItemAmount()') -and
    $inventory.Contains('row.getItemRegionIndexId() != -1')) '按堆叠上限合并，不能混入地区限定堆叠'
Assert-That (-not $store.Contains('Session') -and -not $inventory.Contains('.send(') -and -not $flow.Contains('org.jooq')) '事务层与表现层分离'
Assert-That ($flow.IndexOf('store(manager).execute') -lt $flow.IndexOf('apply(manager, result.progress())') -and
    $flow.IndexOf('apply(manager, result.progress())') -lt $flow.IndexOf('new SendInventoryPacket')) '数据库提交后更新在线阶段及客户端背包'
Assert-That ($flow.Contains('new SendLoadDexPacket') -and $flow.Contains('getPokemonDexUnlockDataById')) '刷新现有图鉴而非伪造解锁旗标'
Assert-That ($scene.Contains('sequence != state.expectedReply') -and $scene.Contains('generation == state.generation') -and
    $scene.Contains('InteractType.NONE') -and $scene.Contains('new SendHasEventPacket(false)')) '复用回执防重、过期回调及解锁清理'

$items = Read-Source ($base + 'game/item/ItemManager.java')
Assert-That ($items.Contains('OAK_PARCEL_ITEM_ID = 349') -and $items.Contains('getItemData(itemIndexId) == null || isStoryBound(itemIndexId)')) '包裹独立禁止转移，不依赖不完整资源元数据'
foreach ($path in 'services/inventory/InventoryService.java','services/pokemon/PokemonItemService.java','services/item/ItemUseService.java') {
    Assert-That ((Read-Source ($base + $path)).Contains('ItemManager.isStoryBound')) "任务物品保护入口: $path"
}
foreach ($path in 'services/shop/ShopItemPolicy.java','services/mail/MailSendService.java','services/gtl/GtlListingService.java','services/trade/TradeValidationService.java') {
    Assert-That ((Read-Source ($base + $path)).Contains('ItemManager.isTradeableForExchange')) "复用转移限制: $path"
}
foreach ($path in 'game/story/OakParcelStoryTest.java','services/story/OakParcelStoreTest.java') {
    $test = Read-Source ('server.game/src/test/java/org/pokemmo/gameserver/' + $path)
    Assert-That (([regex]::Matches($test, '@Test\b')).Count -eq 6) "六条回归用例源码存在（未运行）: $path"
}
Assert-That ((Read-Source 'tools/check_story_contract.ps1').Contains('services/story''')) '通用剧情规模检查覆盖持久化测试目录'
Write-Output "大木包裹静态检查通过: $script:Checks 项断言。"
Write-Output '仅核对源码、配置、客户端文本和接入约束；未编译、未运行 JUnit、未启动应用、未连接数据库。'
