package org.pokemmo.gameserver.game.map;

public class MapHashUtils {
    public static int getHash(int regionInedxId, int mapHeaderIdOrGBAmapGroupId, int gbaMapId) {
        return (byte)regionInedxId | ((byte)(mapHeaderIdOrGBAmapGroupId & 255) << 8) | ((byte)(gbaMapId & 255) << 16);
    }
}
