package org.pokemmo.gameserver.protocol.packets.c2s;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.pokemmo.gameserver.game.character.CharacterManager;
import org.pokemmo.gameserver.game.pokemon.PokemonData;
import org.pokemmo.gameserver.game.pokemon.UpdatePokemonData;
import org.pokemmo.gameserver.game.trade.TradeManager;
import org.pokemmo.gameserver.protocol.GameProtocol;
import org.pokemmo.gameserver.protocol.packets.s2c.SendInventoryPacket;
import org.pokemmo.gameserver.protocol.packets.s2c.SendUpdatePokemonDataPacket;
import org.pokemmo.gameserver.services.GameServerService;
import org.pokemmo.gameserver.services.item.ItemUseService;
import org.server.IncomingPacket;
import org.server.Session;
import org.server.bytes.ByteBufEx;

@Slf4j
public class UseItemPacket extends IncomingPacket {
    private static final int PAYLOAD_SIZE = Short.BYTES + Long.BYTES + Short.BYTES + 2;

    private short itemIndexId;
    private long targetPokemonId;
    private short usedItemAmount;
    private byte useItemTargetMovePos;
    private byte unuse;

    @Inject
    private GameServerService gameServerService;

    @Override
    public void decode(ByteBufEx buffer) {
        if (buffer.readableBytes() != PAYLOAD_SIZE) {
            throw new IllegalArgumentException("使用道具封包长度错误: expected=" + PAYLOAD_SIZE
                    + ", actual=" + buffer.readableBytes());
        }
        itemIndexId = buffer.readShortLE();
        targetPokemonId = buffer.readLongLE();
        usedItemAmount = buffer.readShortLE();
        useItemTargetMovePos = buffer.readByte();
        unuse = buffer.readByte();
        if (Short.toUnsignedInt(itemIndexId) <= 0) {
            throw new IllegalArgumentException("道具索引必须为正数: " + Short.toUnsignedInt(itemIndexId));
        }
        if (Short.toUnsignedInt(usedItemAmount) <= 0 || Short.toUnsignedInt(usedItemAmount) > 9999) {
            throw new IllegalArgumentException("使用道具数量必须在 1..9999 范围内: "
                    + Short.toUnsignedInt(usedItemAmount));
        }
        if (useItemTargetMovePos < -1 || useItemTargetMovePos > 3) {
            throw new IllegalArgumentException("非法的技能位置: " + useItemTargetMovePos);
        }
        if (unuse != 0 && unuse != 1) {
            throw new IllegalArgumentException("非法的 unuse 标记: " + Byte.toUnsignedInt(unuse));
        }
        log.trace("使用道具封包: itemIndexId={} (0x{}), usedItemAmount={}, targetPokemonId={} (0x{}), "
                        + "targetMovePosition={} (0x{}), unuse={} (0x{})",
                Short.toUnsignedInt(itemIndexId), String.format("%04X", Short.toUnsignedInt(itemIndexId)),
                Short.toUnsignedInt(usedItemAmount), targetPokemonId, String.format("%016X", targetPokemonId),
                useItemTargetMovePos, String.format("%02X", Byte.toUnsignedInt(useItemTargetMovePos)),
                unuse, String.format("%02X", Byte.toUnsignedInt(unuse)));
    }
    @Override
    public void handle(Session session) throws Exception {
        CharacterManager characterManager = session.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get();
        if (characterManager == null || characterManager.getCharacterData() == null
                || characterManager.getCharacterData().getPlayerEntity() == null) {
            log.warn("忽略没有角色上下文的使用道具封包: itemIndexId={}", Short.toUnsignedInt(itemIndexId));
            return;
        }
        if (TradeManager.isInTrade(characterManager)) {
            log.warn("拒绝交易期间使用道具: characterId={}, itemIndexId={}",
                    characterManager.getCharacterData().getPlayerEntity().getEntityGameId(),
                    Short.toUnsignedInt(itemIndexId));
            return;
        }

        long characterId = characterManager.getCharacterData().getPlayerEntity().getEntityGameId();
        ItemUseService.Result result = gameServerService.useItem(
                characterId,
                itemIndexId,
                targetPokemonId,
                usedItemAmount,
                useItemTargetMovePos,
                unuse);
        if (!result.success()) {
            log.warn("使用道具被拒绝: characterId={}, itemIndexId={}, targetPokemonId={}, reason={}",
                    characterId, Short.toUnsignedInt(itemIndexId), targetPokemonId, result.reason());
            return;
        }

        if (result.transportationChanged()) {
            characterManager.getCharacterData().getPlayerEntity().setTransportation(result.transportation());
            characterManager.broadcastPlayerTransportation();
        }

        PokemonData updatedPokemon = result.pokemon();
        if (updatedPokemon != null) {
            updateOnlinePartyPokemon(characterManager, updatedPokemon);
            session.send(new SendUpdatePokemonDataPacket(new UpdatePokemonData.Builder()
                    .setUpdatePokemon(updatedPokemon)
                    .setIsReloadPokemonLevel(result.reloadLevel())
                    .setIsReloadPokemonMove(result.reloadMove())
                    .setIsReloadPokemonCurrentHp(result.reloadCurrentHp())
                    .setIsReloadPokemonStatus(result.reloadStatus())
                    .setIsReloadPokemonFriendValue(result.reloadFriendValue())
                    .setIsReloadPokemonParticleEffects(result.reloadParticleEffects())
                    .build()));
        }

        var inventory = gameServerService.getInventory();
        if (inventory != null && result.consumedAmount() > 0) {
            session.send(new SendInventoryPacket(
                    inventory,
                    gameServerService.getItemsByContainerAndCharacter(characterId, inventory)));
        }
    }

    private void updateOnlinePartyPokemon(CharacterManager characterManager, PokemonData updatedPokemon) {
        PokemonData[] partyPokemons = characterManager.getPartyPokemons();
        for (int index = 0; index < partyPokemons.length; index++) {
            if (partyPokemons[index] != null
                    && partyPokemons[index].getPokemonId() == updatedPokemon.getPokemonId()) {
                partyPokemons[index] = updatedPokemon;
                return;
            }
        }
    }
}
