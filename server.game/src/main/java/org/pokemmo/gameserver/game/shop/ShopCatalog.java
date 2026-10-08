package org.pokemmo.gameserver.game.shop;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;
import java.util.function.Supplier;

import lombok.extern.slf4j.Slf4j;
import org.pokemmo.gameserver.game.entity.NpcEntity;
import org.pokemmo.gameserver.game.map.MapData;

/** Readers retain a consistent price version until their transaction has finished. */
@Slf4j
public final class ShopCatalog {
    public record Snapshot(long version, Map<String, ShopDefinition> shops, ShopNpcBindings bindings) {
        public Snapshot {
            shops = Map.copyOf(shops);
        }

        public String resolveShopId(MapData map, NpcEntity npc) {
            return bindings.resolveShopId(map, npc, shops);
        }
    }

    public record ReloadResult(boolean success, long version, int count, String message) {
    }

    private final Path directory;
    private final List<MapData> maps;
    private final ShopConfigLoader loader = new ShopConfigLoader();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private Snapshot snapshot;

    public ShopCatalog(Path directory, List<MapData> maps) {
        this.directory = directory.toAbsolutePath().normalize();
        this.maps = List.copyOf(maps);
        ShopConfigLoader.Loaded initial = loader.load(this.directory, this.maps);
        initial.errors().forEach(error -> log.error("店铺配置加载错误: {}", error));
        snapshot = new Snapshot(1, initial.shops(), initial.bindings());
        log.info("店铺目录加载完成: 目录={}, 店铺数量={}, 错误数量={}",
                this.directory, snapshot.shops().size(), initial.errors().size());
        snapshot.shops().forEach((shopId, definition) ->
                log.info("店铺配置已启用: shopId={}, 商品数量={}, 可买={}, 可卖={}",
                        shopId, definition.items().size(),
                        definition.buyEnabled(), definition.sellEnabled()));
        logBindings();
    }

    public <T> T withSnapshot(Function<Snapshot, T> action) {
        lock.readLock().lock();
        try {
            return action.apply(snapshot);
        } finally {
            lock.readLock().unlock();
        }
    }

    public synchronized ReloadResult reload() {
        ShopConfigLoader.Loaded loaded = loader.load(directory, maps);
        if (!loaded.errors().isEmpty()) {
            loaded.errors().forEach(error -> log.error("店铺重载错误，保留旧配置: {}", error));
            return withSnapshot(current -> new ReloadResult(false, current.version(), current.shops().size(),
                    "店铺重载失败，旧配置保持不变: " + loaded.errors().get(0)));
        }
        lock.writeLock().lock();
        try {
            snapshot = new Snapshot(Math.incrementExact(snapshot.version()), loaded.shops(), loaded.bindings());
            log.info("店铺目录重载成功: 版本={}, 店铺数量={}", snapshot.version(), snapshot.shops().size());
            logBindings();
            return new ReloadResult(true, snapshot.version(), snapshot.shops().size(), "店铺重载成功");
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** Excludes all shop opens/transactions while a merchant is durably removed. */
    public <T> T withNpcMutation(Supplier<T> action) {
        lock.writeLock().lock();
        try {
            return action.get();
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void logBindings() {
        snapshot.shops().forEach((shopId, definition) -> {
            if (definition.npcs() == null) {
                log.info("店铺 NPC 绑定使用旧地图配置: 店铺={}", shopId);
            } else if (definition.npcs().isEmpty()) {
                log.info("店铺未绑定 NPC，旧地图绑定已停用: 店铺={}", shopId);
            } else {
                definition.npcs().forEach(binding ->
                        log.info("店铺 NPC 绑定已启用: 店铺={}, 地图={}, NPC序号={}",
                                shopId, binding.map(), binding.entityIdx()));
            }
        });
    }
}
