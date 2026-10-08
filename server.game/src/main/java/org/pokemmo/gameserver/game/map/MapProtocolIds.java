package org.pokemmo.gameserver.game.map;

import org.pokemmo.gameserver.game.region.RegionType;

/**
 * Converts the server map pair to the two map bytes used by the client.
 */
public final class MapProtocolIds {
    private MapProtocolIds() {
    }

    public static int first(int regionId, int mapHeaderOrGroupId, int mapId) {
        return RegionType.isNDS(regionId) ? mapId : mapHeaderOrGroupId;
    }

    public static int second(int regionId, int mapHeaderOrGroupId, int mapId) {
        return RegionType.isNDS(regionId) ? mapHeaderOrGroupId : mapId;
    }
}
