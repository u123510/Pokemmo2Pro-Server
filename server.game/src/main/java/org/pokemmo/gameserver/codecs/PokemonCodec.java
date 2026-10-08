package org.pokemmo.gameserver.codecs;

import org.pokemmo.gameserver.game.pokemon.PokemonData;
import org.server.bytes.ByteBufEx;
import org.pokemmo.db.jooq.tables.records.PokemonRecord;
import org.pokemmo.gameserver.game.pokemon.PokemonRarity;
import org.pokemmo.gameserver.game.pokemon.PokemonRibbonMask;

import java.time.ZoneOffset;

public class PokemonCodec implements ObjectCodec<PokemonData> {
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
        encode(buffer, object, object.getContainerId(), object.getContainerPosition());
    }

    /**
     * Encodes a Pokemon using a packet-local container projection without
     * changing the online PokemonData instance.
     */
    public void encode(ByteBufEx buffer, PokemonData object, int containerId, short containerPosition) {
        buffer.writeLongLE(object.getPokemonId());
        buffer.writeByte(object.getWidgetType());
        buffer.writeLongLE(object.getTrainerId());
        buffer.writeLongLE(object.getTrainerId());
        buffer.writeByte(containerId);
        buffer.writeShortLE(containerPosition);
        buffer.writeShortLE(object.getPokemonIndexId());
        buffer.writeIntLE(object.getPersonalityValue());//personalityValue
        buffer.writeLongLE(object.getOriginalTrainerId());//originalTrainerId
        buffer.writeUtf16LE(object.getOtName()); // ot
        buffer.writeUtf16LE(object.getName()); // name
        buffer.writeByte(object.getFormType());
        buffer.writeByte(object.getPokemonStatus().getType());
        buffer.writeByte(object.getLevel());
        buffer.writeShortLE(object.getCurrentHp()); // hp
        buffer.writeShortLE(object.getItem());
        buffer.writeIntLE(object.getExp()); // xp
        buffer.writeByte(object.getPpUpTimes());
        buffer.writeShortLE(object.getFriendValue());
        short[] moves = object.getMoves();
        assert moves.length == 4;
        for (short move : moves) {
            buffer.writeShortLE(move); // 招式ID为short(16位)，不能用 & 0xFF 截断
        }
        short[] movesPp = object.getMovesPp();
        assert movesPp.length == 4;
        for (short pp : movesPp) {
            buffer.writeByte(pp & 0xFF);
        }
        short[] canRemberMoves = object.getCanRememberMoves();
        assert canRemberMoves.length == 4;
        for (short move : canRemberMoves) {
            buffer.writeShortLE(move); // 同上，保留完整16位ID
        }
        short[] evValues = object.getPokemonEvs();
        assert evValues.length == 6;
        for (short ev : evValues) {
            buffer.writeByte(ev & 0xFF);
        }
        short[] contestsCategoryValues = object.getPokemonContestsCategoryValues();
        assert contestsCategoryValues.length == 5;
        for (short value : contestsCategoryValues) {
            buffer.writeByte(value & 0xFF);
        }
        buffer.writeByte(0);
        buffer.writeByte(object.getCatchAddress());
        buffer.writeByte(object.getCatchLevel());
        buffer.writeByte(object.getCatchRegion());
        buffer.writeByte(object.getBallType());
        buffer.writeByte(object.getFormType());
        // introduce a enum for stats to use here and make it more readable
        short[] ivValues = object.getPokemonIvs();
        int ivs = ((ivValues[0] & 31) << 0) |
                    ((ivValues[1] & 31) << 5) |
                    ((ivValues[2] & 31) << 10) |
                    ((ivValues[3] & 31) << 15) |
                    ((ivValues[4] & 31) << 20) |
                    ((ivValues[5] & 31) << 25);
        buffer.writeIntLE(ivs);
        buffer.writeByte(object.getPokemonAbilityIndex());
        buffer.writeLongLE(PokemonRibbonMask.encode(
                object.getContestRibbon(), object.getNormalRibbon()));
        byte rarity = 0;

        if (object.isShiny())
            rarity |= 1 << PokemonRarity.SHINY.ordinal();
        if (object.isHasHiddenAbility())
            rarity |= 1 << PokemonRarity.HIDDEN_ABILITY.ordinal();
        if (object.isAlpha())
            rarity |= 1 << PokemonRarity.ALPHA.ordinal();
        if (object.isSecret())
            rarity |= 1 << PokemonRarity.SECRET.ordinal();
        buffer.writeShortLE(rarity);
        buffer.writeIntLE((int)object.getCatchTime().toEpochSecond(ZoneOffset.UTC));
        buffer.writeShortLE(object.getEggValue());
        buffer.writeByte(object.getHiddenPowerType()); // hiddenPowerType
        buffer.writeByte(object.getCurrentSelectParticleEffectTypeForDisplay()); // currentSelectParticleEffectType
        short[] particleEffects = object.getParticleEffectsForClient();
        buffer.writeByte(particleEffects.length); // effect size
        for (short effect : particleEffects) {
            buffer.writeByte(effect);
        }
    }
}
