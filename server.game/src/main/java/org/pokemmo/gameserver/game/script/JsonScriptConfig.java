package org.pokemmo.gameserver.game.script;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
@Getter @Setter
public class JsonScriptConfig {
    private String name;
    private List<JsonNodeConfig> nodes;
    private List<String> nextScripts;
}
