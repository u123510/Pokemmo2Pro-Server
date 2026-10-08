package org.pokemmo.gameserver.protocol.packets.c2s;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.pokemmo.db.jooq.tables.records.ContainerRecord;
import org.pokemmo.gameserver.game.character.CharacterManager;
import org.pokemmo.gameserver.game.container.PokemonContainerType;
import org.pokemmo.gameserver.game.pokemon.PokemonData;
import org.pokemmo.gameserver.game.pokemon.UpdatePokemonData;
import org.pokemmo.gameserver.game.trade.TradeManager;
import org.pokemmo.gameserver.protocol.GameProtocol;
import org.pokemmo.gameserver.protocol.packets.s2c.SendUpdatePokemonDataPacket;
import org.pokemmo.gameserver.services.GameServerService;
import org.server.IncomingPacket;
import org.server.Session;
import org.server.bytes.ByteBufEx;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** Handles the client PC/party sort request (C2S 0x1B). */
@Slf4j
public final class SortPokemonPacket extends IncomingPacket {
    private static final int MAX_CONTAINER_AMOUNT = 127;
    private static final int PC_BOX_SIZE = 60;

    private int containerType;
    private int[] containerIndexes = new int[0];
    private int sortFlags;

    @Inject
    private GameServerService gameServerService;

    @Override
    public void decode(ByteBufEx buffer) {
        containerType = buffer.readUnsignedByte();
        int containerAmount = buffer.readUnsignedByte();
        if (containerAmount < 1 || containerAmount > MAX_CONTAINER_AMOUNT) {
            throw new IllegalArgumentException("宝可梦排序容器数量超出范围: " + containerAmount);
        }

        int requiredBytes = containerAmount * Integer.BYTES + Short.BYTES;
        if (buffer.readableBytes() < requiredBytes) {
            throw new IllegalArgumentException("宝可梦排序封包长度不足: actual="
                    + buffer.readableBytes() + ", required=" + requiredBytes);
        }

        containerIndexes = new int[containerAmount];
        for (int index = 0; index < containerAmount; index++) {
            containerIndexes[index] = buffer.readIntLE();
        }
        sortFlags = buffer.readUnsignedShortLE();
        if (buffer.isReadable()) {
            throw new IllegalArgumentException("宝可梦排序封包包含未消费的尾部数据");
        }
    }

    @Override
    public void handle(Session session) {
        CharacterManager characterManager =
                session.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get();
        if (characterManager == null || characterManager.getCharacterData() == null
                || characterManager.getCharacterData().getPlayerEntity() == null) {
            log.warn("忽略没有角色上下文的宝可梦排序请求");
            return;
        }
        if (TradeManager.isInTrade(characterManager)) {
            log.warn("拒绝交易期间的宝可梦排序请求: containerType={}, indexes={}",
                    containerType, Arrays.toString(containerIndexes));
            return;
        }
        if (!isSupportedContainer()) {
            log.warn("拒绝未知容器的宝可梦排序请求: containerType={}", containerType);
            return;
        }

        long characterId = characterManager.getCharacterData().getPlayerEntity().getEntityGameId();
        if (containsOfferedPokemon(characterManager, characterId)) {
            log.warn("拒绝包含交易报价宝可梦的排序请求: characterId={}, indexes={}",
                    characterId, Arrays.toString(containerIndexes));
            return;
        }

        Optional<List<PokemonData>> result = gameServerService.sortPokemon(
                characterId, containerType, containerIndexes, sortFlags);
        if (result.isEmpty()) {
            log.warn("角色 {} 的宝可梦排序请求未能应用: containerType={}, indexes={}, flags=0x{}",
                    characterId, containerType, Arrays.toString(containerIndexes),
                    Integer.toHexString(sortFlags));
            return;
        }

        if (containerType == PokemonContainerType.PARTY.getType()) {
            if (!reloadOnlineParty(characterManager, characterId)) {
                log.warn("角色 {} 的队伍排序完成但在线 PARTY 重载失败", characterId);
                return;
            }
        }
        List<PokemonData> changedPokemons = result.get();
        SendUpdatePokemonDataPacket[] packets = changedPokemons.stream()
                .map(pokemon -> new SendUpdatePokemonDataPacket(new UpdatePokemonData.Builder()
                        .setUpdatePokemon(pokemon)
                        .setIsReloadPokemonPos(true)
                        .build()))
                .toArray(SendUpdatePokemonDataPacket[]::new);
        if (packets.length > 0) {
            session.send(packets);
        }
        log.debug("角色 {} 的宝可梦排序完成: containerType={}, indexes={}, flags=0x{}, changed={}",
                characterId, containerType, Arrays.toString(containerIndexes),
                Integer.toHexString(sortFlags), packets.length);
    }

    private boolean isSupportedContainer() {
        return containerType == PokemonContainerType.PC.getType()
                || containerType == PokemonContainerType.PARTY.getType();
    }

    private boolean containsOfferedPokemon(CharacterManager characterManager, long characterId) {
        try {
            PokemonContainerType type = containerType == PokemonContainerType.PC.getType()
                    ? PokemonContainerType.PC : PokemonContainerType.PARTY;
            ContainerRecord container = gameServerService.getContainerByType(type);
            if (container == null) {
                return true;
            }
            int slotSize = type == PokemonContainerType.PC ? PC_BOX_SIZE : type.getSize();
            List<PokemonData> pokemons = gameServerService
                    .getCharacterContainerPokemons(characterId, container);
            for (int containerIndex : containerIndexes) {
                int start = containerIndex * slotSize;
                int end = start + slotSize;
                for (PokemonData pokemon : pokemons) {
                    if (pokemon.getContainerPosition() >= start
                            && pokemon.getContainerPosition() < end
                            && TradeManager.isPokemonOffered(characterManager, pokemon.getPokemonId())) {
                        return true;
                    }
                }
            }
            return false;
        } catch (RuntimeException exception) {
            log.error("检查排序目标中的交易报价宝可梦失败: characterId={}", characterId, exception);
            return true;
        }
    }

    private boolean reloadOnlineParty(CharacterManager characterManager, long characterId) {
        ContainerRecord partyContainer = gameServerService
                .getContainerByType(PokemonContainerType.PARTY);
        if (partyContainer == null) {
            return false;
        }
        PokemonData[] party = characterManager.getPartyPokemons();
        PokemonData[] reloadedParty = new PokemonData[party.length];
        for (PokemonData pokemon : gameServerService
                .getCharacterContainerPokemons(characterId, partyContainer)) {
            int position = pokemon.getContainerPosition();
            if (position < 0 || position >= reloadedParty.length
                    || reloadedParty[position] != null) {
                return false;
            }
            reloadedParty[position] = pokemon;
        }
        System.arraycopy(reloadedParty, 0, party, 0, party.length);
        return true;
    }
}
