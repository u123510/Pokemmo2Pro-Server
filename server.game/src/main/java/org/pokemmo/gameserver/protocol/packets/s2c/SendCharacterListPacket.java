package org.pokemmo.gameserver.protocol.packets.s2c;

import org.pokemmo.gameserver.game.character.CharacterData;
import org.pokemmo.gameserver.game.pokemon.PokemonData;
import org.server.OutgoingPacket;
import org.server.bytes.ByteBufEx;
import org.pokemmo.gameserver.codecs.Codecs;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public class SendCharacterListPacket extends OutgoingPacket {
  private final Map<CharacterData, List<PokemonData>> characterPokemonMap;

  public SendCharacterListPacket() {
    this.characterPokemonMap = Map.of();
  }

  @Override
  public void encode(ByteBufEx buffer) throws Exception {
    buffer.writeByte(characterPokemonMap.size());
    for (Map.Entry<CharacterData, List<PokemonData>> entry : characterPokemonMap.entrySet()) {
      CharacterData character = entry.getKey();
      List<PokemonData> pokemons = entry.getValue();
      Codecs.CHARACTER_CODEC_NO_MAC.encode(buffer, character);
      Codecs.SKIN_CODEC_1.encode(buffer, character);
      Codecs.SKIN_CODEC_2.encode(buffer, character);
      Codecs.CHARACTER_GUILD_CODEC.encode(buffer, character);

      buffer.writeByte(pokemons.size());
      for (PokemonData pokemon : pokemons) {
        Codecs.POKEMON_CODEC.encode(buffer, pokemon);
      }
    }
  }
}
