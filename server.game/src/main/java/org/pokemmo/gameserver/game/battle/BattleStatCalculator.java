package org.pokemmo.gameserver.game.battle;
import org.pokemmo.gameserver.codecs.BattleDebutPokemonCodec;
import org.pokemmo.gameserver.codecs.BattleTeamPokemonCodec;
import org.pokemmo.gameserver.game.battle.effect.*;
import org.pokemmo.gameserver.game.item.ItemData;
import org.pokemmo.gameserver.game.item.ItemManager;
import org.pokemmo.gameserver.game.move.MoveDamageType;
import org.pokemmo.gameserver.game.move.MoveManager;
import org.pokemmo.gameserver.game.move.PokemonMoveData;
import org.pokemmo.gameserver.game.pokemon.*;
import org.pokemmo.gameserver.game.string.BattleString;
import org.pokemmo.gameserver.game.string.BattleStringType;
import org.pokemmo.gameserver.protocol.GameProtocol;
import org.pokemmo.gameserver.protocol.packets.s2c.*;
import org.server.Session;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

final class BattleStatCalculator extends BattleContextComponent {
    BattleStatCalculator(BattleContextState context) {
        super(context);
    }

    public short getPokemonAttackStat(BattlePokemonData battlePokemonData){
        PokemonData pokemonData = battlePokemonData.getPokemonData();
        if (pokemonData == null) {
            return -1;
        }
        if (pokemonData.getPokemonDexData() == null) {
            return -1;
        }
        // 获取宝可梦的性格
        PokemonNatureType natureType = pokemonData.getNatureType();
        short attack = 0;
        // 计算能力值攻击值
        if(battlePokemonData.isInPowerTrick()){
            short defenseStat = pokemonData.getPokemonDexData().getPokemonAbilityValue(
                    PokemonStatType.DEFENSE,
                    pokemonData.getPokemonIvs()[PokemonStatType.DEFENSE.getType()],
                    pokemonData.getPokemonEvs()[PokemonStatType.DEFENSE.getType()],
                    pokemonData.getLevel(),
                    natureType);
            double staticStatRate = 1;
            if(battlePokemonData.getStaticStats()[PokemonStatType.ATTACK.getType()] > 0){
                staticStatRate = (2.0+battlePokemonData.getStaticStats()[PokemonStatType.ATTACK.getType()])/2.0;
            }
            else if(battlePokemonData.getStaticStats()[PokemonStatType.ATTACK.getType()] < 0){
                staticStatRate = 2.0/(2.0+Math.abs(battlePokemonData.getStaticStats()[PokemonStatType.ATTACK.getType()]));
            }
            attack = (short) ((double)defenseStat*staticStatRate);
        }
        else{
            short attackStat = pokemonData.getPokemonDexData().getPokemonAbilityValue(
                    PokemonStatType.ATTACK,
                    pokemonData.getPokemonIvs()[PokemonStatType.ATTACK.getType()],
                    pokemonData.getPokemonEvs()[PokemonStatType.ATTACK.getType()],
                    pokemonData.getLevel(),
                    natureType);
            double staticStatRate = 1;
            if(battlePokemonData.getStaticStats()[PokemonStatType.ATTACK.getType()] > 0){
                staticStatRate = (2.0+battlePokemonData.getStaticStats()[PokemonStatType.ATTACK.getType()])/2.0;
            }
            else if(battlePokemonData.getStaticStats()[PokemonStatType.ATTACK.getType()] < 0){
                staticStatRate = 2.0/(2.0+Math.abs(battlePokemonData.getStaticStats()[PokemonStatType.ATTACK.getType()]));
            }
            attack = (short) ((double)attackStat*staticStatRate);
        }
        //讲究头带1.5倍伤害
        if(battlePokemonData.getPokemonBattleItem() ==5220 || battlePokemonData.getPokemonBattleItem() ==6220){
            attack = (short) ((double)attack*1.5);
        }
        //卡拉卡拉 嘎啦嘎啦 粗骨头2倍攻击
        if(battlePokemonData.getPokemonData().getPokemonIndexId() == 104 || battlePokemonData.getPokemonData().getPokemonIndexId() == 105){
            if(battlePokemonData.getPokemonBattleItem() == 5228 || battlePokemonData.getPokemonBattleItem() == 6228){
                attack *= 2;
            }
        }
        //皮卡丘电气球2倍攻击
        if(battlePokemonData.getPokemonData().getPokemonIndexId() == 25){
            if(battlePokemonData.getPokemonBattleItem() == 5236 || battlePokemonData.getPokemonBattleItem() == 6236){
                attack *= 2;
            }
        }
        if(battleBasisInfo.getBattlePublicFieldInfo().isHasWeather() && battleBasisInfo.getBattlePublicFieldInfo().getWeatherRemainRound()>0) {
            switch (battleBasisInfo.getBattlePublicFieldInfo().getBattleWeatherType()) {
                case SUNNY:  //樱花儿花之礼晴天状态下1.5倍攻击
                    if (pokemonData.getPokemonIndexId() == 421 && pokemonData.getPokemonAbilityIndexId() == 122) {
                        attack = (short) ((double) attack * 1.5);
                    }
                    break;
            }
        }
        //大力士 瑜伽之力 2倍攻击
        if(battlePokemonData.getPokemonAbilityIndexId() == 65 || battlePokemonData.getPokemonAbilityIndexId() == 74){
            attack *= 2;
        }
        //毅力在异常状态1.5倍攻击
        if(battlePokemonData.getPokemonAbilityIndexId() == 62){
            if(pokemonData.getPokemonStatus() != PokemonStatusType.NORMAL){
                attack = (short) ((double) attack * 1.5);
            }
        }
        else{
            //烧伤0.5倍攻击
            if(pokemonData.getPokemonStatus() == PokemonStatusType.BURN){
                attack = (short) ((double) attack * 0.5);
            }
        }
        //中毒激升在中毒或者剧毒1.5倍攻击
        if(battlePokemonData.getPokemonAbilityIndexId() ==137){
            if(pokemonData.getPokemonStatus() == PokemonStatusType.POISON || pokemonData.getPokemonStatus() == PokemonStatusType.BAD_POISON){
                attack = (short) ((double) attack * 1.5);
            }
        }
        //活力1.5倍攻击
        if(battlePokemonData.getPokemonAbilityIndexId() ==55){
            attack = (short) ((double) attack * 1.5);
        }
        //软弱当hp小于50%时候0.5倍攻击
        if(battlePokemonData.getPokemonAbilityIndexId() ==129){
            short maxHp = pokemonData.getMaxHp();
            short currentHp = pokemonData.getCurrentHp();
            if(currentHp < maxHp*0.5){
                attack = (short) ((double) attack * 0.5);
            }
        }
        return attack;
    }

    public short getPokemonDefenseStat(BattlePokemonData battlePokemonData){
        PokemonData pokemonData = battlePokemonData.getPokemonData();
        if (pokemonData == null) {
            return -1;
        }
        if (pokemonData.getPokemonDexData() == null) {
            return -1;
        }
        // 获取宝可梦的性格
        PokemonNatureType natureType = pokemonData.getNatureType();
        short defense = 0;
        // 计算能力值防御值
        if(battlePokemonData.isInPowerTrick()){
            short attackStat = pokemonData.getPokemonDexData().getPokemonAbilityValue(
                    PokemonStatType.ATTACK,
                    pokemonData.getPokemonIvs()[PokemonStatType.ATTACK.getType()],
                    pokemonData.getPokemonEvs()[PokemonStatType.ATTACK.getType()],
                    pokemonData.getLevel(),
                    natureType);
            double staticStatRate = 1;
            if(battlePokemonData.getStaticStats()[PokemonStatType.DEFENSE.getType()] > 0){
                staticStatRate = (2.0+battlePokemonData.getStaticStats()[PokemonStatType.DEFENSE.getType()])/2.0;
            }
            else if(battlePokemonData.getStaticStats()[PokemonStatType.DEFENSE.getType()] < 0){
                staticStatRate = 2.0/(2.0+Math.abs(battlePokemonData.getStaticStats()[PokemonStatType.DEFENSE.getType()]));
            }
            defense = (short) ((double)attackStat*staticStatRate);
        }
        else{
            short defenseStat = pokemonData.getPokemonDexData().getPokemonAbilityValue(
                    PokemonStatType.DEFENSE,
                    pokemonData.getPokemonIvs()[PokemonStatType.DEFENSE.getType()],
                    pokemonData.getPokemonEvs()[PokemonStatType.DEFENSE.getType()],
                    pokemonData.getLevel(),
                    natureType);
            double staticStatRate = 1;
            if(battlePokemonData.getStaticStats()[PokemonStatType.DEFENSE.getType()] > 0){
                staticStatRate = (2.0+battlePokemonData.getStaticStats()[PokemonStatType.DEFENSE.getType()])/2.0;
            }
            else if(battlePokemonData.getStaticStats()[PokemonStatType.DEFENSE.getType()] < 0){
                staticStatRate = 2.0/(2.0+Math.abs(battlePokemonData.getStaticStats()[PokemonStatType.DEFENSE.getType()]));
            }
            defense= (short) ((double)defenseStat*staticStatRate);
        }
        //当存在奇妙空间时候，防御与特防基础能力值对调
        if(battleBasisInfo.getBattlePublicFieldInfo().isHasWonderRoom() && battleBasisInfo.getBattlePublicFieldInfo().getWonderRoomRemainRound() > 0){
            short spDefenseStat = pokemonData.getPokemonDexData().getPokemonAbilityValue(
                    PokemonStatType.SPECIAL_DEFENSE,
                    pokemonData.getPokemonIvs()[PokemonStatType.SPECIAL_DEFENSE.getType()],
                    pokemonData.getPokemonEvs()[PokemonStatType.SPECIAL_DEFENSE.getType()],
                    pokemonData.getLevel(),
                    natureType);
            double staticStatRate = 1;
            if(battlePokemonData.getStaticStats()[PokemonStatType.DEFENSE.getType()] > 0){
                staticStatRate = (2.0+battlePokemonData.getStaticStats()[PokemonStatType.DEFENSE.getType()])/2.0;
            }
            else if(battlePokemonData.getStaticStats()[PokemonStatType.DEFENSE.getType()] < 0){
                staticStatRate = 2.0/(2.0+Math.abs(battlePokemonData.getStaticStats()[PokemonStatType.DEFENSE.getType()]));
            }
            defense = (short) ((double)spDefenseStat*staticStatRate);
        }
        //百变怪金属粉2倍防御
        if(pokemonData.getPokemonIndexId() == 132){
            if(battlePokemonData.getPokemonBattleItem() == 5257 || battlePokemonData.getPokemonBattleItem() == 6257){
                defense *=2;
            }
        }
        //进化奇石在宝可梦还有进化形态获得1.5倍防御
        if(pokemonData.getItem() == 5538 || pokemonData.getItem() == 6538){
            PokemonDexData pokemonInfo = pokemonData.getPokemonDexData();
            if(pokemonInfo.getPokemonEvolutions().size() > 0){
                defense = (short)((double)defense *1.5);
            }
        }
        //神奇鳞片特性异常状态1.5倍防御
        if(battlePokemonData.getPokemonAbilityIndexId() == 63 && pokemonData.getPokemonStatus() != PokemonStatusType.NORMAL){
            defense = (short)((double)defense *1.5);
        }
        return defense;
    }

    public short getPokemonSpAttackStat(BattlePokemonData battlePokemonData){
        byte factionIndex = battlePokemonData.getDebutFactionIndex();
        PokemonData pokemonData = battlePokemonData.getPokemonData();
        if (pokemonData == null) {
            return -1;
        }
        if (pokemonData.getPokemonDexData() == null) {
            return -1;
        }
        // 获取宝可梦的性格
        PokemonNatureType natureType = pokemonData.getNatureType();
        // 计算能力值特攻值
        short spAttack = pokemonData.getPokemonDexData().getPokemonAbilityValue(
                PokemonStatType.SPECIAL_ATTACK,
                pokemonData.getPokemonIvs()[PokemonStatType.SPECIAL_ATTACK.getType()],
                pokemonData.getPokemonEvs()[PokemonStatType.SPECIAL_ATTACK.getType()],
                pokemonData.getLevel(),
                natureType
        );
        double staticStatRate = 1;
        if(battlePokemonData.getStaticStats()[PokemonStatType.SPECIAL_ATTACK.getType()] > 0){
            staticStatRate = (2.0+battlePokemonData.getStaticStats()[PokemonStatType.SPECIAL_ATTACK.getType()])/2.0;
        }
        else if(battlePokemonData.getStaticStats()[PokemonStatType.SPECIAL_ATTACK.getType()] < 0){
            staticStatRate = 2.0/(2.0+Math.abs(battlePokemonData.getStaticStats()[PokemonStatType.SPECIAL_ATTACK.getType()]));
        }
        spAttack = (short) ((double)spAttack*staticStatRate);
        //讲究眼镜1.5倍攻击
        if(battlePokemonData.getPokemonBattleItem() == 5297|| battlePokemonData.getPokemonBattleItem() == 6297){
            spAttack = (short)((double)spAttack *1.5);
        }
        //拉帝亚斯 拉帝欧斯心之水滴1.5倍特防
        if(pokemonData.getPokemonIndexId() == 380 || pokemonData.getPokemonIndexId() == 381){
            if(battlePokemonData.getPokemonBattleItem() == 5225 || battlePokemonData.getPokemonBattleItem() == 6225){
                spAttack = (short)((double)spAttack *1.5);
            }
        }
        //珍珠贝深海之牙2倍特攻
        if(pokemonData.getPokemonIndexId() == 366){
            if(battlePokemonData.getPokemonBattleItem() == 5226 || battlePokemonData.getPokemonBattleItem() == 6226){
                spAttack *=2;
            }
        }
        //皮卡丘电气球2倍特攻
        if(battlePokemonData.getPokemonData().getPokemonIndexId() == 25){
            if(battlePokemonData.getPokemonBattleItem() == 5236 || battlePokemonData.getPokemonBattleItem() == 6236){
                spAttack *= 2;
            }
        }
        //负电正电当场上同伴存在负电正电特性1.5倍特攻
        if(battlePokemonData.getPokemonAbilityIndexId() == 57 || battlePokemonData.getPokemonAbilityIndexId() == 58){
            for(BattlePokemonData battlePokemon : debutFactions.get(factionIndex).getDebutAlivePokemons()){
                if(battlePokemon.getPokemonAbilityIndexId() == 57 || battlePokemon.getPokemonAbilityIndexId() == 58){
                    spAttack = (short)((double)spAttack *1.5);
                }
            }
        }
        //太阳之力在晴天下1.5倍特攻
        if(battleBasisInfo.getBattlePublicFieldInfo().getBattleWeatherType() == BattleWeatherType.SUNNY){
            if(battlePokemonData.getPokemonAbilityIndexId() == 94){
                spAttack = (short)((double)spAttack *1.5);
            }
        }
        //软弱当hp小于50%时候0.5倍特攻
        if(battlePokemonData.getPokemonAbilityIndexId() ==129){
            short maxHp = pokemonData.getMaxHp();
            short currentHp = pokemonData.getCurrentHp();
            if(currentHp < maxHp*0.5){
                spAttack = (short) ((double) spAttack * 0.5);
            }
        }
        return spAttack;
    }

    public short getPokemonSpDefenseStat(BattlePokemonData battlePokemonData){
        PokemonData pokemonData = battlePokemonData.getPokemonData();
        if (pokemonData == null) {
            return -1;
        }
        if (pokemonData.getPokemonDexData() == null) {
            return -1;
        }
        // 获取宝可梦的性格
        PokemonNatureType natureType = pokemonData.getNatureType();
        short spDefense = 0;
        //当存在奇妙空间时候，防御与特防基础能力值对调
        if(battleBasisInfo.getBattlePublicFieldInfo().isHasWonderRoom() && battleBasisInfo.getBattlePublicFieldInfo().getWonderRoomRemainRound() > 0){
            spDefense = pokemonData.getPokemonDexData().getPokemonAbilityValue(
                    PokemonStatType.DEFENSE,
                    pokemonData.getPokemonIvs()[PokemonStatType.DEFENSE.getType()],
                    pokemonData.getPokemonEvs()[PokemonStatType.DEFENSE.getType()],
                    pokemonData.getLevel(),
                    natureType
            );
            double staticStatRate = 1;
            if(battlePokemonData.getStaticStats()[PokemonStatType.SPECIAL_DEFENSE.getType()] > 0){
                staticStatRate = (2.0+battlePokemonData.getStaticStats()[PokemonStatType.SPECIAL_DEFENSE.getType()])/2.0;
            }
            else if(battlePokemonData.getStaticStats()[PokemonStatType.SPECIAL_DEFENSE.getType()] < 0){
                staticStatRate = 2.0/(2.0+Math.abs(battlePokemonData.getStaticStats()[PokemonStatType.SPECIAL_DEFENSE.getType()]));
            }
            spDefense = (short) ((double)spDefense*staticStatRate);
        }
        else
        {
            // 计算能力值特防值
            spDefense = pokemonData.getPokemonDexData().getPokemonAbilityValue(
                    PokemonStatType.SPECIAL_DEFENSE,
                    pokemonData.getPokemonIvs()[PokemonStatType.SPECIAL_DEFENSE.getType()],
                    pokemonData.getPokemonEvs()[PokemonStatType.SPECIAL_DEFENSE.getType()],
                    pokemonData.getLevel(),
                    natureType
            );
            double staticStatRate = 1;
            if(battlePokemonData.getStaticStats()[PokemonStatType.SPECIAL_DEFENSE.getType()] > 0){
                staticStatRate = (2.0+battlePokemonData.getStaticStats()[PokemonStatType.SPECIAL_DEFENSE.getType()])/2.0;
            }
            else if(battlePokemonData.getStaticStats()[PokemonStatType.SPECIAL_DEFENSE.getType()] < 0){
                staticStatRate = 2.0/(2.0+Math.abs(battlePokemonData.getStaticStats()[PokemonStatType.SPECIAL_DEFENSE.getType()]));
            }
            spDefense = (short) ((double)spDefense*staticStatRate);
        }
        //拉帝亚斯 拉帝欧斯心之水滴1.5倍特防
        if(pokemonData.getPokemonIndexId() == 380 || pokemonData.getPokemonIndexId() == 381){
            if(battlePokemonData.getPokemonBattleItem() == 5225 || battlePokemonData.getPokemonBattleItem() == 6225){
                spDefense = (short)((double)spDefense *1.5);
            }
        }
        //珍珠贝深海鳞片2倍特防
        if(pokemonData.getPokemonIndexId() == 336){
            if(battlePokemonData.getPokemonBattleItem() == 5227 || battlePokemonData.getPokemonBattleItem() == 6227){
                spDefense = (short)((double)spDefense *2);
            }
        }
        //突击背心1.5倍特防
        if(battlePokemonData.getPokemonBattleItem() == 1423){
            spDefense = (short)((double)spDefense *1.5);
        }
        if(battleBasisInfo.getBattlePublicFieldInfo().isHasWeather() && battleBasisInfo.getBattlePublicFieldInfo().getWeatherRemainRound()>0){
            switch (battleBasisInfo.getBattlePublicFieldInfo().getBattleWeatherType()){
                case SUNNY://樱花儿花之礼晴天状态下1.5倍特防
                    if(pokemonData.getPokemonIndexId() == 421 && pokemonData.getPokemonAbilityIndexId() == 122){
                        spDefense = (short)((double)spDefense *1.5);
                    }
                    break;
                case SANDSTORM://沙暴天气下岩石属性特防1.5倍
                    if(pokemonData.getPokemonFirstType() == PokemonType.ROCK || pokemonData.getPokemonSecondType() == PokemonType.ROCK){
                        spDefense = (short)((double)spDefense *1.5);
                    }
                    break;
            }
        }
        return spDefense;
    }

    public short getPokemonSpeedStat(BattlePokemonData battlePokemonData) {
        byte factionIndex = battlePokemonData.getDebutFactionIndex();
        PokemonData pokemonData = battlePokemonData.getPokemonData();
        if (pokemonData == null) {
            return -1;
        }
        if (pokemonData.getPokemonDexData() == null) {
            return -1;
        }
        // 获取宝可梦的性格
        PokemonNatureType natureType = pokemonData.getNatureType();
        // 计算能力值速度值
        short speed = pokemonData.getPokemonDexData().getPokemonAbilityValue(
                PokemonStatType.SPEED,
                pokemonData.getPokemonIvs()[PokemonStatType.SPEED.getType()],
                pokemonData.getPokemonEvs()[PokemonStatType.SPEED.getType()],
                pokemonData.getLevel(),
                natureType
        );
        double staticStatRate = 1;
        if(battlePokemonData.getStaticStats()[PokemonStatType.SPEED.getType()] > 0){
            staticStatRate = (2.0+battlePokemonData.getStaticStats()[PokemonStatType.SPEED.getType()])/2.0;
        }
        else if(battlePokemonData.getStaticStats()[PokemonStatType.SPEED.getType()] < 0){
            staticStatRate = 2.0/(2.0+Math.abs(battlePokemonData.getStaticStats()[PokemonStatType.SPEED.getType()]));
        }
        //百变怪速度粉2倍速度
        if(pokemonData.getPokemonIndexId() == 132){
            if(battlePokemonData.getPokemonBattleItem() == 5274 || battlePokemonData.getPokemonBattleItem() == 6274){
                speed *=2;
            }
        }
        //讲究围巾1.5倍速度
        if(battlePokemonData.getPokemonBattleItem() == 5287 || battlePokemonData.getPokemonBattleItem() == 6287){
            speed = (short)((double)speed *1.5);
        }
        //顺风2倍速度
        if(debutFactions.get(factionIndex).getFieldInfo().isHasTailWindField()&&debutFactions.get(factionIndex).getFieldInfo().getTailWindRemainRound()>0){
            speed *=2;
        }
        if(battleBasisInfo.getBattlePublicFieldInfo().isHasWeather() && battleBasisInfo.getBattlePublicFieldInfo().getWeatherRemainRound()>0){
            switch (battleBasisInfo.getBattlePublicFieldInfo().getBattleWeatherType()){
                case RAIN:
                    if(pokemonData.getPokemonAbilityIndexId() == 33){
                        speed *=2;
                    }
                    break;
                case SUNNY:
                    if(pokemonData.getPokemonAbilityIndexId() == 34){
                        speed *=2;
                    }
                    break;
                case SANDSTORM:
                    if(pokemonData.getPokemonAbilityIndexId() == 146){
                        speed *=2;
                    }
                    break;
            }
            //轻装特性无道具2倍速度
            if(battlePokemonData.getPokemonBattleItem() == -1 && battlePokemonData.getPokemonAbilityIndexId() == 84){
                speed *=2;
            }
            //飞毛腿特性处于异常状态1.5倍速度
            if(pokemonData.getPokemonAbilityIndexId() == 95 &&pokemonData.getPokemonStatus() != PokemonStatusType.NORMAL){
                speed = (short)((double)speed *1.5);
            }
        }
        return (short) ((double)speed*staticStatRate);
    }
}

