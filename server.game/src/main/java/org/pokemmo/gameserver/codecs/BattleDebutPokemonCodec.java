package org.pokemmo.gameserver.codecs;
import org.pokemmo.gameserver.game.battle.BattlePokemonData;
import org.server.bytes.ByteBufEx;
import org.pokemmo.gameserver.game.pokemon.PokemonRarity;
import org.pokemmo.gameserver.game.battle.BattleFormType;
import org.pokemmo.gameserver.game.pokemon.PokemonDexData;
import org.pokemmo.gameserver.game.pokemon.PokemonType;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class BattleDebutPokemonCodec implements ObjectCodec<BattlePokemonData>{
    private final byte battleTeamIndex;
    private final BattleFormType battleFormType;
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
        buffer.writeBoolean(object.isHasDebutPokemon());
        if(!object.isHasDebutPokemon()){
            return;
        }
        buffer.writeByte(this.battleTeamIndex);
        buffer.writeByte(object.getPokemonData().getContainerPosition());
        buffer.writeShortLE(object.getPokemonData().getPokemonIndexId());
        buffer.writeByte(object.getPokemonData().getLevel());
        buffer.writeUtf16LE(object.getPokemonData().getName());
        PokemonDexData pokemonDexData = object.getPokemonData().getPokemonDexData();
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
        byte pokemonSex = (object.getPokemonData().getPersonalityValue() & 255) >= pokemonDexData.getGenderRatio() ? (byte) 0 : (byte) 1;
        buffer.writeByte(pokemonSex);
        buffer.writeByte(object.getPokemonData().getFormType());
        buffer.writeByte(object.getPokemonData().getBallType());
        buffer.writeByte(object.getBattleParticleEffectType());
        int debutPokemonInfoFlag = 0;
        if(object.isAlreadyUseSkill())
            debutPokemonInfoFlag = debutPokemonInfoFlag  | 1;
        if(object.isItemBlock())
            debutPokemonInfoFlag = debutPokemonInfoFlag  | 2;
        if(object.isTaunt())
            debutPokemonInfoFlag = debutPokemonInfoFlag  | 4;
        if(object.isTorment())
            debutPokemonInfoFlag = debutPokemonInfoFlag  | 8;
        if(object.isHealBlock())
            debutPokemonInfoFlag = debutPokemonInfoFlag  | 16;
        if(object.isHasDisableUseSkill())
            debutPokemonInfoFlag = debutPokemonInfoFlag  | 32;
        if(object.isInEncore())
            debutPokemonInfoFlag = debutPokemonInfoFlag  | 64;
        if(object.isItemLockSkill())
            debutPokemonInfoFlag = debutPokemonInfoFlag  | 128;
        if(object.isReloadSkill())
            debutPokemonInfoFlag = debutPokemonInfoFlag  | 256;
        if(object.isReloadPokemonData())
            debutPokemonInfoFlag = debutPokemonInfoFlag  | 512;
        if(object.isOnGround())
            debutPokemonInfoFlag = debutPokemonInfoFlag  | 1024;
        if(object.isCanHurtByNormalAndFightType())
            debutPokemonInfoFlag = debutPokemonInfoFlag  | 2048;
        if(object.isCanHurtByPsychicType())
            debutPokemonInfoFlag = debutPokemonInfoFlag  | 4096;
        if(object.isReloadPokemonType())
            debutPokemonInfoFlag = debutPokemonInfoFlag  | 8192;
        if(object.isReloadPokemonAbility())
            debutPokemonInfoFlag = debutPokemonInfoFlag  | 16384;
        if(object.isReloadPokemonIndexId())
            debutPokemonInfoFlag = debutPokemonInfoFlag  | 65536;
        if(object.isPokemonAggrandizement())
            debutPokemonInfoFlag = debutPokemonInfoFlag  | 131092;
        if(object.isBoss())
            debutPokemonInfoFlag = debutPokemonInfoFlag  | 262144;
        if(object.isResetPokemonSkill())
            debutPokemonInfoFlag = debutPokemonInfoFlag  | 524288;
        if(object.isNotSetPokemonSpriteRenderAnimationSpeed())
            debutPokemonInfoFlag = debutPokemonInfoFlag  | 32768;

        buffer.writeIntLE(debutPokemonInfoFlag);
        if(object.isHealBlock()){
            buffer.writeBoolean(object.isIngrain());
            buffer.writeBoolean(object.isFallen());
            buffer.writeBoolean(object.isVinesEntangled());
        }
        if(object.isTorment()){
            buffer.writeShortLE(object.getLastUseSkillIndexId());
        }
        if(object.isHasDisableUseSkill()){
            buffer.writeShortLE(object.getDisableSkillIndexId());
        }
        if(object.isInEncore()){
            buffer.writeShortLE(object.getEncoreLockedSkillIndexId());
        }
        if(object.isItemLockSkill()){
            buffer.writeShortLE(object.getItemLockedSkillIndexId());
            buffer.writeShortLE(object.getItemLockedIndexId());
        }
        if(object.isReloadSkill()) {
            short[] moves = object.getPokemonData().getMoves();
            for (byte i = 0; i < 4; i++) {
                buffer.writeShortLE(moves[i]);
            }
        }
        if(object.isReloadPokemonData()){
            buffer.writeShortLE(object.getPokemonData().getPokemonIndexId());
            buffer.writeByte(object.getPokemonData().getFormType());
            short[] moves = object.getPokemonData().getMoves();
            short[] movePps = object.getPokemonData().getMovesPp();
            for (byte i = 0; i < 4; i++) {
                buffer.writeShortLE(moves[i]);
                buffer.writeByte(movePps[i]);
            }
            buffer.writeShortLE(pokemonDexData.getPokemonAbilities().get(object.getPokemonData().getPokemonAbilityIndex()).getAbilityIndexId());
        }
        if (object.isReloadPokemonType()) {
            PokemonType firstType = object.getPokemonFirstType();
            PokemonType secondType = object.getPokemonSecondType();
            buffer.writeByte(firstType.getType());
            buffer.writeByte(secondType.getType());
        }
        if(object.isReloadPokemonAbility()){
            buffer.writeShortLE(pokemonDexData.getPokemonAbilities().get(object.getPokemonData().getPokemonAbilityIndex()).getAbilityIndexId());
        }
        if(object.isReloadPokemonIndexId()){
            buffer.writeShortLE(object.getPokemonData().getPokemonIndexId());
        }
        if(object.isBoss()){
            buffer.writeByte(object.getBossType().getType());
        }
        if(object.isResetPokemonSkill()){
            short[] moves = object.getPokemonData().getMoves();
            short[] movePps = object.getPokemonData().getMovesPp();
            for (byte i = 0; i < 4; i++) {
                buffer.writeShortLE(moves[i]);
                buffer.writeByte(movePps[i]);
            }
        }
        int packedStats = 0;
        byte[] staticStats = object.getStaticStats();
        for(int i = 0;i<8;i++){
            int res =  ((staticStats[i] & 0xF) + 6) << (i * 4);
            packedStats |= res;
        }
        buffer.writeIntLE(packedStats);
        if(this.battleFormType == BattleFormType.CONTEST){
            buffer.writeByte(0);//enemyPanelRowIndex
            buffer.writeByte(0);//slotIndex
            buffer.writeByte(0);//unk
            buffer.writeShortLE(0);//unk
        }
        else if(this.battleFormType == BattleFormType.COOP_RAID){
            buffer.writeByte(0);//itemTypeLocalIndex
            if(object.getBossType().isAlpha()){
                buffer.writeByte(0);//transformationState
            }
        }
    }
}
