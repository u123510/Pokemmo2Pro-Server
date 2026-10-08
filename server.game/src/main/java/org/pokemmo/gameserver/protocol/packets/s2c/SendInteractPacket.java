package org.pokemmo.gameserver.protocol.packets.s2c;

import org.server.OutgoingPacket;
import org.server.bytes.ByteBufEx;
import org.pokemmo.gameserver.game.interact.GameInteractionType;
import org.pokemmo.gameserver.game.script.InteractScript;
import org.pokemmo.gameserver.game.script.LocalFormatStringScript;

public class SendInteractPacket extends OutgoingPacket {
    private long entityGameId;
    private byte interactTimes;
    private InteractScript interactScript;
    @Override
    public void encode(ByteBufEx buffer) throws Exception {
        buffer.writeByte(interactTimes);
        buffer.writeByte(interactScript.getGameInteractionType().getProtocolType());
        buffer.writeIntLE(interactScript.getStringOffset());
        buffer.writeLongLE(entityGameId);
        buffer.writeIntLE(interactScript.getInteractDelay());
        int localFormatCount = interactScript.getLocalStringFormatSize();
        if (localFormatCount != interactScript.getLocalFormatStringScriptList().size()) {
            throw new IllegalStateException("交互脚本本地字符串格式数量不一致");
        }
        buffer.writeByte(localFormatCount);
        for (int i = 0; i < localFormatCount; i++) {
            writeLocalFormat(buffer, interactScript.getLocalFormatStringScriptList().get(i));
        }

        if (interactScript.getGameInteractionType() == GameInteractionType.MULTICHOICE) {
            // The client resolves MULTICHOICE entries from its built-in menu table.
            buffer.writeByte(interactScript.getMultiChoiceCategory());
            buffer.writeByte(interactScript.getMultiChoiceMenuId());
            buffer.writeByte(interactScript.getMultiChoiceFlags());
            return;
        }
        GameInteractionType interactionType = interactScript.getGameInteractionType();
        if (interactionType == GameInteractionType.REQUEST_BATTLE) {
            buffer.writeUtf16LE(interactScript.getInteractPlayerName() == null
                    ? ""
                    : interactScript.getInteractPlayerName());
            // The client uses a dedicated request parser for battle prompts and
            // always reads these fields after the requester name, even when no
            // optional battle rules are enabled.
            buffer.writeByte(0); // Cq: default battle type (single/default)
            buffer.writeByte(0); // Jh0: default N2/rule setting
            buffer.writeByte(0); // my0: default turn/setting value
            buffer.writeByte(0); // flags: no optional fields
            buffer.writeByte(0); // yW: default auxiliary value
            buffer.writeByte(0); // Qo0: default auxiliary value
            return;
        }
        if (interactionType == GameInteractionType.REQUEST_TRADE
                || interactionType == GameInteractionType.REQUEST_FRIEND
                || interactionType == GameInteractionType.REQUEST_LINK) {
            buffer.writeUtf16LE(interactScript.getInteractPlayerName() == null
                    ? ""
                    : interactScript.getInteractPlayerName());
        }
    }

    private static void writeLocalFormat(ByteBufEx buffer, LocalFormatStringScript format) {
        byte stringType = format.getStringType();
        buffer.writeByte(format.getReplaceIndex());
        buffer.writeByte(stringType);
        if ((stringType & 128) != 0) {
            buffer.writeByte(format.getRegionIndexId());
        } else if (stringType == 30) {
            buffer.writeLongLE(format.getLocalStringId());
        } else if (stringType == 9 || stringType == 10 || stringType == 17) {
            buffer.writeIntLE(format.getIntStringId());
        } else if (stringType == 5 || stringType == 18) {
            buffer.writeUtf16LE(format.getExportString() == null ? "" : format.getExportString());
        } else {
            if (format.getFormatIndexIdList() == null
                    || format.getFormatIndexIdList().size() > 255) {
                throw new IllegalStateException("交互脚本格式索引数量非法");
            }
            buffer.writeByte(format.getFormatIndexIdList().size());
            for (short index : format.getFormatIndexIdList()) {
                buffer.writeShortLE(index);
            }
        }
    }

    public SendInteractPacket(long entityGameId, byte interactTimes, InteractScript interactScript) {
        this.entityGameId = entityGameId;
        this.interactTimes = interactTimes;
        this.interactScript = interactScript;
    }
}
