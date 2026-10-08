package org.pokemmo.gameserver.command.commands;

import com.google.inject.Inject;
import org.pokemmo.gameserver.command.Command;
import org.pokemmo.gameserver.command.CommandContext;
import org.pokemmo.gameserver.game.shop.ShopCatalog;
import org.pokemmo.gameserver.game.shop.ShopService;

public final class ReloadShopsCommand implements Command {
    private final ShopService shops;

    @Inject
    public ReloadShopsCommand(ShopService shops) {
        this.shops = shops;
    }

    @Override
    public String getName() {
        return "reloadshops";
    }

    @Override
    public String getUsage() {
        return "//reloadshops";
    }

    @Override
    public void execute(CommandContext context, String[] arguments) {
        if (arguments.length != 0) {
            context.reply("用法: " + getUsage());
            return;
        }
        ShopCatalog.ReloadResult result = shops.reload();
        context.reply(result.message() + (result.success()
                ? ": 店铺数量=" + result.count() + ", 版本=" + result.version() : ""));
    }
}
