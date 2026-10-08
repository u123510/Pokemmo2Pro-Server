package org.pokemmo.gameserver.command.commands;

import java.io.UncheckedIOException;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.pokemmo.gameserver.command.Command;
import org.pokemmo.gameserver.command.CommandContext;
import org.pokemmo.gameserver.game.entity.NpcDeleteService;

@Slf4j
public final class EventDeleteNpcCommand implements Command {
    private final NpcDeleteService deletions;

    @Inject
    public EventDeleteNpcCommand(NpcDeleteService deletions) {
        this.deletions = deletions;
    }

    @Override
    public String getName() {
        return "eventdeletenpc";
    }

    @Override
    public String getUsage() {
        return "//eventdeletenpc <当前地图自定义 NPC 的运行时 Object ID>";
    }

    @Override
    public void execute(CommandContext context, String[] arguments) {
        if (arguments.length != 1) {
            context.reply("用法: " + getUsage());
            return;
        }
        try {
            long npcId = Long.parseLong(arguments[0]);
            NpcDeleteService.Deleted result = deletions.delete(context.getSession(), context.getCharacterManager(), npcId);
            context.reply("自定义 NPC 已删除: 地图=" + result.map().getMapKey() + ", 固定序号=" + result.entityIdx()
                    + "。文件保留为 enabled=false，重启不会恢复，原生 NPC 不变。若已绑定商店，请移除对应 npcs 项。文件=" + result.file());
        } catch (NumberFormatException exception) {
            context.reply("NPC编号必须是正的 64 位十进制整数，不是 entityIdx。用法: " + getUsage());
        } catch (IllegalArgumentException exception) {
            context.reply("无法删除 NPC: " + exception.getMessage());
        } catch (UncheckedIOException exception) {
            log.error("自定义 NPC 停用保存失败，未移除在线实体", exception);
            context.reply("NPC 停用保存失败，未移除在线实体；请检查文件权限、外部修改和服务器日志，勿连续重试。");
        } catch (RuntimeException exception) {
            log.error("自定义 NPC 删除或通知异常", exception);
            context.reply("NPC 删除或通知异常，请核对服务器日志、文件 enabled 状态和在线地图，勿重复提交。");
        }
    }
}
