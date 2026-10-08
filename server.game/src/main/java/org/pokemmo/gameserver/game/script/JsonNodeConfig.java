package org.pokemmo.gameserver.game.script;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;
@Getter @Setter
public class JsonNodeConfig {
    private String interactionType;
    private Map<String, Object> parameters;
}
