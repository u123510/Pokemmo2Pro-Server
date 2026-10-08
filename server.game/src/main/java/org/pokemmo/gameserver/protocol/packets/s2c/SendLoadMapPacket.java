package org.pokemmo.gameserver.protocol.packets.s2c;

import org.pokemmo.gameserver.game.map.*;
import org.pokemmo.gameserver.game.region.RegionType;
import org.server.OutgoingPacket;
import org.server.bytes.ByteBufEx;
import org.pokemmo.gameserver.util.Compression;

import java.util.List;

public class SendLoadMapPacket extends OutgoingPacket {
    /**
     * Causes the client to respond with a ConfirmMapLoadPacket.
     */
    private final boolean isDeleteCache;
    private final boolean isReloadPlayer;
    private final byte regionIndexId; // maybe use enum for regionIds
    private final byte mapHeaderIdOrGBAmapGroupId;
    private final byte gbaMapId;
    private final byte currentChannelIndex;
    private final Tile2D[] borderTiles;
    private final int mapWidth;
    private final int mapHeight;
    private final int primaryTileset;
    private final int secondaryTileset;
    private final byte borderWidth;
    private final byte borderHeight;
    private final short romMapIndex;
    private final byte romMapHeaderIndex;
    private final MapLightingType mapLightingType;
    private final MapWeatherType weatherType;
    private final MapZoneType mapZoneType;
    private final MapEncounterType encounterType;
    private final List<MapConnection> mapConnections;
    public SendLoadMapPacket(boolean isDeleteCache, boolean isReloadPlayer, int regionIndexId, int mapHeaderIdOrGBAmapGroupId, int gbaMapId, int currentChannelIndex, int width, int height, int primaryTileset, int secondaryTileset, int borderWidth, int borderHeight,
                             int romMapIndex , int romMapHeaderIndex , MapLightingType mapLightingType, MapWeatherType weatherType, MapZoneType mapZoneType, MapEncounterType encounterType, Tile2D[] borderTiles, List<MapConnection> mapConnections) {
        this.isDeleteCache = isDeleteCache;
        this.isReloadPlayer = isReloadPlayer;
        this.regionIndexId = (byte) regionIndexId;
        this.mapHeaderIdOrGBAmapGroupId = (byte) mapHeaderIdOrGBAmapGroupId;
        this.gbaMapId = (byte) gbaMapId;
        this.currentChannelIndex = (byte) currentChannelIndex;
        this.mapWidth = width;
        this.mapHeight = height;
        this.primaryTileset = primaryTileset;
        this.secondaryTileset = secondaryTileset;
        this.borderWidth = (byte) borderWidth;
        this.borderHeight = (byte) borderHeight;
        this.romMapIndex = (short) romMapIndex;
        this.romMapHeaderIndex = (byte) romMapHeaderIndex;
        assert borderTiles.length == borderWidth * borderHeight;
        assert borderTiles.length == 0 || !RegionType.isNDS(regionIndexId);
        this.mapLightingType = mapLightingType;
        this.weatherType = weatherType;
        this.mapZoneType = mapZoneType;
        this.encounterType = encounterType;
        this.borderTiles = borderTiles;
        this.mapConnections = mapConnections;
    }

    @Override
    public void encode(ByteBufEx buffer) throws Exception {
        byte mapFlag = 0;
        if (isDeleteCache) {
            mapFlag |= 1;
        }
        if (isReloadPlayer) {
            mapFlag |= 2;
        }
        buffer.writeByte(mapFlag);
        buffer.writeByte(regionIndexId);
        buffer.writeByte(MapProtocolIds.first(
                regionIndexId, mapHeaderIdOrGBAmapGroupId, gbaMapId));
        buffer.writeByte(MapProtocolIds.second(
                regionIndexId, mapHeaderIdOrGBAmapGroupId, gbaMapId));
        buffer.writeByte(currentChannelIndex);
        if (RegionType.isNDS(regionIndexId)) {
            buffer.writeShortLE(0);
            byte size = 0;
            buffer.writeByte(size);
            for (byte i = 0; i < size; i++) {
                buffer.writeShortLE(0);
                buffer.writeShortLE(0);
            }
            buffer.writeByte(mapLightingType.getType());
            buffer.writeByte(weatherType.ordinal());
            buffer.writeByte(mapZoneType.getType());
            return;
        }
        buffer.writeIntLE(mapWidth);
        buffer.writeIntLE(mapHeight);
        buffer.writeIntLE(primaryTileset);
        buffer.writeIntLE(secondaryTileset);
        buffer.writeByte(borderWidth);
        buffer.writeByte(borderHeight);
        buffer.writeShortLE(romMapIndex);
        buffer.writeByte(romMapHeaderIndex);
        buffer.writeByte(mapLightingType.getType());
        buffer.writeByte(weatherType.ordinal());
        buffer.writeByte(mapZoneType.getType());
        buffer.writeByte(encounterType.getType()); // unused by client
        for (Tile2D tile : borderTiles) {
            buffer.writeShortLE(tile.encode());
        }
        boolean hasUnknown = false;
        buffer.writeBoolean(hasUnknown);
        if (hasUnknown) {
            int size = 0;
            buffer.writeIntLE(size);
            byte[] unknown = new byte[size];
            buffer.writeBytes(Compression.compress(unknown));
        }
        buffer.writeByte(mapConnections.size());
        for (byte i = 0; i < mapConnections.size(); i++) {
            buffer.writeByte(mapConnections.get(i).getMapConnectionType().getType());//connect toward
            buffer.writeIntLE(mapConnections.get(i).getConnectOffset());//connect offset
            buffer.writeByte(mapConnections.get(i).getConnectMapHeaderIdOrGbaMapGroup());//connect bankId
            buffer.writeByte(mapConnections.get(i).getConnectGbaMapId());//connect mapId
        }
        boolean hasUnknown2 = false;
        buffer.writeBoolean(hasUnknown2);
        if (hasUnknown2) {
            buffer.writeLongLE(0);
            buffer.writeUtf16LE("");
        }
    }
}
