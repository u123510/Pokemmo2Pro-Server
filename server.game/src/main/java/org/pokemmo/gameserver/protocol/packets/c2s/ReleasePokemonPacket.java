package org.pokemmo.gameserver.protocol.packets.c2s;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.pokemmo.gameserver.game.character.CharacterManager;
import org.pokemmo.gameserver.game.container.PokemonContainerType;
import org.pokemmo.gameserver.game.trade.TradeManager;
import org.pokemmo.gameserver.protocol.GameProtocol;
import org.pokemmo.gameserver.protocol.packets.s2c.SendRemovePokemonPacket;
import org.pokemmo.gameserver.services.GameServerService;
import org.server.IncomingPacket;
import org.server.Session;
import org.server.bytes.ByteBufEx;

/** Handles the PC release request (C2S 0x0C). */
@Slf4j
public final class ReleasePokemonPacket extends IncomingPacket {
    private static final int PAYLOAD_SIZE = Long.BYTES;

    private long pokemonId;

    @Inject
    private GameServerService gameServerService;

    @Override
    public void decode(ByteBufEx buffer) {
        if (buffer.readableBytes() != PAYLOAD_SIZE) {
            throw new IllegalArgumentException(
                    "精灵放生封包长度错误: expected=" + PAYLOAD_SIZE
                            + ", actual=" + buffer.readableBytes());
        }

        pokemonId = buffer.readLongLE();
        if (pokemonId <= 0) {
            throw new IllegalArgumentException("精灵 Object ID 必须为正数: " + pokemonId);
        }
    }

    @Override
    public void handle(Session session) {
        CharacterManager characterManager =
                session.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get();
        if (characterManager == null || characterManager.getCharacterData() == null
                || characterManager.getCharacterData().getPlayerEntity() == null) {
            log.warn("忽略没有角色上下文的精灵放生请求: pokemonId={}", pokemonId);
            return;
        }
        if (TradeManager.isInTrade(characterManager)
                || TradeManager.isPokemonOffered(characterManager, pokemonId)) {
            log.warn("拒绝交易期间或已报价精灵的放生请求: pokemonId={}", pokemonId);
            return;
        }

        long characterId = characterManager.getCharacterData()
                .getPlayerEntity().getEntityGameId();
        if (!gameServerService.releasePokemonFromPc(characterId, pokemonId)) {
            log.warn("精灵放生未应用，目标必须是角色所属的 PC 精灵: characterId={}, pokemonId={}",
                    characterId, pokemonId);
            return;
        }

        session.send(new SendRemovePokemonPacket(PokemonContainerType.PC, pokemonId));
        log.info("精灵放生成功: characterId={}, pokemonId={}", characterId, pokemonId);
    }
}
