package org.pokemmo.gameserver.protocol.packets.c2s;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.pokemmo.db.jooq.tables.records.ContainerRecord;
import org.pokemmo.gameserver.game.character.CharacterManager;
import org.pokemmo.gameserver.game.container.PokemonContainerType;
import org.pokemmo.gameserver.game.pokemon.PokemonData;
import org.pokemmo.gameserver.game.trade.TradeManager;
import org.pokemmo.gameserver.protocol.GameProtocol;
import org.pokemmo.gameserver.protocol.packets.s2c.SendAddPokemonPacket;
import org.pokemmo.gameserver.services.GameServerService;
import org.server.IncomingPacket;
import org.server.Session;
import org.server.bytes.ByteBufEx;

/** Handles the client's Pokemon nickname request (C2S 0x0E). */
@Slf4j
public final class RenamePokemonPacket extends IncomingPacket {
    private static final int MAX_NAME_LENGTH = 16;
    private static final int MIN_PAYLOAD_SIZE = Long.BYTES + Character.BYTES;

    private long pokemonId;
    private String name;

    @Inject
    private GameServerService gameServerService;

    @Override
    public void decode(ByteBufEx buffer) {
        if (buffer.readableBytes() < MIN_PAYLOAD_SIZE) {
            throw new IllegalArgumentException("精灵重命名封包长度不足: actual=" + buffer.readableBytes());
        }

        pokemonId = buffer.readLongLE();
        if (pokemonId <= 0) {
            throw new IllegalArgumentException("精灵 Object ID 必须为正数: " + pokemonId);
        }

        name = readName(buffer);
        if (buffer.isReadable()) {
            throw new IllegalArgumentException("精灵重命名封包包含未消费的尾部数据");
        }
    }

    @Override
    public void handle(Session session) {
        CharacterManager characterManager =
                session.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get();
        if (characterManager == null || characterManager.getCharacterData() == null
                || characterManager.getCharacterData().getPlayerEntity() == null) {
            log.warn("忽略没有角色上下文的精灵重命名请求: pokemonId={}", pokemonId);
            return;
        }
        if (TradeManager.isInTrade(characterManager)
                || TradeManager.isPokemonOffered(characterManager, pokemonId)) {
            log.warn("拒绝交易期间或已报价精灵的重命名请求: pokemonId={}", pokemonId);
            return;
        }

        long characterId = characterManager.getCharacterData().getPlayerEntity().getEntityGameId();
        try {
            PokemonData pokemon = findOwnedPokemon(characterManager, characterId);
            if (pokemon == null) {
                log.warn("未找到角色 {} 所属的精灵，忽略重命名: pokemonId={}", characterId, pokemonId);
                return;
            }
            if (!gameServerService.updatePokemonName(characterId, pokemonId, name)) {
                log.warn("精灵重命名未写入数据库: characterId={}, pokemonId={}, name={}",
                        characterId, pokemonId, name);
                return;
            }

            pokemon.setName(name);
            updateOnlinePartyPokemon(characterManager, pokemon);
            session.send(new SendAddPokemonPacket(pokemon));
            log.info("精灵重命名成功: characterId={}, pokemonId={}, name={}",
                    characterId, pokemonId, name);
        } catch (RuntimeException exception) {
            log.error("处理精灵重命名失败: characterId={}, pokemonId={}, name={}",
                    characterId, pokemonId, name, exception);
        }
    }

    private PokemonData findOwnedPokemon(CharacterManager characterManager, long characterId) {
        for (PokemonData pokemon : characterManager.getPartyPokemons()) {
            if (pokemon != null && pokemon.getPokemonId() == pokemonId
                    && pokemon.getTrainerId() == characterId
                    && pokemon.getContainerId() == PokemonContainerType.PARTY.getType()) {
                return pokemon;
            }
        }

        PokemonData partyPokemon = findInContainer(characterId, PokemonContainerType.PARTY);
        if (partyPokemon != null) {
            return partyPokemon;
        }
        return findInContainer(characterId, PokemonContainerType.PC);
    }

    private PokemonData findInContainer(long characterId, PokemonContainerType containerType) {
        ContainerRecord container = gameServerService.getContainerByType(containerType);
        if (container == null || container.getId() == null) {
            return null;
        }
        return gameServerService.getCharacterContainerPokemons(characterId, container).stream()
                .filter(pokemon -> pokemon.getPokemonId() == pokemonId)
                .findFirst()
                .orElse(null);
    }

    private void updateOnlinePartyPokemon(CharacterManager characterManager, PokemonData pokemon) {
        if (pokemon.getContainerId() != PokemonContainerType.PARTY.getType()) {
            return;
        }
        PokemonData[] partyPokemons = characterManager.getPartyPokemons();
        for (int index = 0; index < partyPokemons.length; index++) {
            if (partyPokemons[index] != null && partyPokemons[index].getPokemonId() == pokemonId) {
                partyPokemons[index] = pokemon;
                return;
            }
        }
    }

    private static String readName(ByteBufEx buffer) {
        StringBuilder value = new StringBuilder(MAX_NAME_LENGTH);
        for (int index = 0; index <= MAX_NAME_LENGTH; index++) {
            if (buffer.readableBytes() < Character.BYTES) {
                throw new IllegalArgumentException("精灵昵称缺少 UTF-16LE 终止符");
            }
            char character = buffer.readCharLE();
            if (character == '\0') {
                return value.toString();
            }
            if (index == MAX_NAME_LENGTH) {
                throw new IllegalArgumentException("精灵昵称长度超过 " + MAX_NAME_LENGTH + " 个字符");
            }
            if (!isAllowedNameCharacter(character)) {
                throw new IllegalArgumentException("精灵昵称包含客户端不允许的字符: U+"
                        + Integer.toHexString(character).toUpperCase());
            }
            value.append(character);
        }
        throw new IllegalArgumentException("精灵昵称缺少 UTF-16LE 终止符");
    }

    private static boolean isAllowedNameCharacter(char character) {
        return character >= 'a' && character <= 'z'
                || character >= 'A' && character <= 'Z'
                || character >= '0' && character <= '9'
                || character == ' ' || character == ',' || character == '.'
                || character == '!' || character == '?' || character == '"'
                || character == '\'' || character == '-' || character == '~'
                || character >= '\u00C0' && character <= '\u00FF'
                && character != '\u00D7' && character != '\u00F7';
    }
}
