package org.pokemmo.gameserver.protocol.packets.c2s;

import lombok.extern.slf4j.Slf4j;
import org.pokemmo.gameserver.game.battle.BattleFormatType;
import org.pokemmo.gameserver.game.battle.BattleRequestManager;
import org.pokemmo.gameserver.game.character.CharacterManager;
import org.pokemmo.gameserver.game.map.MapConnectionType;
import org.pokemmo.gameserver.game.map.MapData;
import org.pokemmo.gameserver.protocol.GameProtocol;
import org.pokemmo.gameserver.protocol.packets.s2c.SendInteractPacket;
import org.server.IncomingPacket;
import org.server.Session;
import org.server.bytes.ByteBufEx;

/** C2S 0x25: request a battle with a player visible on the current map. */
@Slf4j
public final class BattleRequestPacket extends IncomingPacket {
    private static final int MAX_NAME_LENGTH = 32;
    private static final int ALLOWED_FLAGS = 0x7F;

    private String targetPlayerName;
    private byte battleFormat;
    private byte auxiliary;
    private byte flags;
    private byte battleSetting;
    private byte n2Setting;
    private byte optionCount;
    private byte[] options = new byte[0];
    private byte tail;

    @Override
    public void decode(ByteBufEx buffer) {
        targetPlayerName = readUtf16LeField(buffer);
        requireReadable(buffer, 3, "单挑请求头");
        battleFormat = buffer.readByte();
        auxiliary = buffer.readByte();
        flags = buffer.readByte();
        int unsignedFlags = Byte.toUnsignedInt(flags);
        if ((unsignedFlags & ~ALLOWED_FLAGS) != 0) {
            throw new IllegalArgumentException("单挑请求包含未知 flags: " + unsignedFlags);
        }
        if ((unsignedFlags & 0x08) != 0) {
            requireReadable(buffer, 1, "单挑 flags[0x08]");
            battleSetting = buffer.readByte();
        }
        if ((unsignedFlags & 0x10) != 0) {
            requireReadable(buffer, 1, "单挑 N2 设置");
            n2Setting = buffer.readByte();
            if (Byte.toUnsignedInt(n2Setting) > 6) {
                throw new IllegalArgumentException("单挑 N2 设置非法: " + Byte.toUnsignedInt(n2Setting));
            }
        }
        if ((unsignedFlags & 0x20) != 0) {
            requireReadable(buffer, 1, "单挑 GV 数量");
            optionCount = buffer.readByte();
            int count = Byte.toUnsignedInt(optionCount);
            if (count > 32) {
                throw new IllegalArgumentException("单挑 GV 数量超过限制: " + count);
            }
            options = new byte[count];
            for (int index = 0; index < count; index++) {
                requireReadable(buffer, 1, "单挑 GV 条目");
                byte option = buffer.readByte();
                options[index] = option;
                int optionId = Byte.toUnsignedInt(option);
                if (optionId > 11) {
                    throw new IllegalArgumentException("单挑 GV 条目非法: " + optionId);
                }
                if (optionId == 7 || optionId == 9 || optionId == 10 || optionId == 11) {
                    requireReadable(buffer, 1, "单挑 GV 参数");
                    buffer.readByte();
                }
            }
        }
        if ((unsignedFlags & 0x40) != 0) {
            requireReadable(buffer, 1, "单挑尾部参数");
            tail = buffer.readByte();
        }
        if (buffer.isReadable()) {
            throw new IllegalArgumentException("单挑请求包含未消费的尾部数据");
        }
        if (targetPlayerName.isBlank()) {
            throw new IllegalArgumentException("单挑目标名称不能为空");
        }
        if (Byte.toUnsignedInt(battleFormat) != BattleFormatType.SINGLE_BATTLE.getType()) {
            throw new IllegalArgumentException("当前仅支持单打格式: " + Byte.toUnsignedInt(battleFormat));
        }
    }

    @Override
    public void handle(Session session) {
        CharacterManager requester = session.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get();
        if (requester == null || requester.getCharacterData() == null
                || requester.getCharacterData().getPlayerEntity() == null) {
            return;
        }
        MapData currentMap = requester.getCurrentMapDatas()[MapConnectionType.NOTHING.getType()];
        if (currentMap == null) {
            log.warn("角色尚未进入地图，忽略单挑请求: target={}", targetPlayerName);
            return;
        }
        CharacterManager target = findTarget(requester, currentMap);
        if (target == null) {
            log.warn("当前地图未找到单挑目标: requester={}, target={}",
                    requester.getCharacterData().getPlayerEntity().getPlayerName(), targetPlayerName);
            return;
        }
        BattleRequestManager.PendingBattleRequest request = BattleRequestManager.registerRequest(
                requester, target, BattleFormatType.SINGLE_BATTLE);
        if (request == null) {
            log.warn("单挑请求被拒绝或目标正忙: target={}", targetPlayerName);
            return;
        }
        target.getCharacterSession().send(new SendInteractPacket(
                -1L, target.getInteractManager().getInteractTimes(), request.script()));
    }

    private CharacterManager findTarget(CharacterManager requester, MapData currentMap) {
        for (Session candidateSession : currentMap.getPlayerSessionPool().values()) {
            if (candidateSession == null || candidateSession == requester.getCharacterSession()
                    || !candidateSession.isActive()) {
                continue;
            }
            CharacterManager candidate = candidateSession.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get();
            if (candidate == null || candidate.getCharacterData() == null
                    || candidate.getCharacterData().getPlayerEntity() == null) {
                continue;
            }
            String candidateName = candidate.getCharacterData().getPlayerEntity().getPlayerName();
            if (candidateName != null && candidateName.equalsIgnoreCase(targetPlayerName)
                    && candidate.getCharacterData().getChannel() == requester.getCharacterData().getChannel()
                    && candidate.getCharacterData().getPlayerEntity().getRegionIndexId()
                    == requester.getCharacterData().getPlayerEntity().getRegionIndexId()
                    && candidate.getCharacterData().getPlayerEntity().getMapHeaderIdOrGbaMapGroupId()
                    == requester.getCharacterData().getPlayerEntity().getMapHeaderIdOrGbaMapGroupId()
                    && candidate.getCharacterData().getPlayerEntity().getGbaMapId()
                    == requester.getCharacterData().getPlayerEntity().getGbaMapId()) {
                return candidate;
            }
        }
        return null;
    }

    private static String readUtf16LeField(ByteBufEx buffer) {
        StringBuilder value = new StringBuilder();
        for (int index = 0; index <= MAX_NAME_LENGTH; index++) {
            requireReadable(buffer, Character.BYTES, "单挑目标名称");
            char character = buffer.readCharLE();
            if (character == '\0') {
                return value.toString();
            }
            if (Character.isISOControl(character)) {
                throw new IllegalArgumentException("单挑目标名称包含控制字符");
            }
            value.append(character);
        }
        throw new IllegalArgumentException("单挑目标名称长度超过 " + MAX_NAME_LENGTH + " 个字符");
    }

    private static void requireReadable(ByteBufEx buffer, int length, String fieldName) {
        if (buffer.readableBytes() < length) {
            throw new IllegalArgumentException(fieldName + "数据截断");
        }
    }
}
