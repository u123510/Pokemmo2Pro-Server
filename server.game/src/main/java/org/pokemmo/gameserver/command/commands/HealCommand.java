package org.pokemmo.gameserver.command.commands;

import java.util.concurrent.CompletableFuture;

import lombok.extern.slf4j.Slf4j;
import org.pokemmo.gameserver.command.Command;
import org.pokemmo.gameserver.command.CommandContext;
import org.pokemmo.gameserver.game.battle.BattleRequestManager;
import org.pokemmo.gameserver.game.character.CharacterManager;
import org.pokemmo.gameserver.game.interact.InteractType;
import org.pokemmo.gameserver.game.permission.PermissionType;
import org.pokemmo.gameserver.game.pokemon.PokemonData;
import org.pokemmo.gameserver.game.pokemon.PokemonManager;
import org.pokemmo.gameserver.game.pokemon.UpdatePokemonData;
import org.pokemmo.gameserver.game.trade.TradeManager;
import org.pokemmo.gameserver.protocol.GameProtocol;
import org.pokemmo.gameserver.protocol.packets.s2c.SendCharacterPokemonAbilityPacket;
import org.pokemmo.gameserver.protocol.packets.s2c.SendUpdatePokemonDataPacket;
import org.pokemmo.gameserver.services.pokemon.PokemonHealingService;
import org.server.Session;

@Slf4j
public final class HealCommand implements Command {
    @Override
    public String getName() {
        return "heal";
    }

    @Override
    public String getUsage() {
        return "//heal";
    }

    @Override
    public void execute(CommandContext context, String[] arguments) {
        if (arguments.length != 0) {
            context.reply("用法: //heal（恢复自己的整支队伍，无需参数）");
            return;
        }
        try {
            CharacterManager manager = context.getCharacterManager();
            validateContext(context);
            // Withdraw outgoing invitations before restoring, so an old acceptance cannot start a trade.
            BattleRequestManager.cancelFor(manager);
            TradeManager.cancelPendingFor(manager);
            synchronized (manager.getInteractManager()) {
                validateContext(context);
                restoreParty(context);
            }
        } catch (IllegalArgumentException exception) {
            context.reply("无法恢复队伍: " + exception.getMessage());
        } catch (RuntimeException exception) {
            log.error("管理员队伍恢复或客户端同步异常", exception);
            context.reply("队伍恢复或同步异常，请检查服务器日志");
        }
    }

    private void restoreParty(CommandContext context) {
        CharacterManager manager = context.getCharacterManager();
        long characterId = manager.getCharacterData().getPlayerEntity().getEntityGameId();
        PokemonHealingService.Result result = context.getGameServerService()
                .healParty(characterId, manager.getPartyPokemons());
        if (!result.success()) {
            context.reply("无法恢复队伍: " + result.reason());
            return;
        }

        // Preserve existing party object references; only committed HP/PP and the derived maximum change.
        for (PokemonData restored : result.pokemons()) {
            PokemonData online = manager.getPartyPokemons()[restored.getContainerPosition()];
            if (online == null || online.getPokemonId() != restored.getPokemonId()) {
                throw new IllegalStateException("队伍已保存，但在线槽位发生变化，需要重新登录同步");
            }
        }
        for (PokemonData restored : result.pokemons()) {
            PokemonData online = manager.getPartyPokemons()[restored.getContainerPosition()];
            online.setMaxHp(restored.getMaxHp());
            online.setCurrentHp(restored.getCurrentHp());
            online.setMovesPp(restored.getMovesPp().clone());
        }
        Session session = manager.getCharacterSession();
        if (session == null || !session.isActive()
                || session.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get() != manager) {
            log.warn("队伍已恢复保存，连接已失效，等待重新登录同步: 角色编号={}", characterId);
            return;
        }
        for (PokemonData restored : result.pokemons()) {
            PokemonData online = manager.getPartyPokemons()[restored.getContainerPosition()];
            session.send(new SendUpdatePokemonDataPacket(new UpdatePokemonData.Builder()
                    .setUpdatePokemon(online)
                    .setIsReloadPokemonMove(true)
                    .setIsReloadPokemonCurrentHp(true)
                    .build()));
        }
        PokemonData leader = manager.getPartyPokemons()[0];
        if (leader != null && (leader.getEggValue() & 1) == 0 && leader.getCurrentHp() > 0
                && PokemonManager.outBattleAbility.contains(leader.getPokemonAbilityIndexId())) {
            session.send(new SendCharacterPokemonAbilityPacket(leader.getPokemonAbilityIndexId()));
        }
        log.info("管理员队伍恢复完成: 角色编号={}, 宝可梦数量={}, 恢复项目=生命值和招式PP",
                characterId, result.pokemons().size());
        context.reply("队伍恢复完成：" + result.pokemons().size() + " 只宝可梦的生命值和全部招式 PP 已恢复至上限。");
    }

    private void validateContext(CommandContext context) {
        CharacterManager manager = context.getCharacterManager();
        if (manager == null || manager.getCharacterData() == null
                || manager.getCharacterData().getPlayerEntity() == null) {
            throw new IllegalArgumentException("角色尚未就绪");
        }
        PermissionType permission = manager.getCharacterData().getPlayerEntity().getPermission();
        if (permission == null || permission.getType() < PermissionType.GM.getType()) {
            throw new IllegalArgumentException("只有 GM 及以上管理员可以使用 //heal");
        }
        Session session = context.getSession();
        if (session == null || !session.isActive() || manager.getCharacterSession() != session
                || session.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get() != manager) {
            throw new IllegalArgumentException("当前角色会话已失效");
        }
        if (manager.getBattleManager() != null || TradeManager.isInTrade(manager)
                || manager.getInteractManager().getInteractType() != InteractType.NONE) {
            throw new IllegalArgumentException("战斗、观战、交易或场景交互期间不能恢复队伍");
        }
        CompletableFuture<Boolean> loading = manager.getMapLoadFuture();
        if (loading != null && (!loading.isDone() || loading.isCompletedExceptionally()
                || !Boolean.TRUE.equals(loading.getNow(false)))) {
            throw new IllegalArgumentException("地图尚未加载完成");
        }
    }
}
