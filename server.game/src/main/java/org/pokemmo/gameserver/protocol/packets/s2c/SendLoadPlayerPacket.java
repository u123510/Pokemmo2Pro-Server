package org.pokemmo.gameserver.protocol.packets.s2c;

import org.pokemmo.gameserver.game.character.CharacterData;
import org.pokemmo.gameserver.game.entity.EntityNameplateType;
import org.pokemmo.gameserver.game.map.MapProtocolIds;
import org.pokemmo.gameserver.game.permission.PermissionType;
import org.server.OutgoingPacket;
import org.server.bytes.ByteBufEx;
import org.pokemmo.gameserver.codecs.Codecs;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class SendLoadPlayerPacket extends OutgoingPacket {
    private final CharacterData characterData;
    @Override
    public void encode(ByteBufEx buffer) throws Exception {
        var player = characterData.getPlayerEntity();
        buffer.writeLongLE(player.getEntityGameId());
        buffer.writeByte(player.getSex());
        Codecs.SKIN_CODEC_1.encode(buffer, characterData);
        buffer.writeUtf16LE(player.getPlayerName());
        buffer.writeByte(player.getRegionIndexId()); // regionIndexId
        buffer.writeByte(MapProtocolIds.first(
                player.getRegionIndexId(),
                player.getMapHeaderIdOrGbaMapGroupId(),
                player.getGbaMapId()));
        buffer.writeByte(MapProtocolIds.second(
                player.getRegionIndexId(),
                player.getMapHeaderIdOrGbaMapGroupId(),
                player.getGbaMapId()));
        buffer.writeShortLE(player.getX()); // x
        buffer.writeShortLE(player.getY()); // y
        buffer.writeByte(player.getZ()); // z
        buffer.writeByte(player.getToward() & 0x03); // packed direction/position flags
        buffer.writeByte(0); // entity PD0 flags; ordinary players have no entity flags
        EntityNameplateType nameplateType = player.getEntityNameplateType();
        buffer.writeByte((nameplateType == null ? EntityNameplateType.NONE : nameplateType).getType()); // RL0 state
        PermissionType permissionType = player.getPermission();
        byte permission = (permissionType == null ? PermissionType.NORMAL : permissionType).getType();
        byte flags = 0;
        boolean isGm = permission > 3;
        if(isGm){
            flags |= 0x01;
        }
        boolean isUseRomModel = characterData.getPlayerEntity().getModelRegionIndexId()>-1 && characterData.getPlayerEntity().getModelIndexId()>-1;
        if(isUseRomModel){
            flags |= 0x02;
        }
        boolean hasFollowerPokemon = characterData.getPlayerEntity().getFollowPokemonIndexId()>0;
        if(hasFollowerPokemon){
            flags |= 0x04;
        }
        boolean hasFollowingPokemonRarity = characterData.getPlayerEntity().getFollowPokemonRarity()>0;
        if(hasFollowingPokemonRarity){
            flags |= 0x08;
        }
        boolean hasUnion = characterData.getPlayerEntity().getUnionName()!=null && !characterData.getPlayerEntity().getUnionName().isEmpty();
        if(hasUnion){
            flags |= 0x10;
        }
        buffer.writeByte(flags);
        if(isGm) {
            buffer.writeByte(permission);//permission
        }
        if (isUseRomModel) {
            buffer.writeByte(characterData.getPlayerEntity().getModelRegionIndexId());//self model area
            buffer.writeShortLE(characterData.getPlayerEntity().getModelIndexId());// self model index
        }
        // has follower
        if (hasFollowerPokemon) {
            buffer.writeShortLE(characterData.getPlayerEntity().getFollowPokemonIndexId()); // follower pokedex id
        }
        if (hasFollowingPokemonRarity) {
            buffer.writeByte(characterData.getPlayerEntity().getFollowPokemonRarity());//follower pokemon rarity
        }
        if (hasUnion) {
            buffer.writeUtf16LE(characterData.getPlayerEntity().getUnionName());//unionName
        }
    }
}
