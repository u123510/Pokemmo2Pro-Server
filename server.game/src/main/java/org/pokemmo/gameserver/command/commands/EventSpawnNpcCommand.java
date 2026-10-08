package org.pokemmo.gameserver.command.commands;

import java.io.UncheckedIOException;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.pokemmo.gameserver.command.Command;
import org.pokemmo.gameserver.command.CommandContext;
import org.pokemmo.gameserver.game.entity.EventNpcSpawnRequest;
import org.pokemmo.gameserver.game.entity.NpcSpawnService;

/** Supports manual, unconditional event-category NPC creation from the original editor. */
@Slf4j
public final class EventSpawnNpcCommand implements Command {
    private final NpcSpawnService spawns;

    @Inject
    public EventSpawnNpcCommand(NpcSpawnService spawns) {
        this.spawns = spawns;
    }

    @Override
    public String getName() {
        return "eventspawnnpc";
    }

    @Override
    public String getUsage() {
        return "//eventspawnnpc <事件 0..6> <外观编号> <外观地区> <移动类型> <横范围> <纵范围>"
                + " <脚本 0> <标志 -1> <标志值 -1> <更新 false> <闪光 true|false> <忽略重复 false> <缩放 0.25..4.0> <条件数 0>";
    }

    @Override
    public void execute(CommandContext context, String[] arguments) {
        try {
            EventNpcSpawnRequest request = EventNpcSpawnRequest.parse(arguments);
            NpcSpawnService.Spawned spawned = spawns.spawn(context.getSession(), context.getCharacterManager(),
                    request.npc(), request.appearance());
            context.reply("事件 NPC 已保存并生成: 事件分类=" + request.appearance().eventId()
                    + ", 固定序号=" + spawned.entityIdx() + ", NPC编号=" + spawned.entity().getEntityGameId()
                    + ", 文件=" + spawned.file() + "。已保留事件分类；不会自动开启节日活动或按日期显隐。");
        } catch (IllegalArgumentException exception) {
            context.reply("无法生成事件 NPC: " + exception.getMessage() + "。用法: " + getUsage());
        } catch (UncheckedIOException exception) {
            log.error("事件 NPC 保存失败，未发布在线实体", exception);
            context.reply("事件 NPC 保存失败，未加入在线地图。请检查目录写入权限和日志，勿重复生成。");
        } catch (RuntimeException exception) {
            log.error("事件 NPC 生成或通知异常", exception);
            context.reply("事件 NPC 生成或通知异常，请核对日志和独立配置文件；已保存的文件可重启恢复，勿重复提交。");
        }
    }
}
