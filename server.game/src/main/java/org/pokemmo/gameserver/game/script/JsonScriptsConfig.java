package org.pokemmo.gameserver.game.script;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter @Setter
public class JsonScriptsConfig {
   private List<JsonScriptConfig> scripts;
}
