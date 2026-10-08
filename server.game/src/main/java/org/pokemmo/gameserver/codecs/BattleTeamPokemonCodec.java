package org.pokemmo.gameserver.codecs;

import lombok.extern.slf4j.Slf4j;
import org.pokemmo.gameserver.game.battle.BattlePokemonData;
import org.server.bytes.ByteBufEx;
import org.pokemmo.gameserver.game.pokemon.PokemonRarity;
import org.pokemmo.gameserver.game.battle.BattleStatsBroadcastMode;
import lombok.RequiredArgsConstructor;
import org.pokemmo.gameserver.game.pokemon.PokemonDexData;
import org.pokemmo.gameserver.game.pokemon.PokemonManager;
import org.pokemmo.gameserver.game.pokemon.PokemonStatType;

@RequiredArgsConstructor
@Slf4j
public class BattleTeamPokemonCodec implements ObjectCodec<BattlePokemonData> {
    private final boolean isReLoadBattleStatsBroadcastMode;
    private final BattleStatsBroadcastMode battleStatsBroadcastMode;
    private final byte teamIndex;
    private final boolean isSelfFaction;
    private final boolean[] isShowPokemonAbilityValue;
    @Override
    public BattlePokemonData decode(ByteBufEx buffer) {
        throw new UnsupportedOperationException();
    }
    @Override
    public void decode(ByteBufEx buffer, BattlePokemonData object) {
        throw new UnsupportedOperationException();
    }
    @Override
    public void encode(ByteBufEx buffer, BattlePokemonData object) {
        buffer.writeByte(teamIndex);
        buffer.writeByte(object.getPokemonData().getContainerPosition());
        buffer.writeByte(object.isHasDebutPokemon() ? (byte) 1 : (byte) 0);
        if (!object.isHasDebutPokemon()) {
            return;
        }
        buffer.writeLongLE(object.getPokemonData().getPokemonId());
        buffer.writeShortLE(object.getPokemonData().getPokemonIndexId());
        buffer.writeByte(object.getPokemonData().getLevel());
        buffer.writeUtf16LE(object.getPokemonData().getName());
        buffer.writeByte(object.getPokemonSex());
        buffer.writeByte(object.getPokemonData().getFormType());
        buffer.writeShortLE(object.getPokemonData().getItem());
        buffer.writeShortLE(object.getPokemonData().getCurrentHp());
        short derivedMaxHp = PokemonManager.calculateMaxHp(object.getPokemonData());
        if (derivedMaxHp > 0 && derivedMaxHp != object.getPokemonData().getMaxHp()) {
            log.warn("战斗队伍 HP 上限已校正: pokemonId={}, dexId={}, level={}, currentHp={}, cachedMaxHp={}, derivedMaxHp={}, ivHp={}, evHp={}",
                    object.getPokemonData().getPokemonId(),
                    object.getPokemonData().getPokemonIndexId(),
                    object.getPokemonData().getLevel(),
                    object.getPokemonData().getCurrentHp(),
                    object.getPokemonData().getMaxHp(),
                    derivedMaxHp,
                    object.getPokemonData().getPokemonIvs()[PokemonStatType.HP.getType()],
                    object.getPokemonData().getPokemonEvs()[PokemonStatType.HP.getType()]);
            object.getPokemonData().setMaxHp(derivedMaxHp);
        }
        buffer.writeShortLE(object.getPokemonData().getMaxHp());//最大血量
        buffer.writeByte(object.getPokemonData().getPokemonStatus().getType());
        byte rarity = 0;
        if (object.getPokemonData().isShiny())
            rarity |= 1 << PokemonRarity.SHINY.ordinal();
        if (object.getPokemonData().isHasHiddenAbility())
            rarity |= 1 << PokemonRarity.HIDDEN_ABILITY.ordinal();
        if (object.getPokemonData().isAlpha())
            rarity |= 1 << PokemonRarity.ALPHA.ordinal();
        if (object.getPokemonData().isSecret())
            rarity |= 1 << PokemonRarity.SECRET.ordinal();
        buffer.writeShortLE(rarity);
        buffer.writeByte(object.getBattleParticleEffectType());
        buffer.writeByte(object.getPokemonData().getBallType());
        buffer.writeBoolean(isSelfFaction);
        if (isSelfFaction) {
            buffer.writeShortLE(object.getPokemonData().getPokemonAbilityIndex());
            short[] moves = object.getPokemonData().getMoves();
            for (int i = 0; i < moves.length; i++) {
                buffer.writeShortLE(moves[i]);
            }
        }
        PokemonDexData pokemonDexData = object.getPokemonData().getPokemonDexData();
        //pokemonStatBisMask
        if (isReLoadBattleStatsBroadcastMode) {
            switch(battleStatsBroadcastMode) {
                case BASE_STATS_WITH_BATTLE_MOD:
                    for (int i = 0; i < 8; i++) { // PokemonStatArray大小
                        if(isShowPokemonAbilityValue[i]){
                            PokemonStatType pokemonStatType = PokemonStatType.getByType(i);
                            if(i<6){
                                short abilityValue = pokemonDexData.getPokemonAbilityValue(pokemonStatType, object.getPokemonData().getPokemonIvs()[i], object.getPokemonData().getPokemonEvs()[i], object.getPokemonData().getLevel(), object.getPokemonData().getNatureType());
                                buffer.writeShortLE(abilityValue);//宝可梦的每项能力值
                            }
                            else{
                                buffer.writeShortLE(object.getStaticStats()[i]);//战斗中强化能力
                            }
                        }
                    }
                    break;
                case BASE_STAT_AND_CURRENT_BATTLE_STAT:
                    for (int i = 0; i < 8; i++) { // PokemonStatArray大小
                        if(isShowPokemonAbilityValue[i]){
                            PokemonStatType pokemonStatType = PokemonStatType.getByType(i);
                            if(i<6){
                                short abilityValue = pokemonDexData.getPokemonAbilityValue(pokemonStatType, object.getPokemonData().getPokemonIvs()[i], object.getPokemonData().getPokemonEvs()[i], object.getPokemonData().getLevel(), object.getPokemonData().getNatureType());
                                buffer.writeShortLE(abilityValue);//宝可梦的每项能力值
                                buffer.writeShortLE(object.getStaticStats()[i]);//战斗中强化能力
                            }
                            else{
                                buffer.writeShortLE(object.getStaticStats()[i]);//战斗中强化能力
                                buffer.writeShortLE(object.getStaticStats()[i]);//战斗中强化能力
                            }
                        }
                    }
                    break;
                case RANDOMS_SPEED_MINMAX:
                    for (int i = 0; i < 8; i++) { // PokemonStatArray大小
                        if(isShowPokemonAbilityValue[i]){
                            PokemonStatType pokemonStatType = PokemonStatType.getByType(i);
                            if(i<6){
                                short Expectation_50= pokemonDexData.getPokemonAbilityValue(pokemonStatType, object.getPokemonData().getPokemonIvs()[i], object.getPokemonData().getPokemonEvs()[i], 50, object.getPokemonData().getNatureType());
                                short Expectation_100 = pokemonDexData.getPokemonAbilityValue(pokemonStatType, object.getPokemonData().getPokemonIvs()[i], object.getPokemonData().getPokemonEvs()[i], 100, object.getPokemonData().getNatureType());
                                buffer.writeShortLE(Expectation_50);//宝可梦期望能力值50级
                                buffer.writeShortLE(Expectation_100);//宝可梦期望能力值100级
                            }
                            else{
                                buffer.writeShortLE(object.getStaticStats()[i]);//战斗中强化能力
                                buffer.writeShortLE(object.getStaticStats()[i]);//战斗中强化能力
                            }
                        }
                    }
                    break;
            }
        }
    }
}
