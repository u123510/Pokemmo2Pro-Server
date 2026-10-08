package org.pokemmo.gameserver.game.shop;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShopNpcMutationTest {
    @TempDir
    Path shops;

    @Test
    void npcMutationWaitsForExistingTransactionReaders() throws Exception {
        ShopCatalog catalog = new ShopCatalog(shops, List.of());
        CountDownLatch readerEntered = new CountDownLatch(1);
        CountDownLatch releaseReader = new CountDownLatch(1);
        CountDownLatch writerAttempted = new CountDownLatch(1);
        CountDownLatch writerEntered = new CountDownLatch(1);
        var workers = Executors.newFixedThreadPool(2);
        try {
            var reader = workers.submit(() -> catalog.withSnapshot(snapshot -> {
                readerEntered.countDown();
                await(releaseReader);
                return snapshot.version();
            }));
            assertTrue(readerEntered.await(5, TimeUnit.SECONDS));
            var writer = workers.submit(() -> {
                writerAttempted.countDown();
                return catalog.withNpcMutation(() -> {
                    writerEntered.countDown();
                    return true;
                });
            });
            assertTrue(writerAttempted.await(5, TimeUnit.SECONDS));
            assertFalse(writerEntered.await(100, TimeUnit.MILLISECONDS));
            releaseReader.countDown();
            assertEquals(1L, reader.get(5, TimeUnit.SECONDS).longValue());
            assertTrue(writer.get(5, TimeUnit.SECONDS));
        } finally {
            releaseReader.countDown();
            workers.shutdownNow();
        }
    }

    @Test
    void failingMutationReleasesWriteLockAndPreservesCatalogVersion() throws Exception {
        ShopCatalog catalog = new ShopCatalog(shops, List.of());
        assertThrows(IllegalStateException.class, () -> catalog.withNpcMutation(() -> {
            throw new IllegalStateException("模拟保存失败");
        }));
        var worker = Executors.newSingleThreadExecutor();
        try {
            var read = worker.submit(() -> catalog.withSnapshot(snapshot -> snapshot.version()));
            assertEquals(1L, read.get(5, TimeUnit.SECONDS).longValue());
        } finally {
            worker.shutdownNow();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) throw new AssertionError("测试同步超时");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }
}
