package org.pokemmo.gameserver.game.map;

public class SinnohMapData extends NdsMapData {
    private final NdsTerrainPlane terrain;

    public SinnohMapData(
            SinnohMapConfig config,
            MapLightingType lighting,
            MapWeatherType weather,
            MapZoneType zone,
            NdsTerrainPlane terrain
    ) {
        super(
                config.regionId,
                config.bankId,
                config.mapId,
                terrain.getWidth(),
                terrain.getHeight(),
                lighting,
                weather,
                zone,
                true,
                false,
                false
        );
        this.terrain = terrain;
    }

    @Override
    public boolean checkIsWalkable(int x, int y) {
        return terrain.isWalkable(x, y);
    }

    public int getHeaderAt(int x, int y) {
        return terrain.getHeaderAt(x, y);
    }

    public boolean isGrass(int x, int y) {
        return terrain.isGrass(x, y);
    }

    public boolean isWater(int x, int y) {
        return terrain.isWater(x, y);
    }
}
