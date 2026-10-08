package org.pokemmo.gameserver.command.commands;

import lombok.extern.slf4j.Slf4j;
import org.pokemmo.gameserver.command.Command;
import org.pokemmo.gameserver.command.CommandContext;
import org.pokemmo.gameserver.game.character.PlayerVisibilityService;

@Slf4j
public final class HideCommand implements Command {
    @Override
    public String getName() {
        return "hide";
    }

    @Override
    public String getUsage() {
        return "//hide";
    }

    @Override
    public void execute(CommandContext context, String[] arguments) {
        if (arguments.length != 0) {
            context.reply("用法: //hide（切换隐身，无需参数）");
            return;
        }
        try {
            boolean hidden = PlayerVisibilityService.toggle(context.getSession(), context.getCharacterManager());
            context.reply(hidden ? "隐身已开启：普通玩家看不到你，自己和 GM 及以上管理员仍可见。再次输入 //hide 关闭。"
                    : "隐身已关闭：已向附近玩家恢复显示。");
        } catch (IllegalArgumentException exception) {
            context.reply("无法切换隐身: " + exception.getMessage());
        } catch (RuntimeException exception) {
            log.error("管理员隐身切换或显示同步异常", exception);
            context.reply("隐身切换或同步异常，请检查服务器日志，不要连续重复提交。");
        }
    }
}
