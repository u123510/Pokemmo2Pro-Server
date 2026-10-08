package org.pokemmo.gameserver.game.map;

import org.pokemmo.gameserver.game.entity.NpcEntity;
import org.server.Session;

/**
 * Shared runtime map data for Nintendo DS regions.
 *
 * <p>Sinnoh resources can carry decoded matrix terrain. Maps
 * without terrain data fall back to their declared bounds.</p>
 */
@lombok.Getter
public class NdsMapData extends MapData {
    private final boolean walkable;
    private final boolean grass;
    private final boolean water;
    private long firstNpcId;

    public NdsMapData(
            int regionId,
            int bankId,
            int mapId,
            int width,
            int height,
            MapLightingType lighting,
            MapWeatherType weather,
            MapZoneType zone,
            boolean walkable,
            boolean grass,
            boolean water
    ) {
        super(
                regionId,
                bankId,
                mapId,
                0,
                0,
                0,
                width,
                height,
                0,
                0,
                0,
                0,
                0,
                0,
                lighting,
                weather,
                zone
        );
        this.walkable = walkable;
        this.grass = grass;
        this.water = water;
    }

    @Override
    public boolean checkIsWalkable(int x, int y) {
        return walkable
                && x >= 0
                && y >= 0
                && x < getMapWidth()
                && y < getMapHeight();
    }

    public boolean isGrass(int x, int y) {
        return checkIsWalkable(x, y) && grass;
    }

    public boolean isWater(int x, int y) {
        return checkIsWalkable(x, y) && water;
    }

    @Override
    public synchronized void loadArroundEntity(Session characterSession) {
        for (NpcEntity entity : getNpcEntityHashMap().values()) {
            if (entity.getEntityGameId() <= 0) {
                entity.setEntityGameId(generateNpcId(characterSession));
            }
        }
    }

    private long generateNpcId(Session session) {
        firstNpcId = session.attr(
                org.pokemmo.gameserver.protocol.GameProtocol.ATTRIBUTE_CHARACTER_MANAGER
        ).get().getSnowflakeIdGenerator().nextId();
        return firstNpcId;
    }
}
