package org.pokemmo.gameserver.game.character;

import org.pokemmo.gameserver.game.account.AccountData;
import org.pokemmo.gameserver.script.ScriptManager;
import org.pokemmo.gameserver.services.GameServerService;
import org.pokemmo.gameserver.util.SnowflakeIdGenerator;
import org.server.Session;
import org.server.services.ServerService;

/** Stable character runtime facade while world concerns migrate to focused services. */
public class CharacterManager extends CharacterManagerState {
    public CharacterManager(AccountData accountData, Session characterSession,
                            ScriptManager scriptManager, SnowflakeIdGenerator snowflakeIdGenerator,
                            GameServerService characterService, ServerService serverService) {
        super(accountData, characterSession, scriptManager, snowflakeIdGenerator,
                characterService, serverService);
    }
}
