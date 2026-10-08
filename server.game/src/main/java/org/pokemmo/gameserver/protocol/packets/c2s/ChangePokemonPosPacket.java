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
import org.pokemmo.gameserver.protocol.packets.s2c.SendPcStatePacket;
import org.pokemmo.gameserver.protocol.packets.s2c.SendPokemonContainerPacket;
import org.pokemmo.gameserver.protocol.packets.s2c.SendUpdatePokemonDataPacket;
import org.pokemmo.gameserver.services.GameServerService;
import org.server.IncomingPacket;
import org.server.Packet;
import org.server.Session;
import org.server.bytes.ByteBufEx;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Slf4j
public class ChangePokemonPosPacket extends IncomingPacket {
    private static final int MAX_CHANGE_AMOUNT = 127;
    private static final int PC_BOX_EXTENSION_SIZE = 60;

    private final List<GameServerService.PokemonPositionChange> changes = new ArrayList<>();

    @Inject
    private GameServerService gameServerService;

    @Override
    public void decode(ByteBufEx buffer) {
        changes.clear();
        int changeAmount = buffer.readUnsignedByte();
        if (changeAmount < 1 || changeAmount > MAX_CHANGE_AMOUNT) {
            throw new IllegalArgumentException("Pokemon position change amount is out of range: " + changeAmount);
        }
        if (buffer.readableBytes() < changeAmount * 6) {
            throw new IllegalArgumentException("Pokemon position change packet is truncated");
        }

        for (int i = 0; i < changeAmount; i++) {
            changes.add(new GameServerService.PokemonPositionChange(
                    buffer.readUnsignedByte(),
                    buffer.readShortLE(),
                    buffer.readUnsignedByte(),
                    buffer.readShortLE()
            ));
        }
    }

    @Override
    public void handle(Session session) throws Exception {
        CharacterManager characterManager = session.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get();
        if (characterManager == null || characterManager.getCharacterData() == null
                || characterManager.getCharacterData().getPlayerEntity() == null) {
            log.warn("忽略没有角色上下文的宝可梦换位封包");
            return;
        }
        if (containsTradeChange()) {
            handleTradeChanges(characterManager);
            return;
        }
        if (TradeManager.isInTrade(characterManager)) {
            log.warn("拒绝交易期间的普通宝可梦换位请求: {}", changes);
            return;
        }
        if (!validateChanges(characterManager)) {
            return;
        }

        long characterId = characterManager.getCharacterData().getPlayerEntity().getEntityGameId();
        Optional<List<PokemonData>> result = gameServerService.changePokemonPositions(characterId, changes);
        if (result.isEmpty()) {
            log.warn("角色 {} 的宝可梦换位请求未能应用: {}", characterId, changes);
            if (affectsContainer(PokemonContainerType.PARTY)) {
                reloadPartyPokemons(characterManager, characterId);
            }
            recoverClientContainers(session, characterId);
            return;
        }

        if (affectsContainer(PokemonContainerType.PARTY)
                && !reloadPartyPokemons(characterManager, characterId)) {
            return;
        }

        SendUpdatePokemonDataPacket[] packets = result.get().stream()
                .map(pokemon -> new SendUpdatePokemonDataPacket(new UpdatePokemonData.Builder()
                        .setUpdatePokemon(pokemon)
                        .setIsReloadPokemonPos(true)
                        .build()))
                .toArray(SendUpdatePokemonDataPacket[]::new);
        if (packets.length > 0) {
            session.send(packets);
        }
    }

    private boolean containsTradeChange() {
        return changes.stream().anyMatch(this::isTradeChange);
    }

    private void handleTradeChanges(CharacterManager characterManager) {
        if (changes.stream().anyMatch(change -> !isTradeChange(change))) {
            log.warn("拒绝混合普通容器与交易容器的宝可梦换位请求: {}", changes);
            return;
        }
        int pcCapacity = PokemonContainerType.PC.getSize()
                + Math.max(0, characterManager.getCharacterData().getPcBoxExpansionNumber())
                * PC_BOX_EXTENSION_SIZE;
        Set<String> sourceSlots = new HashSet<>();
        Set<String> targetSlots = new HashSet<>();
        for (GameServerService.PokemonPositionChange change : changes) {
            if (!isValidTradeChange(change, pcCapacity)
                    || !sourceSlots.add(slotKey(change.oldContainerId(), change.oldPosition()))
                    || !targetSlots.add(slotKey(change.newContainerId(), change.newPosition()))) {
                log.warn("忽略非法交易宝可梦换位封包: {}", change);
                return;
            }
        }
        if (!TradeManager.setPokemonPositionChanges(characterManager, changes, gameServerService)) {
            log.warn("忽略非法交易宝可梦换位封包: {}", changes);
        }
    }

    private String slotKey(int containerId, short position) {
        return containerId + ":" + position;
    }

    private boolean isTradeChange(GameServerService.PokemonPositionChange change) {
        int tradeId = PokemonContainerType.TRADE.getType();
        return (change.oldContainerId() == tradeId && isPersistentContainer(change.newContainerId()))
                || (change.newContainerId() == tradeId && isPersistentContainer(change.oldContainerId()));
    }

    private boolean isValidTradeChange(GameServerService.PokemonPositionChange change, int pcCapacity) {
        int tradeId = PokemonContainerType.TRADE.getType();
        if (change.newContainerId() == tradeId) {
            return isValidSlot(change.oldContainerId(), change.oldPosition(), pcCapacity)
                    && change.newPosition() < PokemonContainerType.TRADE.getSize();
        }
        return change.oldContainerId() == tradeId
                && change.oldPosition() < PokemonContainerType.TRADE.getSize()
                && isValidSlot(change.newContainerId(), change.newPosition(), pcCapacity);
    }

    private boolean isPersistentContainer(int containerId) {
        return containerId == PokemonContainerType.PC.getType()
                || containerId == PokemonContainerType.PARTY.getType();
    }

    private boolean validateChanges(CharacterManager characterManager) {
        int pcCapacity = PokemonContainerType.PC.getSize()
                + Math.max(0, characterManager.getCharacterData().getPcBoxExpansionNumber())
                * PC_BOX_EXTENSION_SIZE;
        for (GameServerService.PokemonPositionChange change : changes) {
            if (!isValidSlot(change.oldContainerId(), change.oldPosition(), pcCapacity)
                    || !isValidSlot(change.newContainerId(), change.newPosition(), pcCapacity)) {
                log.warn("忽略包含非法槽位的宝可梦换位封包: {}", change);
                return false;
            }
            if (TradeManager.isPokemonPositionOffered(characterManager,
                    change.oldContainerId(), change.oldPosition(), gameServerService)
                    || TradeManager.isPokemonPositionOffered(characterManager,
                    change.newContainerId(), change.newPosition(), gameServerService)) {
                log.warn("拒绝移动已在交易报价中的宝可梦: {}", change);
                return false;
            }
            if (change.oldContainerId() == change.newContainerId()
                    && change.oldPosition() == change.newPosition()) {
                log.warn("忽略源槽位与目标槽位相同的宝可梦换位封包: {}", change);
                return false;
            }
        }
        return true;
    }

    private boolean isValidSlot(int containerId, short position, int pcCapacity) {
        if (position < 0) {
            return false;
        }
        if (containerId == PokemonContainerType.PARTY.getType()) {
            return position < PokemonContainerType.PARTY.getSize();
        }
        if (containerId == PokemonContainerType.PC.getType()) {
            return position < pcCapacity;
        }
        return false;
    }

    private boolean reloadPartyPokemons(CharacterManager characterManager, long characterId) {
        ContainerRecord partyContainer = gameServerService.getContainerByType(PokemonContainerType.PARTY);
        if (partyContainer == null) {
            log.error("角色 {} 宝可梦换位处理后无法读取 PARTY 容器", characterId);
            return false;
        }

        PokemonData[] partyPokemons = characterManager.getPartyPokemons();
        PokemonData[] reloadedParty = new PokemonData[partyPokemons.length];
        for (PokemonData pokemon : gameServerService.getCharacterContainerPokemons(characterId, partyContainer)) {
            short position = pokemon.getContainerPosition();
            if (position < 0 || position >= reloadedParty.length || reloadedParty[position] != null) {
                log.error("角色 {} 的 PARTY 槽位数据非法: pokemonId={}, position={}",
                        characterId, pokemon.getPokemonId(), position);
                return false;
            }
            reloadedParty[position] = pokemon;
        }
        System.arraycopy(reloadedParty, 0, partyPokemons, 0, partyPokemons.length);
        return true;
    }

    private void recoverClientContainers(Session session, long characterId) {
        if (!affectsContainer(PokemonContainerType.PC)) {
            SendPokemonContainerPacket partyRefresh = createContainerRefreshPacket(
                    characterId, PokemonContainerType.PARTY);
            if (partyRefresh != null) {
                session.send(partyRefresh);
            }
            return;
        }

        SendPokemonContainerPacket partyRefresh = null;
        if (affectsContainer(PokemonContainerType.PARTY)) {
            partyRefresh = createContainerRefreshPacket(characterId, PokemonContainerType.PARTY);
        }
        SendPokemonContainerPacket pcRefresh = createContainerRefreshPacket(
                characterId, PokemonContainerType.PC);
        if (pcRefresh == null
                || (affectsContainer(PokemonContainerType.PARTY) && partyRefresh == null)) {
            session.send(new SendPcStatePacket(false));
            return;
        }

        List<Packet> packets = new ArrayList<>();
        packets.add(new SendPcStatePacket(false));
        if (partyRefresh != null) {
            packets.add(partyRefresh);
        }
        packets.add(pcRefresh);
        packets.add(new SendPcStatePacket(true));
        session.send(packets.toArray(Packet[]::new));
    }

    private boolean affectsContainer(PokemonContainerType containerType) {
        int containerId = containerType.getType();
        return changes.stream().anyMatch(change ->
                change.oldContainerId() == containerId || change.newContainerId() == containerId);
    }

    private SendPokemonContainerPacket createContainerRefreshPacket(
            long characterId, PokemonContainerType containerType) {
        try {
            ContainerRecord container = gameServerService.getContainerByType(containerType);
            if (container == null) {
                log.error("角色 {} 宝可梦换位处理后无法刷新 {} 容器", characterId, containerType);
                return null;
            }
            List<PokemonData> pokemons = gameServerService
                    .getCharacterContainerPokemons(characterId, container);
            return new SendPokemonContainerPacket(container, pokemons);
        } catch (RuntimeException exception) {
            log.error("角色 {} 宝可梦换位处理后刷新 {} 容器失败",
                    characterId, containerType, exception);
            return null;
        }
    }
}
