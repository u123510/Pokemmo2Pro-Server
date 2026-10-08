package org.pokemmo.gameserver.protocol.packets.c2s;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.pokemmo.gameserver.game.character.CharacterManager;
import org.pokemmo.gameserver.game.container.PokemonContainerType;
import org.pokemmo.gameserver.game.pokemon.PokemonData;
import org.pokemmo.gameserver.game.pokemon.UpdatePokemonData;
import org.pokemmo.gameserver.game.trade.TradeManager;
import org.pokemmo.gameserver.protocol.GameProtocol;
import org.pokemmo.gameserver.protocol.packets.s2c.SendInventoryPacket;
import org.pokemmo.gameserver.protocol.packets.s2c.SendUpdatePokemonDataPacket;
import org.pokemmo.gameserver.services.GameServerService;
import org.pokemmo.gameserver.util.SnowflakeIdGenerator;
import org.pokemmo.db.jooq.tables.records.ContainerRecord;
import org.pokemmo.db.jooq.tables.records.InventoryRecord;
import org.server.IncomingPacket;
import org.server.Session;
import org.server.bytes.ByteBufEx;

/** Handles the client's held-item equip/remove request (C2S 0x0F). */
@Slf4j
public final class UpdatePokemonItemPacket extends IncomingPacket {
    private static final int PAYLOAD_SIZE = Long.BYTES + Short.BYTES + Byte.BYTES;

    private long pokemonId;
    private short itemIndexId;
    private int containerId;

    @Inject
    private GameServerService gameServerService;
    @Inject
    private SnowflakeIdGenerator idGenerator;

    @Override
    public void decode(ByteBufEx buffer) {
        if (buffer.readableBytes() != PAYLOAD_SIZE) {
            throw new IllegalArgumentException(
                    "宝可梦携带道具封包长度错误: expected=" + PAYLOAD_SIZE
                            + ", actual=" + buffer.readableBytes());
        }
        pokemonId = buffer.readLongLE();
        itemIndexId = buffer.readShortLE();
        containerId = buffer.readUnsignedByte();
        if (pokemonId <= 0) {
            throw new IllegalArgumentException("宝可梦 Object ID 必须为正数: " + pokemonId);
        }
        if (itemIndexId < -1) {
            throw new IllegalArgumentException("非法的携带道具索引: " + itemIndexId);
        }
        if (containerId != PokemonContainerType.PC.getType()
                && containerId != PokemonContainerType.PARTY.getType()) {
            throw new IllegalArgumentException("非法的宝可梦容器类型: " + containerId);
        }
    }

    @Override
    public void handle(Session session) {
        CharacterManager characterManager =
                session.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get();
        if (characterManager == null || characterManager.getCharacterData() == null
                || characterManager.getCharacterData().getPlayerEntity() == null) {
            log.warn("忽略没有角色上下文的宝可梦携带道具封包");
            return;
        }
        if (TradeManager.isInTrade(characterManager)
                || TradeManager.isPokemonOffered(characterManager, pokemonId)) {
            log.warn("拒绝交易期间或已报价宝可梦的携带道具修改: pokemonId={}", pokemonId);
            return;
        }

        long characterId = characterManager.getCharacterData().getPlayerEntity().getEntityGameId();
        if (!gameServerService.updatePokemonItem(
                characterId, pokemonId, containerId, itemIndexId, idGenerator)) {
            log.warn("宝可梦携带道具修改未应用: characterId={}, pokemonId={}, itemIndexId={}, containerId={}",
                    characterId, pokemonId, itemIndexId, containerId);
            return;
        }

        PokemonData updatedPokemon = findOwnedPokemon(characterManager, characterId);
        if (updatedPokemon == null) {
            log.error("宝可梦携带道具已写入但无法重新读取对象: characterId={}, pokemonId={}",
                    characterId, pokemonId);
            return;
        }
        updateOnlinePartyPokemon(characterManager, updatedPokemon);
        session.send(new SendUpdatePokemonDataPacket(new UpdatePokemonData.Builder()
                .setUpdatePokemon(updatedPokemon)
                .setIsReloadPokemonItem(true)
                .build()));

        InventoryRecord inventory = gameServerService.getInventory();
        if (inventory != null) {
            session.send(new SendInventoryPacket(
                    inventory,
                    gameServerService.getItemsByContainerAndCharacter(characterId, inventory)));
        }
    }

    private PokemonData findOwnedPokemon(CharacterManager characterManager, long characterId) {
        PokemonContainerType requestedContainer = containerId == PokemonContainerType.PARTY.getType()
                ? PokemonContainerType.PARTY : PokemonContainerType.PC;
        ContainerRecord container = gameServerService.getContainerByType(requestedContainer);
        if (container == null || container.getId() == null) {
            return null;
        }
        return gameServerService.getCharacterContainerPokemons(characterId, container)
                .stream()
                .filter(pokemon -> pokemon.getPokemonId() == pokemonId)
                .findFirst()
                .orElse(null);
    }

    private void updateOnlinePartyPokemon(CharacterManager characterManager, PokemonData pokemon) {
        PokemonData[] partyPokemons = characterManager.getPartyPokemons();
        for (int index = 0; index < partyPokemons.length; index++) {
            if (partyPokemons[index] != null
                    && partyPokemons[index].getPokemonId() == pokemonId) {
                partyPokemons[index] = pokemon;
                return;
            }
        }
    }
}
