package org.pokemmo.gameserver.codecs;

import org.pokemmo.gameserver.game.battle.effect.*;
import org.server.bytes.ByteBufEx;

public class BattleRoundResultCodec implements ObjectCodec<BasePokemonActionEffect>{
    @Override
    public BasePokemonActionEffect decode(ByteBufEx buffer) {
        return null;
    }
    @Override
    public void decode(ByteBufEx buffer, BasePokemonActionEffect object) {
        throw new UnsupportedOperationException();
    }
    @Override
    public void encode(ByteBufEx buffer, BasePokemonActionEffect object) {
        byte effectFlags = 0;
        buffer.writeByte(object.getEffectType().getType());
        if(object.isReloadEffectTargetPokemonId()){
            effectFlags |= 1;
        }
        if(object.isReloadEffectActorPokemonId()){
            effectFlags |= 2;
        }
        buffer.writeByte(effectFlags);
        if(object.isReloadEffectTargetPokemonId()){
            buffer.writeLongLE(object.getEffectTargetPokemonId());
        }
        if(object.isReloadEffectActorPokemonId()){
            buffer.writeLongLE(object.getEffectActorPokemonId());
        }
        switch (object.getEffectType())
        {
            // 说明剩余hp效果
            case DAMAGE:
                BattlePokemonRemainHpEffect remainHpEffect = (BattlePokemonRemainHpEffect) object;
                buffer.writeShortLE(remainHpEffect.getRemainHp());
                break;
                // 说明状态变化效果
            case STAT_CHANGE:
                BattlePokemonStatChangeEffect statChangeEffect = (BattlePokemonStatChangeEffect) object;
                buffer.writeByte(statChangeEffect.getStatChangeType().getType());
                buffer.writeByte(statChangeEffect.getStatType().getType());
                buffer.writeByte(statChangeEffect.getTargetChangeValue());
                buffer.writeByte(statChangeEffect.getActualChangeValue());
                break;
            case WEATHER:
                BattleWeatherChangeEffect weatherEffect = (BattleWeatherChangeEffect) object;
                buffer.writeByte(weatherEffect.getBattleWeatherType().getType());
                break;
            case FACTION_STAT_CHANGE:
                BattleFactionStatChangeEffect factionStatChangeEffect = (BattleFactionStatChangeEffect) object;
                buffer.writeByte(factionStatChangeEffect.getFactionIndex());
                buffer.writeByte(factionStatChangeEffect.getChangeStatTypes().length);
                for(int i = 0;i<factionStatChangeEffect.getChangeStatTypes().length;i++){
                    buffer.writeByte(factionStatChangeEffect.getChangeStatTypes()[i].getType());
                }
                buffer.writeByte(factionStatChangeEffect.getBattleTeamStatChangeTargetLevel());
                buffer.writeByte(factionStatChangeEffect.getFactionPokemonsStatChangeValues().length);
                for(int i = 0; i<factionStatChangeEffect.getFactionPokemonsStatChangeValues().length;i++){
                    for(int j = 0;j<factionStatChangeEffect.getFactionPokemonsStatChangeValues()[i].length;j++){
                        buffer.writeByte(factionStatChangeEffect.getFactionPokemonsStatChangeValues()[i][j]);
                    }
                }
                break;
            case ABILITY_TRIGGER:
                byte effectFlag = 0;
                BattlePokemonAbilityTriggerEffect battleAbilityTriggerEffect = (BattlePokemonAbilityTriggerEffect) object;
                if(battleAbilityTriggerEffect.isAbilityHasTriggerPokemon()) {
                    effectFlag |=1;
                }
                if(battleAbilityTriggerEffect.isShowMove()){
                    effectFlag |=2;
                }
                if(battleAbilityTriggerEffect.isCopyEnemyAbility()){
                    effectFlag |=4;
                }
                if(battleAbilityTriggerEffect.isEatItem()){
                    effectFlag |=8;
                }
                buffer.writeByte(effectFlag);
                buffer.writeShortLE(battleAbilityTriggerEffect.getActorPokemonAbilityIndexId());
                buffer.writeLongLE(battleAbilityTriggerEffect.getShowAbilityPokemonId());
                if(battleAbilityTriggerEffect.isAbilityHasTriggerPokemon()) {
                    buffer.writeLongLE(battleAbilityTriggerEffect.getAbilityTargetPokemonId());
                }
                if(battleAbilityTriggerEffect.isShowMove()&&battleAbilityTriggerEffect.isEatItem()) {
                    buffer.writeShortLE(battleAbilityTriggerEffect.getEatItemIndexId());
                }
                if(battleAbilityTriggerEffect.isCopyEnemyAbility()){
                    buffer.writeShortLE(battleAbilityTriggerEffect.getTargetPokemonAbilityIndexId());
                }
                break;
            case FORE_WARN:
                BattlePokemonForeWarnEffect foreWarnEffect = (BattlePokemonForeWarnEffect) object;
                buffer.writeShortLE(foreWarnEffect.getMoveIndexId());
                break;
            case DISOBEY:
                BattlePokemonDisobeyEffect disobeyEffect = (BattlePokemonDisobeyEffect) object;
                buffer.writeByte(disobeyEffect.getDisobeyType() != null ? disobeyEffect.getDisobeyType().getType() : (byte) 2);
                break;

        }
    }
}
