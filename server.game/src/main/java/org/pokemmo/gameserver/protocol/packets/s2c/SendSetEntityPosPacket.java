package org.pokemmo.gameserver.protocol.packets.s2c;

import org.pokemmo.gameserver.game.entity.PlayerEntity;
import org.pokemmo.gameserver.game.map.MapProtocolIds;
import org.server.OutgoingPacket;
import org.server.bytes.ByteBufEx;

public class SendSetEntityPosPacket extends OutgoingPacket {
    private final long entityId;
    private final byte regionIndexId;
    private final byte mapHeaderIdOrGbaMapGroupId;
    private final byte gbaMapId;
    private final short x;
    private final short y;
    private final byte z;
    private final byte toward;
    public SendSetEntityPosPacket(long entityId, int regionIndexId, int mapHeaderIdOrGbaMapGroupId, int gbaMapId, int x, int y, int z, int toward) {
        this.entityId = entityId;
        this.regionIndexId = (byte) regionIndexId;
        this.mapHeaderIdOrGbaMapGroupId = (byte) mapHeaderIdOrGbaMapGroupId;
        this.gbaMapId = (byte) gbaMapId;
        this.x = (short) x;
        this.y = (short) y;
        this.z = (byte) z;
        this.toward = (byte) (toward & 0x03);
    }
    public SendSetEntityPosPacket(PlayerEntity playerEntity) {
        this.entityId = playerEntity.getEntityGameId();
        this.regionIndexId = playerEntity.getRegionIndexId();
        this.mapHeaderIdOrGbaMapGroupId = playerEntity.getMapHeaderIdOrGbaMapGroupId();
        this.gbaMapId = playerEntity.getGbaMapId();
        this.x = playerEntity.getX();
        this.y = playerEntity.getY();
        this.z = playerEntity.getZ();
        this.toward = (byte) (playerEntity.getToward() & 0x03);
    }
    @Override
    public void encode(ByteBufEx buffer) throws Exception {
        buffer.writeLongLE(entityId);
        buffer.writeByte(regionIndexId);
        buffer.writeByte(MapProtocolIds.first(
                regionIndexId, mapHeaderIdOrGbaMapGroupId, gbaMapId));
        buffer.writeByte(MapProtocolIds.second(
                regionIndexId, mapHeaderIdOrGbaMapGroupId, gbaMapId));
        buffer.writeShortLE(x);
        buffer.writeShortLE(y);
        buffer.writeByte(z);
        buffer.writeByte(toward);
    }

}
