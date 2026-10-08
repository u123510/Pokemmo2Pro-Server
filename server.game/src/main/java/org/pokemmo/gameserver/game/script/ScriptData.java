package org.pokemmo.gameserver.game.script;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.pokemmo.gameserver.game.region.RegionType;

import java.util.HashMap;
@Getter
@Setter
@AllArgsConstructor
public class ScriptData {
    private RegionType regionType;
    private final HashMap<String, Script> interactScripts = new HashMap<>();
    private final HashMap<String, Script> eventScripts = new HashMap<>();
    public Script getInteractScript(String scriptName) {
        return interactScripts.get(scriptName);
    }
    public Script getEventScript(String scriptName) {
        return eventScripts.get(scriptName);
    }
}
