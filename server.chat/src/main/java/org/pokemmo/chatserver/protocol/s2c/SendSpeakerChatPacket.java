package org.pokemmo.chatserver.protocol.s2c;

import lombok.RequiredArgsConstructor;
import org.server.OutgoingPacket;
import org.server.bytes.ByteBufEx;
import org.server.union.chat.ChatType;
import org.server.union.language.LanguageType;
@RequiredArgsConstructor
public class SendSpeakerChatPacket extends OutgoingPacket {
    private final ChatType chatType;
    private final long speakerId;
    private final String speakerName;
    private final LanguageType languageType;
    private final byte speakerPermission;
    private final String speakText;
    @Override
    public void encode(ByteBufEx buffer) {
        buffer.writeByte(chatType.getType());
        if(languageType != LanguageType.GREEK){
            buffer.writeLongLE(speakerId);
            buffer.writeUtf16LE(speakerName);
            buffer.writeByte(languageType.getType());
            buffer.writeByte(speakerPermission);
        }
        buffer.writeUtf16LE(speakText);
    }
}
