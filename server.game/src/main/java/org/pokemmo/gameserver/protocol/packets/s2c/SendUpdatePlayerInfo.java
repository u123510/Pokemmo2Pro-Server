package org.pokemmo.gameserver.protocol.packets.s2c;

import lombok.RequiredArgsConstructor;
import org.server.OutgoingPacket;
import org.server.bytes.ByteBufEx;
import org.pokemmo.gameserver.game.character.UpdateCharacterSelector;
@RequiredArgsConstructor
public class SendUpdatePlayerInfo extends OutgoingPacket {
    private final UpdateCharacterSelector refreshCharacterInfo;
    private byte flag = 0;
    @Override
    public void encode(ByteBufEx buffer) throws Exception {

        if(refreshCharacterInfo.isRefreshMoney()) {
            flag |= 1;
        }
        if(refreshCharacterInfo.isRefreshSafariInfo()) {
            flag |= 2;
        }
        if(refreshCharacterInfo.isRefreshCoins()) {
            flag |= 4;
        }
        if(refreshCharacterInfo.isRefreshBoxInfo()) {
            flag |= 8;
        }
        if(refreshCharacterInfo.isRefreshRepelInfo()) {
            flag |= 16;
        }
        if(refreshCharacterInfo.isRefreshBattlePoints()) {
            flag |= 32;
        }
        if(refreshCharacterInfo.isRefreshRureInfo()) {
            flag |= 64;
        }
        if(refreshCharacterInfo.isRefreshParticleEffectInfo()) {
            flag |= Byte.MIN_VALUE;
        }
        buffer.writeByte(flag);
        if(refreshCharacterInfo.isRefreshMoney()) {
            buffer.writeIntLE(refreshCharacterInfo.getCharacter().getMoney());//money
        }
        if(refreshCharacterInfo.isRefreshSafariInfo()) {
            buffer.writeShortLE(refreshCharacterInfo.getCharacter().getSafariSteps());//safariSteps
            buffer.writeByte(refreshCharacterInfo.getCharacter().getSafariBallAmount());//safariBallAmount
        }
        if(refreshCharacterInfo.isRefreshCoins()) {
            buffer.writeShortLE(refreshCharacterInfo.getCharacter().getCoins());//coins
        }
        if(refreshCharacterInfo.isRefreshBoxInfo()) {
            buffer.writeByte(refreshCharacterInfo.getCharacter().getPcBoxExpansionNumber());//PCBoxExpansionNumber
            buffer.writeByte(refreshCharacterInfo.getCharacter().getBattleBoxExpansionNumber());//battleBoxExpansionNumber
            buffer.writeByte(refreshCharacterInfo.getCharacter().getTemplateAmount());//templateAmount
        }
        if(refreshCharacterInfo.isRefreshRepelInfo()) {
            buffer.writeShortLE(refreshCharacterInfo.getCharacter().getRepelSteps());//repelSteps
            buffer.writeShortLE(refreshCharacterInfo.getCharacter().getRepelItemId());//repelItemId
        }
        if(refreshCharacterInfo.isRefreshBattlePoints()) {
            buffer.writeIntLE(refreshCharacterInfo.getCharacter().getBattlePoints());//battlePoints
        }
        if(refreshCharacterInfo.isRefreshRureInfo()) {
            buffer.writeByte(refreshCharacterInfo.getRureTypeValue());//rureTypeValue
            if(refreshCharacterInfo.getRureTypeValue() != -1){
                buffer.writeShortLE(refreshCharacterInfo.getCharacter().getLureItemId());//lureItemId
                buffer.writeShortLE(refreshCharacterInfo.getCharacter().getLureSteps());//lureSteps
            }
        }
        if(refreshCharacterInfo.isRefreshParticleEffectInfo()) {
            buffer.writeByte(refreshCharacterInfo.getParticleEffectTypeArray().length);//particleEffectTypeArrayLength
            for (int i =0;i<refreshCharacterInfo.getParticleEffectTypeArray().length;i++){
                buffer.writeByte(refreshCharacterInfo.getParticleEffectTypeArray()[i]);//particleEffectValue
            }
        }
    }
}
