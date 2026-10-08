package org.pokemmo.gameserver.game.entity;

import java.util.HashSet;
import java.util.Set;

import org.pokemmo.gameserver.game.character.CharacterManager;
import org.pokemmo.gameserver.game.map.MapData;
import org.pokemmo.gameserver.game.region.RegionData;
import org.pokemmo.gameserver.game.story.PalletStoryNpcs;
import org.pokemmo.gameserver.protocol.GameProtocol;
import org.pokemmo.gameserver.protocol.packets.s2c.SendAddGameEntityPacket;
import org.server.Session;

/** Shared map audiences and serialized NPC snapshots, including connected maps/all channels. */
public final class NpcVisibilityService {
    private NpcVisibilityService() {
    }

    static Set<Session> viewers(CharacterManager manager, MapData target) {
        Set<Session> result = new HashSet<>();
        result.add(manager.getCharacterSession());
        for (RegionData region : manager.getScriptManager().getRegionDatas()) {
            if (region == null) continue;
            for (MapData map : region.getRegionMaps().values()) {
                for (Session candidate : map.getPlayerSessionPool().values()) {
                    if (canSee(candidate, target)) result.add(candidate);
                }
            }
        }
        return result;
    }

    static boolean canSee(Session session, MapData target) {
        if (session == null || !session.isActive()) return false;
        CharacterManager manager = session.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get();
        if (manager == null || manager.getCharacterSession() != session) return false;
        for (MapData map : manager.getCurrentMapDatas()) {
            if (map != null && map.equals(target)) return true;
        }
        return false;
    }

    static void sendSpawn(Session session, MapData map, NpcEntity npc) {
        synchronized (map) {
            CharacterManager manager = session.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get();
            NpcEntity visible = manager == null ? null : PalletStoryNpcs.project(manager, map, npc);
            if (canSee(session, map) && map.getNpcEntityHashMap().get(npc.getNpcName()) == npc
                    && visible != null) {
                session.send(new SendAddGameEntityPacket(visible));
            }
        }
    }

    public static void sendMapSnapshot(Session session, MapData map) {
        synchronized (map) {
            CharacterManager manager = session.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get();
            for (NpcEntity npc : map.getNpcEntityHashMap().values()) {
                NpcEntity visible = PalletStoryNpcs.project(manager, map, npc);
                if (visible != null) session.send(new SendAddGameEntityPacket(visible));
            }
        }
    }
}
