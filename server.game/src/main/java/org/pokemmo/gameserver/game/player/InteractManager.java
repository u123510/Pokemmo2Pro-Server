package org.pokemmo.gameserver.game.player;

import lombok.Getter;
import lombok.Setter;
import org.pokemmo.gameserver.game.interact.InteractType;
import org.pokemmo.gameserver.game.script.InteractScript;
import org.pokemmo.gameserver.game.script.Script;

/** Interaction bookkeeping shared by native menus; story execution belongs to game.story. */
@Getter
@Setter
public class InteractManager {
    private volatile InteractType interactType = InteractType.NONE;
    private long lastInteractorEntityId;
    private byte interactTimes;
    private InteractScript currentInteractScript;
    // Compatibility for request/menu cleanup callers. No legacy graph is loaded or executed.
    private Script currentScript;
    private volatile boolean mailWidgetOpen;

    public void clearLastInteractorEntityId() { lastInteractorEntityId = 0; }
    public void addInteractTimes() { interactTimes++; }
}
