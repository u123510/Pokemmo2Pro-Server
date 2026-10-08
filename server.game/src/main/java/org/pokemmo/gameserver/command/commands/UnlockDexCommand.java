package org.pokemmo.gameserver.command.commands;

import lombok.extern.slf4j.Slf4j;
import org.pokemmo.gameserver.command.Command;
import org.pokemmo.gameserver.command.CommandContext;
import org.pokemmo.gameserver.game.character.CharacterData;
import org.pokemmo.gameserver.game.character.CharacterManager;
import org.pokemmo.gameserver.game.permission.PermissionType;
import org.pokemmo.gameserver.game.pool.GameSessionPool;
import org.pokemmo.gameserver.protocol.packets.s2c.SendLoadDexPacket;
import org.server.Session;

import java.util.BitSet;
import java.util.Set;

/**
 * 管理员专用命令：点亮目标玩家或自身的全部宝可梦图鉴。
 * 覆盖全国图鉴 1~649 编号及服务端加载的 Gen 6~9 自定义宝可梦。
 */
@Slf4j
public final class UnlockDexCommand implements Command {

    @Override
    public String getName() {
        return "unlockdex";
    }

    @Override
    public Set<String> getAliases() {
        return Set.of("alldex", "fulldex");
    }

    @Override
    public PermissionType getRequiredPermission() {
        return PermissionType.ADM;
    }

    @Override
    public String getUsage() {
        return "//unlockdex [玩家名称]";
    }

    @Override
    public void execute(CommandContext context, String[] arguments) {
        if (arguments.length > 1) {
            context.reply("用法: " + getUsage() + "（不填玩家名称表示为自己点亮）");
            return;
        }

        long targetCharacterId;
        String targetName;
        Session targetSession;

        if (arguments.length == 0) {
            CharacterManager manager = context.getCharacterManager();
            if (manager == null || manager.getCharacterData() == null
                    || manager.getCharacterData().getPlayerEntity() == null) {
                context.reply("无法获取当前角色信息");
                return;
            }
            targetCharacterId = manager.getCharacterData().getPlayerEntity().getEntityGameId();
            targetName = manager.getCharacterData().getPlayerEntity().getEntityName();
            targetSession = context.getSession();
        } else {
            targetName = arguments[0];
            CharacterData targetCharacter = context.getGameServerService().getCharacterByName(targetName);
            if (targetCharacter == null || targetCharacter.getPlayerEntity() == null) {
                context.reply("未找到角色: " + targetName);
                return;
            }
            targetCharacterId = targetCharacter.getPlayerEntity().getEntityGameId();
            targetSession = GameSessionPool.getPlayerSessionInPool(targetCharacterId);
        }

        try {
            BitSet[] unlockedBitSets = context.getGameServerService().unlockAllPokemonDex(targetCharacterId);
            int unlockedCount = unlockedBitSets[0].cardinality();

            if (targetSession != null) {
                targetSession.send(new SendLoadDexPacket(unlockedBitSets));
            }

            context.reply("已成功为玩家 [" + targetName + "] 点亮所有图鉴（共计 " + unlockedCount + " 种宝可梦）！"
                    + (targetSession == null ? "（玩家当前离线，数据已落库，下次上线生效）" : "（客户端已实时刷新）"));
        } catch (Exception exception) {
            log.error("点亮图鉴失败: targetCharacterId={}, targetName={}", targetCharacterId, targetName, exception);
            context.reply("点亮图鉴失败，请检查服务器日志: " + exception.getMessage());
        }
    }
}
