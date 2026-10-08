package org.pokemmo.gameserver.game.interact;

import io.netty.buffer.Unpooled;
import lombok.extern.slf4j.Slf4j;
import org.pokemmo.gameserver.game.battle.BattleGenerator;
import org.pokemmo.gameserver.game.battle.BattleManager;
import org.pokemmo.gameserver.game.battle.BattleType;
import org.pokemmo.gameserver.game.character.CharacterManager;
import org.pokemmo.gameserver.game.entity.NpcEntity;
import org.pokemmo.gameserver.game.entity.PlayerEntity;
import org.pokemmo.gameserver.game.map.MapData;
import org.pokemmo.gameserver.game.story.PalletOpeningService;
import org.pokemmo.gameserver.game.story.PalletStoryNpcs;
import org.pokemmo.gameserver.game.trade.TradeManager;
import org.pokemmo.gameserver.game.pool.GameSessionPool;
import org.pokemmo.gameserver.game.trainer.TrainerBattleTeam;
import org.pokemmo.gameserver.game.trainer.TrainerLevelType;
import org.pokemmo.gameserver.game.trainer.TrainerTeamData;
import org.pokemmo.gameserver.protocol.packets.s2c.SendBattleInitPacket;
import org.pokemmo.gameserver.protocol.packets.s2c.SendHasEventPacket;
import org.server.Session;
import org.server.bytes.ByteBufEx;

import java.time.Duration;

/** Starts ordinary map trainer battles from the generated FireRed catalog. */
@Slf4j
public final class TrainerInteractionService {
    private static final Duration TRAINER_REMATCH_COOLDOWN = Duration.ofHours(8);

    private TrainerInteractionService() {
    }

    public static boolean tryStart(CharacterManager manager, NpcEntity npc) {
        if (manager == null || npc == null || !npc.isTrainer()
                || manager.getBattleManager() != null
                || manager.getPalletStory().isProgressLoadFailed()
                || manager.getScriptManager().getTrainerTeamManager() == null) {
            return false;
        }
        TrainerTeamData definition = manager.getScriptManager().getTrainerTeamManager()
                .getTrainerTeamByScript(npc.getInteractionScriptName());
        var binding = manager.getScriptManager().getTrainerTeamManager()
                .getTrainerNpcBindingByScript(npc.getInteractionScriptName());
        if (definition == null || definition.getTrainerBattleTeams() == null
                || definition.getTrainerBattleTeams().isEmpty()
                || binding == null || binding.sightRange() <= 0) {
            return false;
        }
        String defeatedFlag = "kanto/trainer/" + manager.getCurrentMapDatas()[0].getMapKey()
                + "/" + npc.getInteractionScriptName();
        if (manager.getPalletStory().hasEventFlag(defeatedFlag)) {
            if (manager.getCharacterService() == null) return true;
            var flags = manager.getCharacterService().getStoryEventFlagStore();
            if (!flags.cooldownElapsed(
                    manager.getCharacterData().getPlayerEntity().getEntityGameId(),
                    defeatedFlag, TRAINER_REMATCH_COOLDOWN)) {
                long seconds = flags.remainingSeconds(
                        manager.getCharacterData().getPlayerEntity().getEntityGameId(),
                        defeatedFlag, TRAINER_REMATCH_COOLDOWN);
                long minutes = Math.max(1, (seconds + 59) / 60);
                PalletOpeningService.notify(manager,
                        "这名训练家暂时不想再次对战，请约 " + minutes + " 分钟后再来挑战。");
                return true;
            }
        }
        return startDefinition(manager, definition, () ->
                markTrainerBattle(manager, defeatedFlag));
    }

    public static boolean startDefinition(CharacterManager manager, TrainerTeamData definition) {
        return startDefinition(manager, definition, null);
    }

    public static boolean startDefinition(CharacterManager manager, TrainerTeamData definition,
                                          Runnable beforeBegin) {
        if (manager == null || definition == null || manager.getBattleManager() != null
                || definition.getTrainerBattleTeams() == null
                || definition.getTrainerBattleTeams().isEmpty()) {
            return false;
        }
        TrainerBattleTeam selected = definition.getTrainerBattleTeams().get(0);
        if (selected == null || selected.getTrainerPokemonDatas() == null
                || selected.getTrainerPokemonDatas().length == 0) {
            return false;
        }
        BattleType battleType = battleType(definition.getTrainerLevelType());
        if (battleType == null) {
            return false;
        }
        Session session = manager.getCharacterSession();
        BattleManager battle = BattleGenerator.generatorTrainerBattle(
                session,
                definition.getTrainerRegionIndexId(),
                definition.getTrainerNameIndexId(),
                definition.getMoney(),
                manager.getPartyPokemons(),
                selected.convertPokemonTeamData(session),
                battleType);
        validate(battle, session);
        manager.getInteractManager().setCurrentInteractScript(null);
        manager.getInteractManager().setInteractType(InteractType.NONE);
        session.send(new SendHasEventPacket(false));
        manager.setBattleManager(battle);
        try {
            GameSessionPool.addBattleManagerInPool(
                    manager.getCharacterData().getPlayerEntity().getEntityGameId(), battle);
            if (beforeBegin != null) {
                beforeBegin.run();
            }
            battle.handleBattleBegin(session, false, false);
            return true;
        } catch (RuntimeException exception) {
            if (manager.getBattleManager() == battle) {
                manager.setBattleManager(null);
            }
            String flag = manager.getPalletStory().getNormalTrainerBattleFlag();
            if (flag != null && manager.getCharacterService() != null) {
                manager.getCharacterService().getStoryEventFlagStore().clearEventFlag(
                        manager.getCharacterData().getPlayerEntity().getEntityGameId(), flag);
                manager.getPalletStory().removeEventFlag(flag);
            }
            manager.getPalletStory().setNormalTrainerBattleFlag(null);
            GameSessionPool.removeBattleManagerInPool(battle);
            throw new IllegalStateException("普通训练家战斗启动失败", exception);
        }
    }

    public static boolean startPendingBattle(CharacterManager manager) {
        if (manager == null || manager.getBattleManager() != null) {
            return false;
        }
        TrainerTeamData definition = manager.getPalletStory().getPendingTrainerBattleDefinition();
        String flag = manager.getPalletStory().getPendingTrainerBattleFlag();
        if (definition == null || flag == null) {
            return false;
        }
        manager.getPalletStory().clearPendingTrainerBattle();
        return startDefinition(manager, definition,
                () -> manager.getPalletStory().setNormalTrainerBattleFlag(flag));
    }

    public static boolean onPlayerStep(CharacterManager manager) {
        if (manager == null || manager.getCharacterData() == null
                || manager.getCharacterData().getPlayerEntity() == null
                || manager.getBattleManager() != null
                || manager.getPalletStory().isProgressLoadFailed()
                || manager.getPalletStory().getPendingTrainerBattleDefinition() != null
                || manager.getInteractManager().getInteractType() != InteractType.NONE
                || TradeManager.isInTrade(manager)) {
            return false;
        }
        MapData map = manager.getCurrentMapDatas()[0];
        if (map == null) return false;
        PlayerEntity player = manager.getCharacterData().getPlayerEntity();
        NpcEntity target = null;
        int targetDistance = Integer.MAX_VALUE;
        for (NpcEntity source : map.getNpcEntityHashMap().values()) {
            if (!source.isTrainer() || source.getInteractionScriptName() == null) continue;
            NpcEntity view = PalletStoryNpcs.project(manager, map, source);
            if (view == null || !inSight(map, player, view)) continue;
            String defeatedFlag = trainerFlag(map, source);
            if (manager.getPalletStory().hasEventFlag(defeatedFlag)) continue;
            int distance = Math.abs(view.getX() - player.getX())
                    + Math.abs(view.getY() - player.getY());
            if (distance < targetDistance) {
                target = source;
                targetDistance = distance;
            }
        }
        if (target == null) return false;
        TrainerTeamData definition = manager.getScriptManager().getTrainerTeamManager()
                .getTrainerTeamByScript(target.getInteractionScriptName());
        if (definition == null) return false;
        String defeatedFlag = trainerFlag(map, target);
        manager.getPalletStory().setPendingTrainerBattle(definition, defeatedFlag);
        log.debug("训练家视线触发已等待客户端确认: 角色={}, 训练家={}, flag={}",
                manager.getCharacterData().getPlayerEntity().getEntityGameId(),
                target.getInteractionScriptName(), defeatedFlag);
        return true;
    }

    public static void onBattleFinished(CharacterManager manager, BattleManager battle) {
        String defeatedFlag = manager.getPalletStory().getNormalTrainerBattleFlag();
        if (defeatedFlag == null || manager.getBattleManager() != battle) return;
        byte faction = battle.getFactionIndexBySession(manager.getCharacterSession());
        if (faction < 0 || faction >= battle.debutFactions.size()) return;
        var result = battle.debutFactions.get(faction).getFactionStatType();
        if (result == org.pokemmo.gameserver.game.battle.FactionResultType.VICTORY) {
            markTrainerBattle(manager, defeatedFlag);
        }
        if (result != org.pokemmo.gameserver.game.battle.FactionResultType.IN_BATTLE) {
            manager.getPalletStory().setNormalTrainerBattleFlag(null);
        }
    }

    private static BattleType battleType(TrainerLevelType type) {
        if (type == null) return null;
        return switch (type) {
            case LowTrainer -> BattleType.LowTrainerBattle;
            case AceTrainer -> BattleType.AceTrainerBattle;
            case RivalTrainer -> BattleType.RivalBattle;
            case GymLeaderTrainer -> BattleType.GymLeaderBattle;
            case EliteTrainer -> BattleType.EliteBattle;
        };
    }

    private static String trainerFlag(MapData map, NpcEntity npc) {
        return "kanto/trainer/" + map.getMapKey() + "/" + npc.getInteractionScriptName();
    }

    private static void markTrainerBattle(CharacterManager manager, String flag) {
        long characterId = manager.getCharacterData().getPlayerEntity().getEntityGameId();
        if (manager.getCharacterService() != null) {
        manager.getCharacterService().getStoryEventFlagStore().setEventFlag(characterId, flag);
        }
        manager.getPalletStory().addEventFlag(flag);
        manager.getPalletStory().setNormalTrainerBattleFlag(flag);
    }

    private static boolean inSight(MapData map, PlayerEntity player, NpcEntity npc) {
        int direction = Byte.toUnsignedInt(npc.getToward());
        int dx = 0;
        int dy = 0;
        switch (direction) {
            case 0 -> dy = 1;
            case 1 -> dy = -1;
            case 2 -> dx = -1;
            case 3 -> dx = 1;
            default -> {
                return false;
            }
        }
        int distanceX = player.getX() - npc.getX();
        int distanceY = player.getY() - npc.getY();
        int distance = Math.abs(distanceX) + Math.abs(distanceY);
        int rawRange = Byte.toUnsignedInt(npc.getTrainAggroRange());
        if (rawRange <= 0) return false;
        int range = rawRange;
        if (distance < 1 || distance > range) return false;
        if ((dx != 0 && distanceY != 0) || (dy != 0 && distanceX != 0)) return false;
        for (int step = 1; step < distance; step++) {
            int x = npc.getX() + dx * step;
            int y = npc.getY() + dy * step;
            if (!map.checkIsWalkable(x, y)) return false;
        }
        return (dx == 0 || Integer.signum(distanceX) == dx)
                && (dy == 0 || Integer.signum(distanceY) == dy);
    }

    private static void validate(BattleManager battle, Session session) {
        ByteBufEx buffer = new ByteBufEx(Unpooled.buffer());
        try {
            new SendBattleInitPacket(battle, session, false, false).encode(buffer);
        } catch (Exception exception) {
            throw new IllegalStateException("普通训练家战斗封包编码检查失败", exception);
        } finally {
            buffer.release();
        }
    }
}
