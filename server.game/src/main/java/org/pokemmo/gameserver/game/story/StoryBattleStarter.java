package org.pokemmo.gameserver.game.story;

import io.netty.buffer.Unpooled;
import org.pokemmo.gameserver.game.battle.BattleGenerator;
import org.pokemmo.gameserver.game.battle.BattleManager;
import org.pokemmo.gameserver.game.battle.BattleType;
import org.pokemmo.gameserver.game.character.CharacterManager;
import org.pokemmo.gameserver.game.pokemon.PokemonData;
import org.pokemmo.gameserver.game.trade.TradeManager;
import org.pokemmo.gameserver.game.pool.GameSessionPool;
import org.pokemmo.gameserver.protocol.packets.s2c.SendBattleInitPacket;
import org.pokemmo.gameserver.game.interact.InteractType;
import org.server.Session;
import org.server.bytes.ByteBufEx;

/** Starts an inline trainer team declared by a story node. */
final class StoryBattleStarter {
    private StoryBattleStarter() {
    }

    static void start(CharacterManager manager, String battleId, String battleType,
                      int trainerModel, int money, PokemonData[] opponent) {
        if (manager.getBattleManager() != null || TradeManager.isInTrade(manager)
                || manager.getInteractManager().getInteractType() != InteractType.STORY) {
            throw new IllegalStateException("当前不能开始剧情训练家战斗");
        }
        PokemonData[] playerTeam = manager.getPartyPokemons();
        boolean alive = false;
        for (PokemonData pokemon : playerTeam) {
            if (pokemon != null && pokemon.getCurrentHp() > 0) {
                alive = true;
                break;
            }
        }
        if (!alive) throw new IllegalStateException("队伍没有可战斗的宝可梦");
        BattleType type = switch (battleType) {
            case "LOW_TRAINER" -> BattleType.LowTrainerBattle;
            case "ACE_TRAINER" -> BattleType.AceTrainerBattle;
            case "GYM_LEADER" -> BattleType.GymLeaderBattle;
            case "RIVAL" -> BattleType.RivalBattle;
            default -> throw new IllegalArgumentException("未知剧情训练家战斗类型: " + battleType);
        };
        Session session = manager.getCharacterSession();
        BattleManager battle = BattleGenerator.generatorTrainerBattle(
                session, 0, trainerModel, money, playerTeam, opponent, type);
        validate(manager, battle);
        manager.getPalletStory().cancelWait();
        PalletStoryScene.closeDialog(manager);
        manager.getInteractManager().setCurrentInteractScript(null);
        manager.getInteractManager().setInteractType(InteractType.NONE);
        manager.getCharacterSession().send(new org.pokemmo.gameserver.protocol.packets.s2c.SendHasEventPacket(false));
        manager.getPalletStory().storyBattleId = battleId;
        manager.getPalletStory().storyBattleResult = null;
        manager.setBattleManager(battle);
        try {
            GameSessionPool.addBattleManagerInPool(
                    manager.getCharacterData().getPlayerEntity().getEntityGameId(), battle);
            battle.handleBattleBegin(session, false, false);
        } catch (RuntimeException exception) {
            if (manager.getBattleManager() == battle) manager.setBattleManager(null);
            GameSessionPool.removeBattleManagerInPool(battle);
            manager.getPalletStory().storyBattleId = null;
            throw new IllegalStateException("剧情训练家战斗启动失败", exception);
        }
    }

    private static void validate(CharacterManager manager, BattleManager battle) {
        ByteBufEx buffer = new ByteBufEx(Unpooled.buffer());
        try {
            new SendBattleInitPacket(battle, manager.getCharacterSession(), false, false).encode(buffer);
        } catch (Exception exception) {
            throw new IllegalStateException("剧情训练家战斗封包编码检查失败", exception);
        } finally {
            buffer.release();
        }
    }
}
