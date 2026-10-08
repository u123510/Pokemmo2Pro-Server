package org.pokemmo.gameserver.protocol.packets.s2c;

import org.server.OutgoingPacket;
import org.server.bytes.ByteBufEx;
import org.pokemmo.gameserver.game.entity.NpcEntity;
import org.pokemmo.gameserver.game.map.MapProtocolIds;

public class SendAddGameEntityPacket extends OutgoingPacket {
    private short flag = 0;
    private NpcEntity object;
    public SendAddGameEntityPacket(NpcEntity npcData) {
        this.object = npcData;
    }
    @Override
    public void encode(ByteBufEx buffer) throws Exception {
        buffer.writeLongLE(object.getEntityGameId());
        buffer.writeByte(object.getNpcModelRegionIndexId());
        buffer.writeShortLE(object.getNpcModelIndexId());
        buffer.writeByte(object.getDefaultToward());
        buffer.writeByte(object.getMoveMentType());
        buffer.writeByte(object.getMovementLeashX());
        buffer.writeByte(object.getMovementLeashY());
        buffer.writeByte(object.getRegionIndexId());
        buffer.writeByte(MapProtocolIds.first(
                object.getRegionIndexId(),
                object.getMapHeaderIdOrGbaMapGroupId(),
                object.getGbaMapId()));
        buffer.writeByte(MapProtocolIds.second(
                object.getRegionIndexId(),
                object.getMapHeaderIdOrGbaMapGroupId(),
                object.getGbaMapId()));
        buffer.writeShortLE(object.getX());
        buffer.writeShortLE(object.getY());
        buffer.writeByte(object.getZ());
        buffer.writeByte(object.getToward());
        if(object.isTrainer())
            flag |= 1;
        if(object.isTree())
            flag |= 2;
        if(object.isResetNpcModel())
            flag |= 4;
        if(object.isCanInteract())
            flag |= 8;
        if(object.isLowHeight())
            flag |= 16;
        if(object.isCompanion())
            flag |= 32;
        if(object.isTree())
            flag |= 64;
        if(object.isWantRematch())
            flag |= 128;
        if(object.isHasFollowedPokemon())
            flag |= 256;
        if(object.isOtherPlayer())
            flag |= 512;
        if(object.isWantedPokemon())
            flag |= 1024;
        if(object.isUnk6())
            flag |= 2048;
        if(object.isSpriteScaleOverride())
            flag |= 4096;
        if(object.isOtherPlayerSkin())
            flag |= 8192;
        buffer.writeShortLE(flag);
        if (object.isTrainer())
        {
            buffer.writeShortLE(object.getNpcTrainId());//npcTrainId
            buffer.writeByte(object.getTrainAggroRange());//trainAggroRange
            buffer.writeBoolean(object.isDoubleBattle());//isTwoBattle
        }
        if(object.isResetNpcModel()){
            buffer.writeByte(object.getNewNpcModelLength());
            buffer.writeByte(object.getNewNpcModelWidth());
        }
        if(object.isHasFollowedPokemon()){
            buffer.writeShortLE(object.getFollowPokemonIndexId());
        }
        if(object.isOtherPlayer()){
            //TODO 写其他玩家数据
        }
        if(object.isWantedPokemon()){
            buffer.writeShortLE(object.getWantedPokemonIndexId());//wantedPokemonId
            buffer.writeByte(object.getWantedPokemonAmount());//wantedAmount
        }
        if(object.isSpriteScaleOverride()){
            buffer.writeFloatLE(object.getSpriteScaleOverride());//spriteScaleOverride
        }
        if(object.isOtherPlayerSkin()){
            //TODO 写其他玩家皮肤数据
        }
    }
}
