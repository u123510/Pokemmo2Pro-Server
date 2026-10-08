package org.pokemmo.gameserver.game.string;

import lombok.Getter;
import lombok.Setter;
import org.server.union.chat.ChatType;

import java.util.List;
@Getter @Setter
public class GameMassageString {
    private int localStringIndexId;
    private List<GameLocalFormatString> localStringList;
    private boolean isBattleString;
    private boolean isGameChatString;
    private ChatType chatType;
}
