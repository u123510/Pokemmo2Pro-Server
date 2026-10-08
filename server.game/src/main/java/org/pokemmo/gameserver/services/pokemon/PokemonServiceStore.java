package org.pokemmo.gameserver.services.pokemon;

import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Result;
import org.jooq.impl.DSL;
import org.pokemmo.db.Database;
import org.pokemmo.db.jooq.tables.records.ContainerRecord;
import org.pokemmo.db.jooq.tables.records.OwnedItemRecord;
import org.pokemmo.db.jooq.tables.records.PokemonRecord;
import org.pokemmo.gameserver.game.character.CharacterData;
import org.pokemmo.gameserver.game.container.PokemonContainerType;
import org.pokemmo.gameserver.game.item.ItemData;
import org.pokemmo.gameserver.game.item.ItemManager;
import org.pokemmo.gameserver.game.particleEffectType.ParticleEffectType;
import org.pokemmo.gameserver.game.pokemon.PokemonData;
import org.pokemmo.gameserver.game.pokemon.PokemonNormalRibbonType;
import org.pokemmo.gameserver.game.pokemon.PokemonRibbonMask;
import org.pokemmo.gameserver.services.GameServerService.PokemonPositionChange;
import org.pokemmo.gameserver.services.character.CharacterService;
import org.pokemmo.gameserver.services.world.WorldService;
import org.pokemmo.gameserver.util.SnowflakeIdGenerator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.pokemmo.db.jooq.Tables.POKEMON;
import static org.pokemmo.db.jooq.Tables.OWNED_ITEM;

/** Shared Pokemon service constants used by focused persistence services. */
final class PokemonServiceStore {
    static final int PC_CONTAINER_ID = 0;
    static final int PARTY_CONTAINER_ID = 1;
    static final int MAIN_INVENTORY_ID = 1;
    static final short NO_HELD_ITEM = -1;
    static final short RANDOM_PARTICLE_EFFECT = -3;
    static final short UNSIGNED_RANDOM_PARTICLE_EFFECT = 253;
}
