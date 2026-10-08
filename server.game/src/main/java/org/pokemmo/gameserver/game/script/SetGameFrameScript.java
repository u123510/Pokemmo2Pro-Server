package org.pokemmo.gameserver.game.script;

import lombok.Getter;
import org.pokemmo.gameserver.game.frame.FrameType;
@Getter
public class SetGameFrameScript extends GameScript {
    private FrameType frameType;
    private short[] data;
    public SetGameFrameScript(FrameType frameType, short[] data) {
        super(ScriptActionType.SET_GAME_FRAME);
        this.frameType = frameType;
        this.data = data;
    }
}
