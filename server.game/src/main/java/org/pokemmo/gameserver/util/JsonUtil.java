package org.pokemmo.gameserver.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class JsonUtil {
    @Getter
    private final Logger logger = LoggerFactory.getLogger(JsonUtil.class);
    @Getter
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
}

