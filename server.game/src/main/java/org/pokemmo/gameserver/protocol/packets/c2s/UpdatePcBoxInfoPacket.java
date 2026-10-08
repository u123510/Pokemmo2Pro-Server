package org.pokemmo.gameserver.protocol.packets.c2s;

import lombok.extern.slf4j.Slf4j;
import org.pokemmo.gameserver.game.character.CharacterManager;
import org.pokemmo.gameserver.game.container.PokemonContainerType;
import org.pokemmo.gameserver.protocol.GameProtocol;
import org.server.IncomingPacket;
import org.server.Session;
import org.server.bytes.ByteBufEx;

import java.util.Arrays;

@Slf4j
public class UpdatePcBoxInfoPacket extends IncomingPacket {
    private static final int UPDATE_NAME_FLAG = 1;
    private static final int UPDATE_ORDER_FLAG = 2;
    private static final int KNOWN_FLAGS = UPDATE_NAME_FLAG | UPDATE_ORDER_FLAG;
    private static final int POKEMON_PER_BOX = 60;
    private static final int MAX_BOX_NAME_LENGTH = 20;
    private static final int MAX_ENCODED_BOX_AMOUNT = Byte.MAX_VALUE;

    private int flags;
    private int boxIndex;
    private String boxName;
    private byte[] boxOrder = new byte[0];

    @Override
    public void decode(ByteBufEx buffer) {
        flags = buffer.readUnsignedByte();
        if ((flags & UPDATE_NAME_FLAG) != 0) {
            boxIndex = buffer.readUnsignedByte();
            boxName = buffer.readUtf16LE();
        }
        if ((flags & UPDATE_ORDER_FLAG) != 0) {
            int boxAmount = buffer.readUnsignedByte();
            if (buffer.readableBytes() < boxAmount) {
                throw new IllegalArgumentException("PC box order packet is truncated");
            }
            boxOrder = buffer.readByteArray(boxAmount);
        }
    }

    @Override
    public void handle(Session session) {
        CharacterManager characterManager = session.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get();
        if (characterManager == null || characterManager.getCharacterData() == null
                || characterManager.getCharacterData().getPlayerEntity() == null) {
            log.warn("忽略没有角色上下文的 PC 箱子信息封包");
            return;
        }
        if (flags == 0 || (flags & ~KNOWN_FLAGS) != 0) {
            log.warn("忽略未知的 PC 箱子信息标志: {}", flags);
            return;
        }

        long characterId = characterManager.getCharacterData().getPlayerEntity().getEntityGameId();
        int availableBoxAmount = getAvailableBoxAmount(
                characterManager.getCharacterData().getPcBoxExpansionNumber());
        if ((flags & UPDATE_NAME_FLAG) != 0
                && (boxIndex >= availableBoxAmount || boxName.length() > MAX_BOX_NAME_LENGTH)) {
            log.warn("角色 {} 提交了非法的 PC 箱子名称更新: boxIndex={}, nameLength={}",
                    characterId, boxIndex, boxName.length());
            return;
        }
        if ((flags & UPDATE_ORDER_FLAG) != 0 && !isValidBoxOrder(availableBoxAmount)) {
            log.warn("角色 {} 提交了非法的 PC 箱子顺序: {}", characterId, Arrays.toString(boxOrder));
            return;
        }

        // The current schema has no PC box preference columns. The client has
        // already applied this UI-only change, so no response packet is needed.
        log.debug("角色 {} 更新 PC 箱子信息: flags={}, boxIndex={}, boxOrder={}",
                characterId, flags, boxIndex, Arrays.toString(boxOrder));
    }

    private boolean isValidBoxOrder(int availableBoxAmount) {
        if (boxOrder.length != availableBoxAmount) {
            return false;
        }
        boolean[] seen = new boolean[availableBoxAmount];
        for (byte encodedIndex : boxOrder) {
            int index = Byte.toUnsignedInt(encodedIndex);
            if (index >= availableBoxAmount || seen[index]) {
                return false;
            }
            seen[index] = true;
        }
        return true;
    }

    private int getAvailableBoxAmount(short pcBoxExpansionNumber) {
        int baseBoxAmount = PokemonContainerType.PC.getSize() / POKEMON_PER_BOX;
        return Math.min(MAX_ENCODED_BOX_AMOUNT,
                baseBoxAmount + Math.max(0, pcBoxExpansionNumber));
    }
}
