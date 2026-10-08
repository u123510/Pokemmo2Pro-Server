package org.pokemmo.gameserver.game.battle;

import lombok.Getter;

@Getter
public class BattlePublicFieldInfo {
    private boolean hasWeather;
    private BattleWeatherType battleWeatherType;
    private int weatherRemainRound;

    private boolean hasTrickRoom;//是否存在戏法空间
    private byte trickRoomRemainRound;//戏法空间剩余回合数

    private boolean hasWonderRoom;//是否存在奇妙空间
    private byte wonderRoomRemainRound;//奇妙空间剩余回合数

    private boolean hasMagicRoom;//是否存在魔法空间
    private byte magicRoomRemainRound;//魔法空间剩余回合数

    private boolean hasGravityField;//是否存在重力场地
    private byte gravityFieldRemainRound;//重力场地剩余回合数

    private boolean hasInverseBattleEffect;//是否存在首次Pokemon类型反转和免疫无效
    private byte inverseBattleEffectRemainRound;//首次Pokemon类型反转和免疫无效剩余回合数

    private boolean hasDamageTypeReverseEffect;//是否存在反转对战效果
    private byte damageTypeReverseEffectRemainRound;//反转对战效果剩余回合数

    public void setPublicWeather(BattleWeatherType battleWeatherType, int weatherRemainRound) {
        if(battleWeatherType != BattleWeatherType.NORMAL && weatherRemainRound>0){
            this.hasWeather = true;
        }
        else{
            this.hasWeather = false;
        }
        this.battleWeatherType = battleWeatherType;
        this.weatherRemainRound = weatherRemainRound;
    }
    public void setTrickRoomData(int remainRound){
        if(remainRound>0){
            this.hasTrickRoom = true;
        }
        else{
            this.hasTrickRoom = false;
        }
        this.trickRoomRemainRound = (byte)remainRound;
    }
    public void setWonderRoomData(int remainRound){
        if(remainRound>0){
            this.hasWonderRoom = true;
        }
        else{
            this.hasWonderRoom = false;
        }
        this.wonderRoomRemainRound = (byte)remainRound;
    }
    public void setMagicRoomData(int remainRound){
        if(remainRound>0){
            this.hasMagicRoom = true;
        }
        else{
            this.hasMagicRoom = false;
        }
        this.magicRoomRemainRound = (byte)remainRound;
    }
    public void setGravityFieldData(int remainRound){
        if(remainRound>0){
            this.hasGravityField = true;
        }
        else{
            this.hasGravityField = false;
        }
        this.gravityFieldRemainRound = (byte)remainRound;
    }
    public void setInverseBattleEffect(int remainRound){
        if(remainRound>0) {
            this.hasInverseBattleEffect = true;
        }
        else{
            this.hasInverseBattleEffect = false;
        }
        this.inverseBattleEffectRemainRound = (byte)remainRound;
    }
    public void setDamageTypeReverseEffect(int remainRound){
        if(remainRound>0){
            this.hasDamageTypeReverseEffect = true;
        }
        else{
            this.hasDamageTypeReverseEffect = false;
        }
        this.damageTypeReverseEffectRemainRound = (byte)remainRound;
    }
}
