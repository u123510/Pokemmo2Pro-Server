package org.pokemmo.gameserver.command.commands;

import java.io.UncheckedIOException;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.pokemmo.gameserver.command.Command;
import org.pokemmo.gameserver.command.CommandContext;
import org.pokemmo.gameserver.game.entity.NpcSpawnRequest;
import org.pokemmo.gameserver.game.entity.NpcSpawnService;

/** GM NPC Tool entry; numeric argument order is client-compatible. */
@Slf4j
public final class SpawnNpcCommand implements Command {
    private final NpcSpawnService spawns;

    @Inject
    public SpawnNpcCommand(NpcSpawnService spawns) {
        this.spawns = spawns;
    }

    @Override
    public String getName() {
        return "spawnnpc";
    }

    @Override
    public String getUsage() {
        return "//spawnnpc <外观编号 0..10000> <脚本偏移 0> <外观地区 0|1|2|3|4|10> <移动类型> <横向范围 0..4> <纵向范围 0..4>";
    }

    @Override
    public void execute(CommandContext context, String[] arguments) {
        if (arguments.length != 6) {
            context.reply("用法: " + getUsage());
            return;
        }
        try {
            int[] values = new int[6];
            for (int i = 0; i < values.length; i++) values[i] = Integer.parseInt(arguments[i]);
            NpcSpawnRequest request = new NpcSpawnRequest(
                    values[0], values[1], values[2], values[3], values[4], values[5]);
            NpcSpawnService.Spawned spawned = spawns.spawn(context.getSession(), context.getCharacterManager(), request);
            context.reply("自定义 NPC 已永久保存并生成: 地图=" + context.getCharacterManager().getCurrentMapDatas()[0].getMapKey()
                    + ", NPC序号=" + spawned.entityIdx() + ", NPC编号=" + spawned.entity().getEntityGameId()
                    + ", 坐标=(" + spawned.entity().getX() + "," + spawned.entity().getY() + "," + spawned.entity().getZ()
                    + ")。本地图所有频道共享，重启自动恢复。独立文件=" + spawned.file());
        } catch (NumberFormatException exception) {
            context.reply("spawnnpc 的六个参数必须都是十进制整数。用法: " + getUsage());
        } catch (IllegalArgumentException exception) {
            context.reply("无法生成 NPC: " + exception.getMessage());
        } catch (UncheckedIOException exception) {
            log.error("自定义 NPC 保存失败，未发布在线实体", exception);
            context.reply("NPC 保存失败，未加入在线地图。请检查日志中的文件路径和写入权限；若目标文件已存在，先核对文件再重启加载，勿重复生成。");
        } catch (RuntimeException exception) {
            log.error("自定义 NPC 保存、生成或通知失败", exception);
            context.reply("NPC 保存、生成或通知异常，请检查日志和独立配置文件；已保存的文件可重启恢复，勿连续重复提交。");
        }
    }
}
