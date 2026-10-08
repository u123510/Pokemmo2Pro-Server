package org.pokemmo.gameserver.codecs;
import org.pokemmo.gameserver.game.pokemon.*;
import org.server.bytes.ByteBufEx;
import org.pokemmo.gameserver.game.pokemon.PokemonRarity;
import lombok.RequiredArgsConstructor;

import java.time.ZoneOffset;
@RequiredArgsConstructor
public class UpdatePokemonDataCodec implements ObjectCodec<PokemonData>{
    private static final int CAN_REMEMBER_MOVES_AND_OT_FLAG = 32768;
    private static final int CAN_REMEMBER_MOVE_SLOTS = 4;

    private final UpdatePokemonData refreshPokemonInfo;
    @Override
    public PokemonData decode(ByteBufEx buffer) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void decode(ByteBufEx buffer, PokemonData object) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void encode(ByteBufEx buffer, PokemonData object) {
        buffer.writeLongLE(object.getPokemonId());
        int flag = 0;
        if(refreshPokemonInfo.isReloadPokemonLevel()) flag |= 1;
        if(refreshPokemonInfo.isReloadPokemonAbilityValue()) flag |= 2;
        if(refreshPokemonInfo.isReloadPokemonMove()) flag |= 4;
        if(refreshPokemonInfo.isReloadPokemonCurrentHp()) flag |= 8;
        if(refreshPokemonInfo.isReloadPokemonStatus()) flag |= 16;
        if(refreshPokemonInfo.isReloadPokemonIndexId()) flag |= 32;
        if(refreshPokemonInfo.isReloadPokemonPos()) flag |= 64;
        if(refreshPokemonInfo.isReloadPokemonEvs()) flag |= 128;
        if(refreshPokemonInfo.isReloadPokemonItem()) flag |= 256;
        if(refreshPokemonInfo.isReloadPokemonFriendValue()) flag |= 512;
        if(refreshPokemonInfo.isReloadPokemonCatchInfo()) flag |= 1024;
        if(refreshPokemonInfo.isReloadPokemonEggValue()) flag |= 2048;
        if(refreshPokemonInfo.isUseRecoverItem()) flag |= 4096;
        if(refreshPokemonInfo.isReloadPokemonBallType()) flag |= 8192;
        if(refreshPokemonInfo.isReloadPokemonRibbonInfo()) flag |= 16384;
        // Client f.OM always assigns the temporary OT string after applying a
        // 0x16 update. Include this field group on every update so it cannot
        // replace the existing OT with its default empty string.
        flag |= CAN_REMEMBER_MOVES_AND_OT_FLAG;
        if(refreshPokemonInfo.isReloadNatureType()) flag |= 65536;
        if(refreshPokemonInfo.isReloadAbilityIndex()) flag |= 131072;
        if(refreshPokemonInfo.isReloadPokemonContestsStatus()) flag |= 262144;
        if(refreshPokemonInfo.isReloadPokemonHiddenPowerType()) flag |= 524288;
        if(refreshPokemonInfo.isReloadPokemonRarity()) flag |= 1048576;
        if(refreshPokemonInfo.isReloadPokemonIndividualValue()) flag |= 2097152;
        if(refreshPokemonInfo.isReloadPokemonParticleEffects()) flag |= 4194304;
        if(refreshPokemonInfo.isReloadCurrentSelectShowParticleEffect()) flag |= 8388608;
        buffer.writeIntLE(flag);
        if (refreshPokemonInfo.isReloadPokemonLevel()) {
            buffer.writeByte(object.getLevel());
            buffer.writeIntLE(object.getExp());//exp
        }
        PokemonDexData pokemonDexData = object.getPokemonDexData();
        short[] abilityValues = new short[6];
        for (int i = 0; i < 6; i++) {
            abilityValues[i] = pokemonDexData.getPokemonAbilityValue(PokemonStatType.getByType(i), object.getPokemonIvs()[i], object.getPokemonEvs()[i], object.getLevel(), object.getNatureType());
        }
        if (refreshPokemonInfo.isReloadPokemonAbilityValue()) {
            for (int i = 0; i < 6; i++) {
                buffer.writeShortLE(abilityValues[i]);//pokemon ability value
            }
        }
        if (refreshPokemonInfo.isReloadPokemonMove()) {
            short[] moves = object.getMoves();
            short[] movesPp = object.getMovesPp();
            for (int i = 0; i < 4; i++) {
                buffer.writeShortLE(moves[i]);// pokemon skill index
                buffer.writeByte(movesPp[i]);//pokemon skill maxpp
            }
            buffer.writeByte(object.getPpUpTimes());//pokemon up pp number
        }
        if (refreshPokemonInfo.isReloadPokemonCurrentHp()) {
            buffer.writeShortLE(object.getCurrentHp());// pokemon current hp
        }
        if (refreshPokemonInfo.isReloadPokemonStatus()) {
            buffer.writeByte(object.getPokemonStatus().getType());// pokemon statys
        }
        if (refreshPokemonInfo.isReloadPokemonIndexId()) {
            buffer.writeShortLE(object.getPokemonIndexId());
            buffer.writeByte(object.getFormType());
        }
        if (refreshPokemonInfo.isReloadPokemonPos()) {
            buffer.writeByte(object.getContainerId());
            buffer.writeShortLE(object.getContainerPosition());
        }
        if (refreshPokemonInfo.isReloadPokemonEvs()) {
            for (int i = 0; i < 6; i++) {
                buffer.writeShortLE(object.getPokemonEvs()[i]);//pokemon base point value
            }
        }
        if (refreshPokemonInfo.isReloadPokemonItem()) {
            buffer.writeShortLE(object.getItem());//pokemon item
        }
        if (refreshPokemonInfo.isReloadPokemonFriendValue()) {
            buffer.writeShortLE(object.getFriendValue());//pokemon friend value
        }
        if (refreshPokemonInfo.isReloadPokemonCatchInfo()) {
            buffer.writeIntLE(object.getPersonalityValue());// pokemon personalityValue   personalityValue % 25 = pokemon nature
            buffer.writeIntLE((int) object.getCatchTime().toEpochSecond(ZoneOffset.UTC) / 1000);
            buffer.writeByte(object.getCatchAddress());//pokemon catchAddrId
            buffer.writeByte(object.getCatchLevel());//pokemon CatchLevel
            buffer.writeByte(object.getCatchRegion());//pokemon CatchRegion
        }
        if (refreshPokemonInfo.isReloadPokemonEggValue()) {
            buffer.writeShortLE(object.getEggValue());//pokemon eggValue
        }
        if (refreshPokemonInfo.isUseRecoverItem()) {
            buffer.writeShortLE(object.getItem());//stringItemId
        }
        if (refreshPokemonInfo.isReloadPokemonBallType()) {
            buffer.writeByte(object.getBallType());//pokemon ballType
        }
        if (refreshPokemonInfo.isReloadPokemonRibbonInfo()) {
            buffer.writeLongLE(PokemonRibbonMask.encode(
                    object.getContestRibbon(), object.getNormalRibbon()));
        }
        short[] pokemonIvs = object.getPokemonIvs();
        int ivs = ((pokemonIvs[0] & 31) << 0) |
                ((pokemonIvs[1] & 31) << 5) |
                ((pokemonIvs[2] & 31) << 10) |
                ((pokemonIvs[3] & 31) << 15) |
                ((pokemonIvs[4] & 31) << 20) |
                ((pokemonIvs[5] & 31) << 25);

        if (refreshPokemonInfo.isReloadPokemonIndividualValue()) {
            buffer.writeIntLE(ivs);
        }
        short[] canRememberMoves = object.getCanRememberMoves();
        if (canRememberMoves == null || canRememberMoves.length != CAN_REMEMBER_MOVE_SLOTS) {
            throw new IllegalStateException("Pokemon " + object.getPokemonId()
                    + " must have exactly four remembered-move slots for a 0x16 update.");
        }
        if (object.getOtName() == null) {
            throw new IllegalStateException("Pokemon " + object.getPokemonId()
                    + " must have a non-null OT name for a 0x16 update.");
        }
        for (int i = 0; i < CAN_REMEMBER_MOVE_SLOTS; i++) {
            buffer.writeShortLE(canRememberMoves[i]);// pokemon can remember skill
        }
        buffer.writeUtf16LE(object.getOtName());// OT
        if (refreshPokemonInfo.isReloadNatureType()) {
            buffer.writeByte(object.getNatureType().getType());//pokemon nature
        }
        if (refreshPokemonInfo.isReloadAbilityIndex()) {
            buffer.writeByte(object.getPokemonAbilityIndex());//pokemon abilityIndex
        }
        if (refreshPokemonInfo.isReloadPokemonContestsStatus()) {
            for (int i = 0;i<5;i++){
                buffer.writeShortLE(object.getPokemonContestsCategoryValues()[i]);//pokemon contests category value
            }
        }
        if (refreshPokemonInfo.isReloadPokemonHiddenPowerType()) {
            buffer.writeShortLE(object.getHiddenPowerType());//pokemon hidden power type
        }
        byte rarity = 0;
        if (refreshPokemonInfo.isReloadPokemonRarity()) {
            if (object.isShiny())
                rarity |= 1 << PokemonRarity.SHINY.ordinal();
            if (object.isHasHiddenAbility())
                rarity |= 1 << PokemonRarity.HIDDEN_ABILITY.ordinal();
            if (object.isAlpha())
                rarity |= 1 << PokemonRarity.ALPHA.ordinal();
            if (object.isSecret())
                rarity |= 1 << PokemonRarity.SECRET.ordinal();
            buffer.writeShortLE(rarity);
        }
        if (refreshPokemonInfo.isReloadPokemonParticleEffects()) {
            short[] particleEffects = object.getParticleEffectsForClient();
            buffer.writeByte(particleEffects.length);
            for (short particleEffect : particleEffects){
                buffer.writeByte(particleEffect);
            }
        }
        if (refreshPokemonInfo.isReloadCurrentSelectShowParticleEffect()) {
            buffer.writeByte(object.getCurrentSelectParticleEffectTypeForDisplay());
        }
    }
}
