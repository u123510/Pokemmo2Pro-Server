package org.pokemmo.gameserver.game.character;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import org.pokemmo.gameserver.game.battle.BattleType;
import org.pokemmo.gameserver.game.entity.PlayerEntity;
import org.pokemmo.gameserver.game.map.MapConnectionType;
import org.pokemmo.gameserver.game.map.MapData;
import org.pokemmo.gameserver.game.pokemon.PokemonData;
import org.pokemmo.gameserver.game.pokemon.UpdatePokemonData;
import org.pokemmo.gameserver.game.region.RegionData;
import org.pokemmo.gameserver.protocol.GameProtocol;
import org.pokemmo.gameserver.protocol.packets.s2c.SendChatMessagePacket;
import org.pokemmo.gameserver.protocol.packets.s2c.SendUpdatePokemonDataPacket;
import org.pokemmo.gameserver.script.ScriptManager;
import org.pokemmo.gameserver.services.character.DeathSaveStore;
import org.pokemmo.gameserver.services.pokemon.PokemonHealingService;
import org.server.Session;
import org.server.union.chat.ChatMessage;

/** Coordinates committed Unova checkpoint data with the existing map and party refresh paths. */
@Singleton
@Slf4j
public final class DeathSaveRuntime {
    private final ScriptManager scripts;
    private final DeathSaveStore store;

    @Inject
    public DeathSaveRuntime(ScriptManager scripts, DeathSaveStore store) {
        this.scripts = scripts;
        this.store = store;
    }

    public void onMapReady(CharacterManager manager) {
        if (!isReady(manager) || manager.getBattleManager() != null) return;
        synchronized (manager.getInteractManager()) {
            if (!isReady(manager) || manager.getBattleManager() != null) return;
            PlayerEntity player = manager.getCharacterData().getPlayerEntity();
            try {
                MapData current = manager.getCurrentMapDatas()[MapConnectionType.NOTHING.getType()];
                if (current == null || current.getRegionIndexId() != 2) return;
                DeathSavePoint point = catalog().forSourceMap(current.getMapKey());
                if (point == null || !current.equals(player.getRegionIndexId(),
                        player.getMapHeaderIdOrGbaMapGroupId(), player.getGbaMapId())) return;
                store.save(player.getEntityGameId(), manager.getCharacterData().getAccountId(), point);
            } catch (RuntimeException exception) {
                log.error("合众存盘点保存失败: characterId={}", player.getEntityGameId(), exception);
                notify(manager, "合众死亡存盘点保存失败，请检查服务器日志。");
            }
        }
    }

    /** Returns true when this Unova defeat is handled, including explicit failure paths. */
    public boolean onDefeat(CharacterManager manager) {
        if (!isReady(manager) || manager.getBattleManager() == null
                || !supports(manager.getBattleManager().battleBasisInfo.getBattleType())) return false;
        synchronized (manager.getInteractManager()) {
            if (!isReady(manager) || manager.getBattleManager() == null
                    || !supports(manager.getBattleManager().battleBasisInfo.getBattleType())) return true;
            try {
                restore(manager);
            } catch (RuntimeException exception) {
                log.error("合众死亡回城失败: characterId={}",
                        manager.getCharacterData().getPlayerEntity().getEntityGameId(), exception);
                notify(manager, "合众死亡回城失败，请检查存盘点地图、数据库迁移及服务器日志后重连重试。");
            }
        }
        return true;
    }

    static boolean supports(BattleType type) {
        if (type == null) return false;
        return switch (type) {
            case WildBattle, LowTrainerBattle, AceTrainerBattle, RivalBattle, GymLeaderBattle, EliteBattle -> true;
            default -> false;
        };
    }

    private void restore(CharacterManager manager) {
        PlayerEntity player = manager.getCharacterData().getPlayerEntity();
        DeathSavePoint point = catalog().get(store.load(player.getEntityGameId(),
                manager.getCharacterData().getAccountId()));
        MapData destination = findDestination(point);
        if (destination == null || !destination.checkIsWalkable(point.x(), point.y())) {
            throw new IllegalStateException("合众存盘点目标地图缺失或不可行走: " + point.destinationMap());
        }
        PokemonHealingService.Result result = store.restore(player.getEntityGameId(),
                manager.getCharacterData().getAccountId(), catalog(), point, destination,
                player.getToward(), manager.getPartyPokemons());
        // All persistence has committed before publishing any online changes or packets.
        for (PokemonData restored : result.pokemons()) {
            PokemonData online = manager.getPartyPokemons()[restored.getContainerPosition()];
            if (online == null || online.getPokemonId() != restored.getPokemonId()) {
                throw new IllegalStateException("存盘已完成，但在线队伍发生变化，需要重连");
            }
        }
        player.upDatePos(destination.getRegionIndexId(), destination.getMapHeaderIdOrGBAmapGroupId(),
                destination.getGbaMapId(), point.x(), point.y(), point.z(), player.getToward());
        player.setTransportation((byte) 0);
        for (PokemonData restored : result.pokemons()) {
            PokemonData online = manager.getPartyPokemons()[restored.getContainerPosition()];
            online.setMaxHp(restored.getMaxHp());
            online.setCurrentHp(restored.getCurrentHp());
            online.setMovesPp(restored.getMovesPp().clone());
            online.setPokemonStatus(restored.getPokemonStatus());
        }
        manager.setBattleManager(null);
        for (PokemonData restored : result.pokemons()) {
            PokemonData online = manager.getPartyPokemons()[restored.getContainerPosition()];
            manager.getCharacterSession().send(new SendUpdatePokemonDataPacket(new UpdatePokemonData.Builder()
                    .setUpdatePokemon(online).setIsReloadPokemonMove(true)
                    .setIsReloadPokemonCurrentHp(true).setIsReloadPokemonStatus(true).build()));
        }
        manager.handleReLoadMap(destination).thenAccept(loaded -> {
            if (!loaded) {
                log.warn("合众回城已保存，但客户端地图确认失败: characterId={}, point={}",
                        player.getEntityGameId(), point.id());
                notify(manager, "回城位置已保存，地图加载失败，请重新登录。");
            }
        });
    }

    private MapData findDestination(DeathSavePoint point) {
        RegionData region = scripts.getRegionDatas()[2];
        if (region == null) return null;
        return region.getRegionMaps().values().stream()
                .filter(map -> map.getRegionIndexId() == 2 && point.destinationMap().equals(map.getMapKey()))
                .findFirst().orElse(null);
    }

    private DeathSavePointCatalog catalog() {
        DeathSavePointCatalog catalog = scripts.getDeathSavePointCatalog();
        if (catalog == null) throw new IllegalStateException("未加载合众死亡存盘点目录");
        return catalog;
    }

    private static boolean isReady(CharacterManager manager) {
        if (manager == null || manager.getCharacterData() == null
                || manager.getCharacterData().getPlayerEntity() == null
                || manager.getCharacterData().getPlayerEntity().getRegionIndexId() != 2) return false;
        Session session = manager.getCharacterSession();
        return session != null && session.isActive()
                && session.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get() == manager;
    }

    private static void notify(CharacterManager manager, String message) {
        Session session = manager.getCharacterSession();
        if (session != null && session.isActive()
                && session.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get() == manager) {
            session.send(new SendChatMessagePacket(ChatMessage.gameNotification(message)));
        }
    }
}
