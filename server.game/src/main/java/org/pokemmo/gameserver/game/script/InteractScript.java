package org.pokemmo.gameserver.game.script;

import lombok.Getter;
import org.pokemmo.gameserver.game.interact.GameInteractionType;

import java.util.ArrayList;
@Getter
public class InteractScript extends GameScript {
    private String interactor;
    private GameInteractionType gameInteractionType;
    private int stringOffset;
    private int interactDelay;
    private byte localStringFormatSize;
    private ArrayList<LocalFormatStringScript> localFormatStringScriptList = new ArrayList<LocalFormatStringScript>(0);
    private String interactPlayerName;
    private byte multiChoiceCategory;
    private byte multiChoiceMenuId;
    private byte multiChoiceFlags;

    public InteractScript(String interactor, GameInteractionType gameInteractionType, int stringOffset, int interactDelay,int localStringFormatSize) {
        super(ScriptActionType.ENTITY_INTERACTION);
        this.interactor = interactor;
        this.gameInteractionType = gameInteractionType;
        this.stringOffset = stringOffset;
        this.interactDelay = interactDelay;
        this.localStringFormatSize = (byte) localStringFormatSize;
    }
    public InteractScript(String interactor, GameInteractionType gameInteractionType, int stringOffset, int interactDelay, int localStringFormatSize,ArrayList<LocalFormatStringScript> localFormatStringScriptList) {
        super(ScriptActionType.ENTITY_INTERACTION);
        this.interactor = interactor;
        this.gameInteractionType = gameInteractionType;
        this.stringOffset = stringOffset;
        this.interactDelay = interactDelay;
        this.localStringFormatSize = (byte) localStringFormatSize;
        this.localFormatStringScriptList = localFormatStringScriptList;
    }

    public InteractScript(String interactor, GameInteractionType gameInteractionType,
                          int stringOffset, int interactDelay,
                          ArrayList<LocalFormatStringScript> localFormatStringScriptList,
                          byte multiChoiceCategory, byte multiChoiceMenuId,
                          byte multiChoiceFlags) {
        this(interactor, gameInteractionType, stringOffset, interactDelay,
                localFormatStringScriptList == null ? 0 : localFormatStringScriptList.size(),
                localStringFormatScriptListOrEmpty(localFormatStringScriptList));
        this.multiChoiceCategory = multiChoiceCategory;
        this.multiChoiceMenuId = multiChoiceMenuId;
        this.multiChoiceFlags = multiChoiceFlags;
    }

    private static ArrayList<LocalFormatStringScript> localStringFormatScriptListOrEmpty(
            ArrayList<LocalFormatStringScript> scripts) {
        return scripts == null ? new ArrayList<>(0) : scripts;
    }

    public void setInteractPlayerName(String interactPlayerName) {
        this.interactPlayerName = interactPlayerName;
    }

    public byte getMultiChoiceCategory() {
        return multiChoiceCategory;
    }

    public byte getMultiChoiceMenuId() {
        return multiChoiceMenuId;
    }

    public byte getMultiChoiceFlags() {
        return multiChoiceFlags;
    }
}
