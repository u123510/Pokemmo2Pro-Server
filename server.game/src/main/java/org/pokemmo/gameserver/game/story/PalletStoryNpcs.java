package org.pokemmo.gameserver.game.story;

import java.util.HashMap;
import java.util.Map;

import org.pokemmo.gameserver.game.character.CharacterManager;
import org.pokemmo.gameserver.game.entity.NpcEntity;
import org.pokemmo.gameserver.game.map.MapData;
import org.pokemmo.gameserver.protocol.packets.s2c.SendAddGameEntityPacket;
import org.pokemmo.gameserver.protocol.packets.s2c.SendRemoveEntityPacket;

/** A player's story must not modify the shared map's NPC coordinates or load flags. */
public final class PalletStoryNpcs {
    public static final String TOWN = "PalletTown";
    public static final String LAB = "PalletTown_ProfessorOaksLab";
    public static final String HOUSE = "PalletTown_PlayersHouse_1F";

    private PalletStoryNpcs() { }

    public static boolean map(CharacterManager manager, String key) {
        MapData map = manager.getCurrentMapDatas()[0];
        return map != null && map.getRegionIndexId() == 0 && key.equals(map.getMapKey());
    }

    public static NpcEntity project(CharacterManager manager, MapData map, NpcEntity source) {
        if (map.getRegionIndexId() != 0) {
            return baseVisible(source) ? source : null;
        }
        if (!visibleByEventFlag(manager, source)) return null;
        if (!StoryService.enabled(manager)) return baseVisible(source) ? source : null;
        String key = map.getMapKey();
        if (!TOWN.equals(key) && !LAB.equals(key)) return baseVisible(source) ? source : null;
        PalletStoryProgress progress = manager.getPalletStory().progress;
        if (progress == null) return baseVisible(source) ? source : null;
        String name = source.getNpcName();
        boolean visible = baseVisible(source);
        if (TOWN.equals(key) && name.equals("npc_2")) {
            visible = progress.stage() == PalletStoryProgress.ESCORT;
        } else if (LAB.equals(key)) {
            visible = switch (name) {
                case "npc_3" -> progress.stage() >= PalletStoryProgress.ESCORT;
                case "npc_4", "npc_5", "npc_6" -> ballVisible(progress, name);
                case "npc_7" -> !progress.completed() || OakParcelStory.showRival(manager);
                case "npc_8", "npc_9" -> progress.stage() < 6;
                default -> visible;
            };
        }
        if (!visible) return null;
        NpcEntity override = manager.getPalletStory().actors.get(source.getEntityGameId());
        if (override != null) return override;
        NpcEntity copy = copy(source);
        copy.setLoad(true);
        return copy;
    }

    private static boolean visibleByEventFlag(CharacterManager manager, NpcEntity source) {
        if (!baseVisible(source)) return false;
        String flag = source.getHideFlag();
        if (flag == null || flag.isBlank() || manager == null || manager.getCharacterData() == null) {
            return baseVisible(source);
        }
        if (manager.getPalletStory().eventFlags.contains(flag)) {
            return false;
        }
        return switch (flag) {
            case "kanto/FLAG_HIDE_DOME_FOSSIL", "kanto/FLAG_HIDE_HELIX_FOSSIL" ->
                    !storyBit(manager, 6);
            case "kanto/FLAG_HIDE_TOWER_FUJI" -> !storyBit(manager, 0);
            case "kanto/FLAG_HIDE_TOWER_ROCKET_1",
                    "kanto/FLAG_HIDE_TOWER_ROCKET_2",
                    "kanto/FLAG_HIDE_TOWER_ROCKET_3" -> !storyBit(manager, 0);
            case "kanto/FLAG_HIDE_TOWER_RIVAL" -> !extraStoryBit(manager, 4);
            case "kanto/FLAG_HIDE_POKEHOUSE_FUJI" -> storyBit(manager, 0);
            case "kanto/FLAG_HIDE_HIDEOUT_GIOVANNI",
                    "kanto/FLAG_HIDE_SILPH_SCOPE" ->
                    !storyBit(manager, 1);
            case "kanto/FLAG_HIDE_MISC_KANTO_ROCKETS" -> !storyBit(manager, 1);
            case "kanto/FLAG_HIDE_LIFT_KEY" ->
                    manager.getPalletStory().hasEventFlag(
                            "kanto/trainer/RocketHideout_B4F/RocketHideout_B4F_EventScript_Grunt1")
                    && manager.getPalletStory().hasEventFlag(
                            "kanto/trainer/RocketHideout_B4F/RocketHideout_B4F_EventScript_Grunt3")
                    && !extraStoryBit(manager, 2);
            case "kanto/FLAG_HIDE_SILPH_ROCKETS",
                    "kanto/FLAG_HIDE_SAFFRON_ROCKETS" -> !storyBit(manager, 3);
            case "kanto/FLAG_HIDE_SAFARI_ZONE_WEST_GOLD_TEETH" ->
                    !storyBit(manager, 15);
            case "kanto/FLAG_HIDE_POKEMON_MANSION_B1F_SECRET_KEY" ->
                    !storyBit(manager, 4);
            case "kanto/FLAG_HIDE_CERULEAN_ROCKET" -> !extraStoryBit(manager, 5);
            case "kanto/FLAG_HIDE_NUGGET_BRIDGE_ROCKET" -> !storyBit(manager, 8);
            case "kanto/FLAG_HIDE_CERULEAN_RIVAL" -> !storyBit(manager, 7);
            case "kanto/FLAG_HIDE_SS_ANNE_RIVAL" -> !storyBit(manager, 12);
            case "kanto/FLAG_HIDE_CELADON_ROCKETS" -> !storyBit(manager, 1);
            case "kanto/FLAG_HIDE_SAFFRON_CIVILIANS" -> storyBit(manager, 3);
            case "kanto/FLAG_HIDE_VIRIDIAN_GIOVANNI" -> !badgeBit(manager, 7);
            case "kanto/FLAG_HIDE_ROUTE_22_RIVAL" ->
                    (!extraStoryBit(manager, 6) || badgeBit(manager, 7))
                    && !extraStoryBit(manager, 0);
            case "kanto/FLAG_HIDE_SILPH_RIVAL" -> !extraStoryBit(manager, 1);
            default -> baseVisible(source);
        };
    }

    private static boolean baseVisible(NpcEntity source) {
        return source.isLoad() || source.isMapResourceEntity();
    }

    private static boolean storyBit(CharacterManager manager, int bit) {
        short[] flags = manager.getCharacterData().getStoryLineFlag();
        return flags != null && flags.length > 0 && (flags[0] & (1 << bit)) != 0;
    }

    private static boolean badgeBit(CharacterManager manager, int bit) {
        short[] flags = manager.getCharacterData().getBadgeFlag();
        return flags != null && flags.length > 0 && (flags[0] & (1 << bit)) != 0;
    }

    private static boolean extraStoryBit(CharacterManager manager, int bit) {
        short[] flags = manager.getCharacterData().getStoryLineFlag();
        return flags != null && flags.length > 1 && (flags[1] & (1 << bit)) != 0;
    }

    private static boolean ballVisible(PalletStoryProgress progress, String name) {
        if (progress.starter() == 3) return true;
        int choice = switch (name) { case "npc_4" -> 0; case "npc_6" -> 1; default -> 2; };
        return choice != progress.starter() && choice != (progress.starter() + 1) % 3;
    }

    public static NpcEntity actor(CharacterManager manager, int index) {
        MapData map = manager.getCurrentMapDatas()[0];
        NpcEntity base = map.getNpcEntityHashMap().get("npc_" + index);
        if (base == null || base.getEntityGameId() <= 0) throw new IllegalStateException("缺少剧情 NPC: " + index);
        NpcEntity view = project(manager, map, base);
        if (view == null) throw new IllegalStateException("剧情 NPC 当前不可见: " + index);
        return view;
    }

    public static void pose(CharacterManager manager, NpcEntity source, int x, int y, int facing) {
        NpcEntity copy = copy(source);
        copy.setX((short) x);
        copy.setY((short) y);
        copy.setToward((byte) facing);
        copy.setLoad(true);
        Map<Long, NpcEntity> actors = new HashMap<>(manager.getPalletStory().actors);
        actors.put(copy.getEntityGameId(), copy);
        manager.getPalletStory().actors = Map.copyOf(actors);
    }

    public static void refresh(CharacterManager manager) {
        MapData map = manager.getCurrentMapDatas()[0];
        if (map == null) return;
        for (NpcEntity npc : map.getNpcEntityHashMap().values()) {
            NpcEntity view = project(manager, map, npc);
            if (view == null) manager.getCharacterSession().send(new SendRemoveEntityPacket(npc.getEntityGameId()));
            else manager.getCharacterSession().send(new SendAddGameEntityPacket(view));
        }
    }

    private static NpcEntity copy(NpcEntity entity) {
        try {
            return (NpcEntity) entity.clone();
        } catch (CloneNotSupportedException exception) {
            throw new IllegalStateException("无法创建玩家独立剧情实体", exception);
        }
    }
}
