package org.pokemmo.gameserver.codecs;

import org.server.bytes.ByteBufEx;
import org.pokemmo.db.jooq.tables.records.OwnedItemRecord;
import java.time.ZoneOffset;

public class ItemCodec implements RecordCodec<OwnedItemRecord> {
    @Override
    public void decode(ByteBufEx buffer, OwnedItemRecord object) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void encode(ByteBufEx buffer, OwnedItemRecord object) {
        byte flag = 0;
        boolean isUseColor = object.getColorId() !=-1;
        boolean isLimitRegion = object.getItemRegionIndexId() !=-1;
        boolean isPvpReward = object.getPvpRewardLevel() !=-1;
        if(isUseColor){
            flag |= 4;
        }
        if(isLimitRegion){
            flag |= 8;
        }
        if(isPvpReward){
            flag |= 16;
        }
        buffer.writeByte(flag);
        buffer.writeLongLE(object.getItemId());
        if ((flag & 1) != 0) {
            buffer.writeLongLE(object.getOwnerId());
        }
        buffer.writeShortLE(object.getItemIndexId());
        buffer.writeShortLE(object.getItemAmount());
        buffer.writeByte(object.getInventoryId());
        if ((flag & 2) != 0) {
            buffer.writeByte(0);//unuse
        }
        if (isUseColor) {
            buffer.writeByte(object.getColorId());
        }
        if (isLimitRegion) {
            buffer.writeByte(object.getItemRegionIndexId());
        }
        if (isPvpReward) {
            buffer.writeByte(object.getPvpRewardLevel());
            buffer.writeByte(object.getPvpRewardSeason());
            buffer.writeIntLE((int)object.getPvpRewardTime().toEpochSecond(ZoneOffset.UTC));
        }
    }
}
