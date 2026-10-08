package org.pokemmo.gameserver.protocol.packets.s2c;

import org.server.OutgoingPacket;
import org.pokemmo.gameserver.game.pokemon.PokemonManager;
import org.server.bytes.ByteBufEx;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

public class SendGameGlobalConfigsPacket extends OutgoingPacket {
  // This packet also triggers the title scrambling
  // we update the inner item database of the client
  @Getter
  @RequiredArgsConstructor
  private static class UnknownType {
    private final short unk1;
    private final byte unk2;
  }
  private final byte unk1;
  private final short creatGuildTeamFee;
  private final float surchargeRate;
  private final short gtlMinItemPrice;
  private final short gtlPokemonMinPrice;
  private final short gtlMaxSurcharge;
  private final int gtlMinShinyPokemonPrice;
  private final byte activeCureSkillMinLimitPp;
  private final byte flagUnk9;
  private final short[] unk10;
  private final byte[] unShowTypeParticleEffects;
  //只记录宝可梦的最初进化索引id
  private final short[] unlockedAlphaPokemons;
  private final short[] firstPartnerPokemons ;
  private final short[] cnyFlags = new short[0];
  private final UnknownType[] unk14;
  public SendGameGlobalConfigsPacket() {
    this.unk1 = 75;
    this.creatGuildTeamFee = 15000;
    this.surchargeRate = 0.05f;
    this.gtlMinItemPrice = 100;
    this.gtlPokemonMinPrice = 1000;
    this.gtlMaxSurcharge = 25000;
    this.gtlMinShinyPokemonPrice = 250000;
    this.activeCureSkillMinLimitPp = 5;
    this.flagUnk9 = 1 | 1 << 1 | 1 << 3 | 1 << 4 | 1<<5| 1 << 6;
    this.unk10 = new short[]{135,255,246,33};
    this.unShowTypeParticleEffects = new byte[0];
    // 图鉴白名单: 直接取已加载的图鉴数据库全量列表(按编号排序);
    // 新增精灵只要加入服务端图鉴数据, 重启后自动进入客户端图鉴, 无需改此文件
    java.util.List<Short> dexIds = new java.util.ArrayList<>();
    PokemonManager.getAllPokemonDexData().stream()
            .mapToInt(d -> d.getPokemonIndexId()).sorted()
            .forEach(i -> dexIds.add((short) i));
    this.unlockedAlphaPokemons = new short[dexIds.size()];
    for (int di = 0; di < dexIds.size(); di++) {
        this.unlockedAlphaPokemons[di] = dexIds.get(di);
    }
    this.firstPartnerPokemons = new short[0];
    this.unk14 = new UnknownType[0];
  }

  @Override
  public void encode(ByteBufEx buffer) throws Exception {
    buffer.writeByte(unk1);
    buffer.writeShortLE(creatGuildTeamFee);
    buffer.writeFloatLE(surchargeRate);
    buffer.writeShortLE(gtlMinItemPrice);
    buffer.writeShortLE(gtlPokemonMinPrice);
    buffer.writeShortLE(gtlMaxSurcharge);
    buffer.writeIntLE(gtlMinShinyPokemonPrice);
    buffer.writeByte(activeCureSkillMinLimitPp);
    buffer.writeByte(flagUnk9);
    buffer.writeShortLE(unk10.length);
    for (short unk : unk10) {
      buffer.writeShortLE(unk);
    }

    buffer.writeByte(unShowTypeParticleEffects.length);
    for (byte unShowTypeParticleEffect : unShowTypeParticleEffects) {
      buffer.writeByte(unShowTypeParticleEffect);
    }
    buffer.writeShortLE(unlockedAlphaPokemons.length);
    for (short unlockedAlphaPokemon : unlockedAlphaPokemons) {
      buffer.writeShortLE(unlockedAlphaPokemon);
    }
    buffer.writeShortLE(firstPartnerPokemons .length);
    for (short firstPartnerPokemon : firstPartnerPokemons ) {
      buffer.writeShortLE(firstPartnerPokemon);
    }
    buffer.writeByte(cnyFlags.length);
    for (short cnyFlag : cnyFlags) {
      buffer.writeShortLE(cnyFlag);
    }
    buffer.writeShortLE(unk14.length);
    for (UnknownType unk : unk14) {
      buffer.writeShortLE(unk.unk1);
      buffer.writeByte(unk.unk2);
    }
  }
}
