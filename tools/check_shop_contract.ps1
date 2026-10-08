param([string]$Root = (Split-Path -Parent $PSScriptRoot))

$ErrorActionPreference = 'Stop'
$Root = [IO.Path]::GetFullPath($Root)
$script:Checks = 0

function Assert-That([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw "检查失败: $Message" }
    $script:Checks++
}

function Read-Source([string]$RelativePath) {
    Get-Content -LiteralPath (Join-Path $Root $RelativePath) -Raw
}

function Skip-Bytes([IO.BinaryReader]$Reader, [int]$Count) {
    Assert-That ($Count -ge 0) '二进制数组长度不能为负'
    [void]$Reader.BaseStream.Seek($Count, [IO.SeekOrigin]::Current)
}

# Read the existing ItemDataReader layout without starting Java or a database.
$reader = [IO.BinaryReader]::new([IO.File]::OpenRead((Join-Path $Root 'resource/item/Item.bin')))
$metadata = @{}
try {
    $count = $reader.ReadInt16()
    for ($index = 0; $index -lt $count; $index++) {
        $id = [int]$reader.ReadInt16() -band 0xFFFF
        $flags = $reader.ReadBytes(12)
        $stack = $reader.ReadInt16()
        Skip-Bytes $reader 7
        Skip-Bytes $reader 8
        Skip-Bytes $reader 5
        Skip-Bytes $reader ($reader.ReadInt16() * 2)
        Skip-Bytes $reader 14
        Skip-Bytes $reader ($reader.ReadInt16() * 2)
        Skip-Bytes $reader 28
        $strings = $reader.ReadInt16()
        for ($s = 0; $s -lt $strings; $s++) {
            Skip-Bytes $reader 4
            $formats = $reader.ReadInt16()
            for ($f = 0; $f -lt $formats; $f++) {
                Skip-Bytes $reader 2
                Skip-Bytes $reader ($reader.ReadInt16() * 2)
            }
        }
        $metadata[$id] = @{ Stack = $stack; Type = $flags[1]; Bound = ($flags[5] -ne 0) }
    }
    Assert-That ($reader.BaseStream.Position -eq $reader.BaseStream.Length) 'Item.bin 必须完整消费'
} finally {
    $reader.Dispose()
}

$shops = @{}
$mapFiles = @(Get-ChildItem -LiteralPath (Join-Path $Root 'resource/map') -Recurse -File -Filter '*.json')
$npcOwners = [Collections.Generic.Dictionary[string,string]]::new([StringComparer]::Ordinal)
$shopFiles = @(Get-ChildItem -LiteralPath (Join-Path $Root 'resource/shop') -File -Recurse |
    Where-Object { $_.Extension -in '.json', '.jsonc' })
Assert-That ($shopFiles.Count -gt 0) '至少存在一个店铺配置'
foreach ($file in $shopFiles) {
    $shop = Get-Content -LiteralPath $file.FullName -Raw | ConvertFrom-Json -AsHashtable
    Assert-That ($shop.shopId -cmatch '^[a-z0-9][a-z0-9._-]{0,127}$') "$($file.Name): shopId"
    Assert-That (-not $shops.ContainsKey($shop.shopId)) "重复 shopId: $($shop.shopId)"
    Assert-That ($shop.buyEnabled -is [bool] -and $shop.sellEnabled -is [bool]) '买卖开关必须是布尔值'
    Assert-That ($shop.items.Count -ge 1 -and $shop.items.Count -le 1024) '每店商品数量范围'
    foreach ($field in $shop.Keys) {
        Assert-That ($field -in 'shopId', 'buyEnabled', 'sellEnabled', 'items', 'npcs') "未知店铺字段: $field"
    }
    if ($shop.ContainsKey('npcs')) {
        Assert-That ($shop.npcs -is [array]) 'npcs 必须是数组，不允许 null'
        Assert-That ($shop.npcs.Count -le 1024) 'npcs 数量上限'
        foreach ($binding in $shop.npcs) {
            Assert-That ($binding -is [Collections.IDictionary]) '每个 NPC 绑定必须是对象'
            foreach ($field in $binding.Keys) {
                Assert-That ($field -cin 'map', 'entityIdx') "未知 NPC 绑定字段: $field"
            }
            Assert-That ($binding.map -is [string] -and -not [string]::IsNullOrWhiteSpace($binding.map) -and
                $binding.map -ceq $binding.map.Trim() -and $binding.map.Length -le 128 -and
                $binding.map -notmatch '[/\\]' -and -not $binding.map.EndsWith('.json')) '地图名格式'
            Assert-That (($binding.entityIdx -is [long] -or $binding.entityIdx -is [int]) -and
                $binding.entityIdx -ge 0 -and $binding.entityIdx -le [int]::MaxValue) 'NPC 序号必须是非负整数'
            $matches = @($mapFiles | Where-Object { $_.BaseName -ceq $binding.map })
            Assert-That ($matches.Count -eq 1) "地图不存在或名称不唯一: $($binding.map)"
            $targetMap = Get-Content -LiteralPath $matches[0].FullName -Raw | ConvertFrom-Json -AsHashtable
            $targets = @($targetMap.npcs | Where-Object { $_.entityIdx -eq $binding.entityIdx })
            if ($binding.entityIdx -ge 100000) {
                $regionName = @{0 = 'kanto'; 1 = 'hoenn'; 3 = 'sinnoh'}[[int]$targetMap.regionId]
                $customPath = Join-Path $Root "resource/npc/custom/$regionName/$($binding.map)/npc_$($binding.entityIdx).jsonc"
                if (Test-Path -LiteralPath $customPath) {
                    $custom = Get-Content -LiteralPath $customPath -Raw | ConvertFrom-Json -AsHashtable
                    Assert-That ($custom.regionId -eq $targetMap.regionId -and $custom.map -ceq $binding.map -and
                        $custom.entityIdx -eq $binding.entityIdx) '自定义店员身份与文件路径一致'
                    if ($custom.enabled -eq $true) { $targets += $custom }
                }
            }
            Assert-That ($targets.Count -eq 1) "NPC 不存在或序号重复: $($binding.map)/$($binding.entityIdx)"
            $key = "$($targetMap.regionId)/$($targetMap.bankId)/$($targetMap.mapId)/$($binding.entityIdx)"
            Assert-That (-not $npcOwners.ContainsKey($key)) "NPC 被重复绑定: $key"
            $npcOwners.Add($key, $shop.shopId)
        }
    }
    $seen = [Collections.Generic.HashSet[int]]::new()
    foreach ($item in $shop.items) {
        foreach ($field in $item.Keys) {
            Assert-That ($field -in 'itemId', 'buyPrice', 'sellPrice') "未知商品字段: $field"
        }
        Assert-That ($item.itemId -is [long] -or $item.itemId -is [int]) 'itemId 必须为整数'
        Assert-That ($item.itemId -gt 0 -and $item.itemId -le 65535) 'itemId 范围'
        Assert-That ($seen.Add([int]$item.itemId)) '同店商品不能重复'
        Assert-That ($metadata.ContainsKey([int]$item.itemId)) "未知商品: $($item.itemId)"
        $meta = $metadata[[int]$item.itemId]
        Assert-That ($meta.Stack -gt 0 -and -not $meta.Bound -and $meta.Type -ne 5) '商品基础限制'
        Assert-That ($null -ne $item.buyPrice -or $null -ne $item.sellPrice) '至少存在一个价格'
        foreach ($name in 'buyPrice', 'sellPrice') {
            $price = $item[$name]
            if ($null -ne $price) {
                Assert-That (($price -is [long] -or $price -is [int]) -and
                    $price -gt 0 -and $price -le [int]::MaxValue) "$name 必须为正的 32 位整数"
            }
        }
    }
    $shops[$shop.shopId] = $shop
}

foreach ($city in 'viridian', 'pewter') {
    $capitalized = (Get-Culture).TextInfo.ToTitleCase($city)
    $mapPath = "resource/map/kanto/${city}_city_mart/${capitalized}City_Mart.json"
    $map = Read-Source $mapPath | ConvertFrom-Json -AsHashtable
    # Explicit shop-side bindings were checked above; map-side shopId is optional.
    $clerk = @($map.npcs | Where-Object { $_.script -ceq "${capitalized}City_Mart_EventScript_Clerk" })
    Assert-That ($clerk.Count -eq 1) "$city 原店员资源"
    Assert-That ($clerk[0].x -eq 2 -and $clerk[0].y -eq 3) "$city 店员位置"
    Assert-That (@($map.interactionCounters | Where-Object { $_.x -eq 3 -and $_.y -eq 3 }).Count -eq 1) "$city 柜台"
}

function New-ShopFrame($Shop, [bool]$Extended) {
    $stream = [IO.MemoryStream]::new()
    $writer = [IO.BinaryWriter]::new($stream)
    try {
        $buy = @($Shop.items | Where-Object { $Shop.buyEnabled -and $null -ne $_.buyPrice })
        $sell = @($Shop.items | Where-Object { $Shop.sellEnabled -and $null -ne $_.sellPrice })
        $flags = 0
        if ($Extended) {
            $flags = 0x80
            if ($buy.Count -gt 0) { $flags = $flags -bor 1 }
            if ($sell.Count -gt 0) { $flags = $flags -bor 6 }
        }
        $writer.Write([byte]0x23)
        $writer.Write([byte]0)
        $writer.Write([byte]$flags)
        $writer.Write([byte]0)
        $writer.Write([uint16]$buy.Count)
        foreach ($item in $buy) {
            $writer.Write([uint16]$item.itemId)
            $writer.Write([int16]1)
            $writer.Write([int16]0)
            $writer.Write([int]$item.buyPrice)
        }
        if ($Extended) {
            $writer.Write([byte]1)
            $writer.Write([long]0x0102030405060708)
            $writer.Write([uint16]$sell.Count)
            foreach ($item in $sell) {
                $writer.Write([uint16]$item.itemId)
                $writer.Write([int]$item.sellPrice)
            }
        }
        $writer.Flush()
        $bytes = $stream.ToArray()
        $expected = 6 + 10 * $buy.Count
        if ($Extended) { $expected += 11 + 6 * $sell.Count }
        Assert-That ($bytes.Length -eq $expected -and $bytes.Length -lt 65000) '商店响应长度'
        Assert-That ($bytes[2] -eq $flags) '商店响应标志'
        Assert-That ([BitConverter]::ToUInt16($bytes, 4) -eq $buy.Count) '商品数 little-endian'
        if ($Extended) {
            $tail = 6 + 10 * $buy.Count
            Assert-That ($bytes[$tail] -eq 1) '扩展版本'
            Assert-That ([BitConverter]::ToInt64($bytes, $tail + 1) -eq 0x0102030405060708) '报价编号 little-endian'
            Assert-That ([BitConverter]::ToUInt16($bytes, $tail + 9) -eq $sell.Count) '回收条目数'
        }
        return ,$bytes
    } finally {
        $writer.Dispose()
        $stream.Dispose()
    }
}

foreach ($shop in $shops.Values) {
    [void](New-ShopFrame $shop $false)
    [void](New-ShopFrame $shop $true)
}
$sellOnly = @{
    buyEnabled = $false; sellEnabled = $true
    items = @(@{ itemId = 5004; buyPrice = $null; sellPrice = 100 })
}
$sellFrame = New-ShopFrame $sellOnly $true
Assert-That ($sellFrame[2] -eq 0x86 -and $sellFrame.Length -eq 23) '仅回收窗口'

$base = 'server.game/src/main/java/org/pokemmo/gameserver/'
$gameFiles = @(Get-ChildItem -LiteralPath (Join-Path $Root ($base + 'game/shop')) -Filter '*.java')
$serviceFiles = @(Get-ChildItem -LiteralPath (Join-Path $Root ($base + 'services/shop')) -Filter '*.java')
foreach ($file in @($gameFiles) + @($serviceFiles)) {
    $text = Get-Content -LiteralPath $file.FullName -Raw
    Assert-That (($text -split '\r?\n').Count -le 500) "$($file.Name) 行数"
    Assert-That ($text -notmatch '(?m)^import .*\*;') "$($file.Name) 禁止新增通配导入"
}
foreach ($file in $gameFiles) {
    $text = Get-Content -LiteralPath $file.FullName -Raw
    Assert-That ($text -notmatch 'import org\.jooq|import org\.pokemmo\.db\.Database') '领域层不直接访问 SQL'
}
foreach ($file in $serviceFiles) {
    $text = Get-Content -LiteralPath $file.FullName -Raw
    Assert-That ($text -notmatch 'import org\.server\.Session|protocol\.packets') '事务层不发送封包'
}

$protocol = Read-Source ($base + 'protocol/GameProtocol.java')
Assert-That ($protocol.Contains('DataFlow.CLIENT_TO_SERVER, (byte) 0xDC, ShopControlPacket.class')) 'C2S 扩展注册'
Assert-That ($protocol.Contains('DataFlow.SERVER_TO_CLIENT, (byte) 0xDC, SendShopControlPacket.class')) 'S2C 扩展注册'
Assert-That ($protocol.Contains('DataFlow.CLIENT_TO_SERVER, (byte) 0x23, TradePokemonPacket.class')) '旧交易注册保留'
$decoder = Read-Source ($base + 'protocol/packets/c2s/ShopControlPacket.java')
Assert-That ($decoder.Contains('case ShopRequest.BUY -> 16') -and
    $decoder.Contains('case ShopRequest.SELL -> 22')) '请求长度合同'
Assert-That ($decoder.IndexOf('quoteId = buffer.readLongLE()') -lt
    $decoder.IndexOf('int requestId = buffer.readIntLE()')) '报价在请求序号之前'
$module = Read-Source ($base + 'command/GameCommandModule.java')
Assert-That ($module.Contains('.to(ReloadShopsCommand.class)')) '重载命令注册'
Assert-That ((Read-Source ($base + 'command/Command.java')).Contains('return PermissionType.GM')) '默认 GM 权限'
$catalog = Read-Source ($base + 'game/shop/ShopCatalog.java')
Assert-That ($catalog.Contains('lock.readLock().lock()') -and $catalog.Contains('lock.writeLock().lock()')) '目录版本锁'
Assert-That ($catalog.IndexOf('if (!loaded.errors().isEmpty())') -lt
    $catalog.IndexOf('lock.writeLock().lock()')) '错误重载不能发布'
Assert-That ($catalog.Contains('loaded.shops(), loaded.bindings()')) '绑定与价格一起发布'
$bindingsSource = Read-Source ($base + 'game/shop/ShopNpcBindings.java')
Assert-That ($bindingsSource.Contains('shop != null && shop.npcs() != null ? null : legacy')) '显式绑定列表覆盖旧地图绑定'
Assert-That ($bindingsSource.Contains('rejected.add(previous)') -and
    $bindingsSource.Contains('rejected.add(shop.shopId())')) '冲突双方都拒绝'
Assert-That ($bindingsSource.Contains('Map.copyOf(shopByNpc)')) '绑定索引不可变'
Assert-That ($bindingsSource -notmatch '\.setShopId\(') '不得修改在线 NPC 的旧绑定'
$loaderSource = Read-Source ($base + 'game/shop/ShopConfigLoader.java')
Assert-That ($loaderSource.Contains('readNpcBindings(root)') -and
    $loaderSource.Contains('ShopNpcBindings.compile(shops, maps, origins, errors)')) '配置解析与地图校验已接入'
$scriptsSource = Read-Source ($base + 'script/ScriptManager.java')
Assert-That ($scriptsSource.Contains('new ShopCatalog(shopDirectory, shopMaps)') -and
    $scriptsSource.Contains('shopMaps.addAll(region.getRegionMaps().values())')) '初始化向目录提供已加载地图'
$session = Read-Source ($base + 'game/shop/ShopSession.java')
Assert-That ($session.Contains('lastRequest.equals(request)') -and $session.Contains('RequestState.DUPLICATE')) '重复请求合同'
$service = Read-Source ($base + 'game/shop/ShopService.java')
Assert-That ($service.IndexOf('RequestState.DUPLICATE') -lt $service.IndexOf('transactions.execute(')) '重放先于事务拦截'
Assert-That ($service.Contains('quote.catalogVersion != snapshot.version()')) '版本失效检查'
Assert-That (([regex]::Matches($service, 'snapshot\.resolveShopId\(currentMap\(manager\), npc\)')).Count -eq 2) '开店与买卖共用绑定解析'
$transactions = Read-Source ($base + 'services/shop/ShopTransactions.java')
Assert-That ($transactions.Contains('.transactionResult(') -and $transactions.Contains('.forUpdate()')) '事务与行锁'
Assert-That ($transactions.Contains('OWNED_ITEM.OWNER_ID.eq(ownerId)') -and
    $transactions.Contains('OWNED_ITEM.INVENTORY_ID.eq(inventoryId)')) '背包归属锁定'
foreach ($name in 'ShopPurchase.java', 'ShopSale.java') {
    $text = Read-Source ($base + "services/shop/$name")
    $writes = [regex]::Matches($text, 'tx\.(?:update|deleteFrom)\(OWNED_ITEM\)[\s\S]*?\.execute\(\)')
    Assert-That ($writes.Count -gt 0) "$name 存在道具写入"
    foreach ($write in $writes) {
        foreach ($guard in 'ITEM_ID.eq', 'OWNER_ID.eq', 'INVENTORY_ID.eq', 'ITEM_AMOUNT.eq') {
            Assert-That ($write.Value.Contains($guard)) "$name 缺少 $guard"
        }
    }
}
foreach ($file in 'protocol/packets/c2s/MovePacket.java', 'protocol/packets/c2s/ChangeTwordPacket.java',
    'game/character/CharacterManagerState.java', 'protocol/GameProtocol.java') {
    Assert-That ((Read-Source ($base + $file)).Contains('ShopSessions.close(')) "$file 生命周期清理"
}
Assert-That (-not (Test-Path -LiteralPath (Join-Path $Root 'resource/item/NpcShops.jsonc'))) '旧配置已移除'

Write-Output "静态检查通过: $script:Checks 项断言，$($shops.Count) 家店铺，$count 条道具资源。"
Write-Output '此脚本不编译 Java、不连接数据库，也不替代事务回滚、并发和客户端集成测试。'
