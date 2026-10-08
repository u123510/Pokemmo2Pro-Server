package org.pokemmo.gameserver.game.story;

import java.util.ArrayList;
import java.util.List;

import io.netty.buffer.Unpooled;
import lombok.extern.slf4j.Slf4j;
import org.pokemmo.db.jooq.tables.records.PokemonRecord;
import org.pokemmo.gameserver.game.battle.BattleGenerator;
import org.pokemmo.gameserver.game.battle.BattleManager;
import org.pokemmo.gameserver.game.battle.FactionResultType;
import org.pokemmo.gameserver.game.character.CharacterManager;
import org.pokemmo.gameserver.game.character.UpdateCharacterSelector;
import org.pokemmo.gameserver.game.interact.InteractType;
import org.pokemmo.gameserver.game.pokemon.PokemonData;
import org.pokemmo.gameserver.game.pokemon.PokemonStatusType;
import org.pokemmo.gameserver.game.pool.GameSessionPool;
import org.pokemmo.gameserver.protocol.packets.s2c.SendHasEventPacket;
import org.pokemmo.gameserver.protocol.packets.s2c.SendBattleInitPacket;
import org.pokemmo.gameserver.protocol.packets.s2c.SendUpdatePlayerInfo;
import org.pokemmo.gameserver.services.story.PalletStoryStore;
import org.server.bytes.ByteBufEx;

/** Binds only this opening's battle to its exactly-once checkpoint and reward transaction. */
@Slf4j
public final class PalletStoryBattle {
    private PalletStoryBattle() { }

    static void start(CharacterManager manager) {
        PalletStoryProgress progress = PalletOpeningService.progress(manager);
        if (progress.stage() != PalletStoryProgress.RIVAL || manager.getBattleManager() != null) {
            throw new IllegalStateException("当前不能开始首次劲敌战斗");
        }
        List<PokemonData> team = new ArrayList<>();
        for (PokemonData pokemon : manager.getPartyPokemons()) {
            if (pokemon != null && (pokemon.getEggValue() & 1) == 0) team.add(pokemon);
        }
        if (team.stream().noneMatch(pokemon -> pokemon.getCurrentHp() > 0)) {
            throw new IllegalStateException("队伍没有可战斗的宝可梦，请先找妈妈治疗");
        }
        PalletStoryCatalog catalog = manager.getScriptManager().getPalletStory();
        int species = catalog.starter(progress.starter()).rivalSpecies();
        PokemonData rival = PalletStarterFactory.create(manager, catalog.species(species), false);
        BattleManager battle = BattleGenerator.generatorTrainerRivalBattle(manager.getCharacterSession(),
                0, catalog.trainerModel(), 80, team.toArray(PokemonData[]::new), new PokemonData[]{rival});
        validateInitialPacket(manager, battle);
        manager.getPalletStory().cancelWait();
        PalletStoryScene.closeDialog(manager);
        manager.getInteractManager().setCurrentInteractScript(null);
        manager.getInteractManager().setInteractType(InteractType.NONE);
        manager.getCharacterSession().send(new SendHasEventPacket(false));
        manager.getPalletStory().battle = battle;
        manager.setBattleManager(battle);
        try {
            GameSessionPool.addBattleManagerInPool(PalletOpeningService.characterId(manager), battle);
            battle.handleBattleBegin(manager.getCharacterSession(), false, false);
            log.info("首次劲敌战斗初始化已发送: 角色编号={}, 初始选择={}, 劲敌宝可梦编号={}, 剧情阶段={}",
                    PalletOpeningService.characterId(manager), progress.starter(), species, progress.stage());
        } catch (RuntimeException exception) {
            if (manager.getBattleManager() == battle) manager.setBattleManager(null);
            if (manager.getPalletStory().battle == battle) manager.getPalletStory().battle = null;
            GameSessionPool.removeBattleManagerInPool(battle);
            log.error("首次劲敌战斗初始化失败，已释放战斗引用，保留领取进度: 角色编号={}",
                    PalletOpeningService.characterId(manager), exception);
            throw new IllegalStateException("首次劲敌战斗未能启动，请重新与劲敌交互", exception);
        }
    }

    private static void validateInitialPacket(CharacterManager manager, BattleManager battle) {
        // Session.send logs encoding failures without throwing; validate before publishing the movement lock.
        ByteBufEx buffer = new ByteBufEx(Unpooled.buffer());
        try {
            new SendBattleInitPacket(battle, manager.getCharacterSession(), false, false).encode(buffer);
        } catch (Exception exception) {
            throw new IllegalStateException("首次劲敌战斗封包编码检查失败", exception);
        } finally {
            buffer.release();
        }
    }

    public static boolean settle(CharacterManager manager, BattleManager battle) {
        if (manager == null || manager.getPalletStory().battle != battle) return true;
        synchronized (manager.getInteractManager()) {
            byte faction = battle.getFactionIndexBySession(manager.getCharacterSession());
            if (faction < 0 || faction >= battle.debutFactions.size()) return false;
            FactionResultType result = battle.debutFactions.get(faction).getFactionStatType();
            if (result != FactionResultType.VICTORY && result != FactionResultType.DEFEAT) return false;
            try {
                if (PalletOpeningService.progress(manager).completed()) return true;
                List<PokemonData> restored = new ArrayList<>();
                List<PokemonRecord> records = new ArrayList<>();
                for (PokemonData online : manager.getPartyPokemons()) {
                    if (online == null) continue;
                    PokemonData copy = new PokemonData.Builder().setByRecord(online.toPokemonRecord()).build();
                    if ((copy.getEggValue() & 1) == 0) {
                        copy.setCurrentHp(copy.getMaxHp());
                        copy.setPokemonStatus(PokemonStatusType.NORMAL);
                        for (int i = 0; i < 4; i++) {
                            copy.getMovesPp()[i] = copy.getMoves()[i] == 0 ? 0
                                    : (short) Byte.toUnsignedInt(copy.getPokemonMoveMaxPp(i));
                        }
                    }
                    restored.add(copy);
                    records.add(copy.toPokemonRecord());
                }
                PalletStoryStore.Result saved = manager.getCharacterService().getPalletStoryStore().finishBattle(
                        manager.getCharacterData().getAccountId(), PalletOpeningService.characterId(manager),
                        PalletOpeningService.progress(manager).starter(), records, result == FactionResultType.VICTORY);
                PalletOpeningService.apply(manager, saved.progress());
                manager.getCharacterData().setMoney(saved.money());
                for (PokemonData pokemon : restored) manager.getPartyPokemons()[pokemon.getContainerPosition()] = pokemon;
                return true;
            } catch (RuntimeException exception) {
                log.error("首次劲敌战斗保存失败，保留战斗等待重试: 角色编号={}", PalletOpeningService.characterId(manager), exception);
                PalletOpeningService.notify(manager, "首次战斗进度保存失败，请重连后重试，不会重复发放奖励。");
                return false;
            }
        }
    }

    public static boolean onFinishReply(CharacterManager manager) {
        BattleManager battle = manager.getBattleManager();
        if (battle == null || manager.getPalletStory().battle != battle) return false;
        if (!finishBattle(manager)) return true;
        manager.setBattleManager(null);
        manager.getPalletStory().battle = null;
        try {
            PalletStoryParty.refresh(manager);
            manager.getCharacterSession().send(new SendUpdatePlayerInfo(new UpdateCharacterSelector.Builder()
                    .setCharacterData(manager.getCharacterData()).setRefreshMoney(true).build()));
            PalletOpeningService.notify(manager, "真新镇开场剧情已完成，进度已保存。重新登录不会重复领取或重打开场。");
        } catch (RuntimeException exception) {
            log.error("开场已保存但客户端队伍刷新失败: 角色编号={}", PalletOpeningService.characterId(manager), exception);
            PalletOpeningService.notify(manager, "开场进度已保存，队伍显示刷新失败，请重新登录同步。");
        } finally {
            PalletStoryScene.end(manager);
        }
        return true;
    }

    static boolean finishBattle(CharacterManager manager) {
        BattleManager battle = manager.getBattleManager();
        if (battle == null || manager.getPalletStory().battle != battle) return false;
        if (!settle(manager, battle)) return false;
        manager.setBattleManager(null);
        manager.getPalletStory().battle = null;
        return true;
    }

    /** World reload must not initialize an already resolved tutorial battle as a new fight. */
    static void resumeAfterLogin(CharacterManager manager) {
        BattleManager battle = manager.getPalletStory().battle;
        if (battle == null || manager.getBattleManager() != battle) return;
        byte faction = battle.getFactionIndexBySession(manager.getCharacterSession());
        if (faction < 0 || faction >= battle.debutFactions.size()) return;
        FactionResultType result = battle.debutFactions.get(faction).getFactionStatType();
        if (result != FactionResultType.VICTORY && result != FactionResultType.DEFEAT) return;
        if (settle(manager, battle)) {
            manager.setBattleManager(null);
            manager.getPalletStory().battle = null;
            GameSessionPool.removeBattleManagerInPool(battle);
        }
    }
}
