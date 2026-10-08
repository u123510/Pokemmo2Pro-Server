package org.pokemmo.gameserver.codecs;

import org.pokemmo.gameserver.game.character.CharacterData;
import org.server.bytes.ByteBufEx;
import lombok.RequiredArgsConstructor;

import java.time.ZoneOffset;

@RequiredArgsConstructor
public class CharacterCodec implements ObjectCodec<CharacterData> {
    private final boolean writeMac;
    private final byte[] pokemonEffects= {1,2,3,4};
    @Override
    public CharacterData decode(ByteBufEx buffer) {
        throw new UnsupportedOperationException();
    }
    @Override
    public void decode(ByteBufEx buffer, CharacterData object) {
        throw new UnsupportedOperationException();
    }
    @Override
    public void encode(ByteBufEx buffer, CharacterData character) {
        buffer.writeLongLE(character.getPlayerEntity().getEntityGameId());
        buffer.writeUtf16LE(character.getPlayerEntity().getEntityName());
        buffer.writeUtf16LE(character.getPlayerEntity().getUnionName());//unionName
        buffer.writeIntLE(character.getAccountId());
        buffer.writeByte(character.getPlayerEntity().getSex());
        buffer.writeIntLE((int) character.getLoginTimeStamp().toEpochSecond(ZoneOffset.UTC));
        if (writeMac) {
            buffer.writeLongLE(character.getLastLoginMac());
        }
        buffer.writeIntLE((int) character.getCreateTimeStamp().toEpochSecond(ZoneOffset.UTC));
        buffer.writeIntLE(0); // unused
        buffer.writeByte(0); // unused
        buffer.writeIntLE(toOnlineSeconds(character.getOnlineMinutes()));
        buffer.writeIntLE(character.getMoney());
        buffer.writeShortLE(character.getCoins());
        buffer.writeIntLE(character.getBattlePoints());
        buffer.writeByte(character.getPlayerEntity().getPermission().getType());
        buffer.writeByte(0); // unused
        buffer.writeByte(character.getPlayerEntity().getTransportation());
        buffer.writeIntLE(0);// unused
        for(int i = 0;i<8;i++)
        {
            buffer.writeByte(0);// unused
        }
        buffer.writeShortLE(character.getSafariSteps());
        buffer.writeByte(character.getSafariBallAmount());
        buffer.writeIntLE(character.getOtherSettingsValue());
        buffer.writeByte(character.getPcBoxExpansionNumber());
        buffer.writeByte(character.getBattleBoxExpansionNumber());
        buffer.writeByte(character.getTemplateAmount());
        buffer.writeByte(0); // unused
        buffer.writeByte(0); // unused
        buffer.writeByte(0); // unused
        buffer.writeByte(character.getPlayerEntity().getRegionIndexId());
        buffer.writeByte(character.getPlayerEntity().getMapHeaderIdOrGbaMapGroupId());
        buffer.writeByte(character.getPlayerEntity().getGbaMapId());
        buffer.writeByte(0); // unused
        buffer.writeShortLE(character.getPlayerEntity().getX());
        buffer.writeShortLE(character.getPlayerEntity().getY());
        buffer.writeByte(0); // unused
        buffer.writeByte(0); // unused
        buffer.writeShortLE(character.getRepelSteps());
        buffer.writeShortLE(character.getRepelItemId());
        buffer.writeByte(character.getLureRarityType().getType());
        buffer.writeShortLE(character.getLureItemId());
        buffer.writeShortLE(character.getLureSteps());
        buffer.writeByte(pokemonEffects.length); // array size
        for (int i = 0;i<pokemonEffects.length;i++) {
            buffer.writeByte(pokemonEffects[i]);
        }
    }

    private static int toOnlineSeconds(int onlineMinutes) {
        if (onlineMinutes <= 0) {
            return 0;
        }
        long onlineSeconds = (long) onlineMinutes * 60L;
        return (int) Math.min(onlineSeconds, Integer.MAX_VALUE);
    }
}
