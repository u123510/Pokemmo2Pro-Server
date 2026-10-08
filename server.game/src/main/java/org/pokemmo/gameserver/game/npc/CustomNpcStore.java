package org.pokemmo.gameserver.game.npc;

import java.io.IOException;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import lombok.extern.slf4j.Slf4j;

/** One file per custom NPC; never opens a resource/map file for writing. */
@Slf4j
final class CustomNpcStore {
    private static final int MAX_FILE_BYTES = 16384;
    private static final int MAX_FILES = 10000;
    private final Path directory;

    record Located(Path file, CustomNpcDefinition definition) {
    }

    CustomNpcStore(Path directory) {
        this.directory = directory.toAbsolutePath().normalize();
    }

    List<Located> readAll() throws IOException {
        checkPath(directory);
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) return List.of();
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) throw new IOException("自定义 NPC 路径不是目录");
        List<Located> result = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(directory)) {
            for (Path file : paths.sorted().toList()) {
                if (Files.isSymbolicLink(file)) throw new IOException("自定义 NPC 目录不允许符号链接: " + file);
                if (!file.getFileName().toString().endsWith(".jsonc")) continue;
                checkPath(file);
                if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.size(file) > MAX_FILE_BYTES) {
                    throw new IOException("自定义 NPC 配置不是普通文件或超过 16 KiB: " + file);
                }
                try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    CustomNpcDefinition definition = CustomNpcCodec.read(reader);
                    if (!file.equals(pathFor(definition))) {
                        throw new IllegalArgumentException("文件路径必须与地区、地图、序号一致: " + pathFor(definition));
                    }
                    result.add(new Located(file, definition));
                } catch (IOException | RuntimeException exception) {
                    throw new IOException("自定义 NPC 文件解析失败: " + file + ": " + exception.getMessage(), exception);
                }
                if (result.size() > MAX_FILES) throw new IOException("自定义 NPC 文件数量超过 10000");
            }
        }
        return result;
    }

    synchronized Path saveNew(CustomNpcDefinition definition) throws IOException {
        return write(definition, null);
    }

    synchronized Path disable(CustomNpcDefinition expected) throws IOException {
        return write(expected.disabled(), expected);
    }

    private Path write(CustomNpcDefinition definition, CustomNpcDefinition expected) throws IOException {
        Path target = pathFor(definition);
        checkPath(target);
        Files.createDirectories(target.getParent());
        checkPath(target);
        Path lockPath = directory.resolve(".write.lock");
        checkPath(lockPath);
        try (FileChannel lockChannel = FileChannel.open(lockPath, StandardOpenOption.CREATE,
                StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
             FileLock lock = lockChannel.tryLock()) {
            if (lock == null) throw new IOException("另一个进程正在保存自定义 NPC，请稍后重试");
            if (expected == null) {
                if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("自定义 NPC 文件已存在，禁止覆盖: " + target);
                }
            } else {
                if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS) || Files.size(target) > MAX_FILE_BYTES) {
                    throw new IOException("待停用的自定义 NPC 文件缺失或无效: " + target);
                }
                try (Reader reader = Files.newBufferedReader(target, StandardCharsets.UTF_8)) {
                    CustomNpcDefinition current = CustomNpcCodec.read(reader);
                    if (current.equals(definition)) return target;
                    if (!current.equals(expected)) {
                        throw new IOException("自定义 NPC 文件已被外部修改，拒绝覆盖，请核对后重启: " + target);
                    }
                }
            }
            Path temporary = Files.createTempFile(target.getParent(), ".npc-", ".tmp");
            try {
                byte[] data = CustomNpcCodec.write(definition).getBytes(StandardCharsets.UTF_8);
                try (FileChannel output = FileChannel.open(temporary, StandardOpenOption.WRITE,
                        StandardOpenOption.TRUNCATE_EXISTING, LinkOption.NOFOLLOW_LINKS)) {
                    ByteBuffer buffer = ByteBuffer.wrap(data);
                    while (buffer.hasRemaining()) output.write(buffer);
                    output.force(true);
                }
                // No non-atomic fallback: an unsupported filesystem is an explicit save failure.
                if (expected == null) {
                    Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
                } else {
                    Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException exception) {
                    log.warn("自定义 NPC 临时文件清理失败: 文件={}", temporary, exception);
                }
            }
        } catch (OverlappingFileLockException exception) {
            throw new IOException("自定义 NPC 保存锁已被占用", exception);
        }
        return target;
    }

    Path pathFor(CustomNpcDefinition definition) {
        return directory.resolve(definition.regionName()).resolve(definition.map())
                .resolve(definition.npcName() + ".jsonc").normalize();
    }

    boolean exists(CustomNpcDefinition definition) {
        return Files.exists(pathFor(definition), LinkOption.NOFOLLOW_LINKS);
    }

    private void checkPath(Path target) throws IOException {
        if (!target.toAbsolutePath().normalize().startsWith(directory)) {
            throw new IOException("自定义 NPC 路径越过独立配置目录");
        }
        for (Path current = target; current != null; current = current.getParent()) {
            if (Files.isSymbolicLink(current)) throw new IOException("自定义 NPC 路径不允许符号链接: " + current);
        }
        if (Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            Path realRoot = directory.toRealPath();
            for (Path current = target; current != null && current.startsWith(directory); current = current.getParent()) {
                if (Files.exists(current, LinkOption.NOFOLLOW_LINKS) && !current.toRealPath().startsWith(realRoot)) {
                    throw new IOException("自定义 NPC 路径重定向到配置目录之外: " + current);
                }
            }
        }
    }
}
