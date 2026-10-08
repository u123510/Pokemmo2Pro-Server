package org.pokemmo.gameserver.protocol.packets.c2s;

import com.google.inject.Inject;
import org.pokemmo.gameserver.game.character.CharacterData;
import org.pokemmo.gameserver.game.container.PokemonContainerType;
import org.pokemmo.gameserver.game.pokemon.PokemonData;
import org.server.IncomingPacket;
import org.server.Session;
import org.server.bytes.ByteBufEx;
import org.pokemmo.db.jooq.tables.records.CharacterRecord;
import org.pokemmo.db.jooq.tables.records.ContainerRecord;
import org.pokemmo.db.jooq.tables.records.PokemonRecord;
import org.pokemmo.gameserver.protocol.GameProtocol;
import org.pokemmo.gameserver.protocol.packets.s2c.SendCharacterListPacket;
import org.pokemmo.gameserver.services.GameServerService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StartCharacterSelectionPacket extends IncomingPacket {
  @Inject
  private GameServerService characterService;
  @Override
  public void decode(ByteBufEx buffer) {}

  @Override
  public void handle(Session session) throws Exception {
    Integer accountId = session.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get().getAccountData().getAccountId();
    if (accountId == null) {
      session.close();
      return;
    }
    List<CharacterData> characters = characterService.getCharacters(accountId);
    if (characters.isEmpty()) {
      session.send(new SendCharacterListPacket());
      return;
    }
    Map<CharacterData, List<PokemonData>> characterPokemonMap = new HashMap<>();
    for (CharacterData character : characters) {
      ContainerRecord partyContainer = characterService.getContainerByType(PokemonContainerType.PARTY);
      characterPokemonMap.put(character, characterService.getCharacterContainerPokemons(character.getPlayerEntity().getEntityGameId(), partyContainer));
    }
    session.send(new SendCharacterListPacket(characterPokemonMap));
  }
}
