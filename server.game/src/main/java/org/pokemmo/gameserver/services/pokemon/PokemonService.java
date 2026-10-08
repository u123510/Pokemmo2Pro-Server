package org.pokemmo.gameserver.services.pokemon;

import org.pokemmo.db.Database;
import org.pokemmo.db.jooq.tables.records.ContainerRecord;
import org.pokemmo.db.jooq.tables.records.PokemonRecord;
import org.pokemmo.gameserver.game.pokemon.PokemonData;
import org.pokemmo.gameserver.services.GameServerService.PokemonPositionChange;
import org.pokemmo.gameserver.services.character.CharacterService;
import org.pokemmo.gameserver.services.world.WorldService;
import org.pokemmo.gameserver.util.SnowflakeIdGenerator;

import java.util.List;
import java.util.Optional;

/** Compatibility facade for focused Pokemon domain services. */
public final class PokemonService {
    private final PokemonAttributeService attributeService;
    private final PokemonItemService itemService;
    private final PokemonContainerService containerService;
    private final PokemonCaptureService captureService;
    private final PokemonReleaseService releaseService;

    public PokemonService(Database database, WorldService worldService,
                          CharacterService characterService) {
        this.attributeService = new PokemonAttributeService(database);
        this.itemService = new PokemonItemService(database);
        this.containerService = new PokemonContainerService(database, worldService, characterService);
        this.captureService = new PokemonCaptureService(database, characterService);
        this.releaseService = new PokemonReleaseService(database);
    }

    public void addPokemon(PokemonRecord pokemon) { attributeService.addPokemon(pokemon); }
    public PokemonCaptureService.CaptureResult captureWildPokemon(
            long characterId, PokemonData wildPokemon, short ballItemIndexId,
            boolean caught) {
        return captureService.capture(characterId, wildPokemon, ballItemIndexId, caught);
    }

    public PokemonCaptureService.CaptureResult captureSafariPokemon(
            long characterId, PokemonData wildPokemon, short ballItemIndexId,
            boolean caught) {
        return captureService.captureSafari(characterId, wildPokemon, ballItemIndexId, caught);
    }
    public boolean updatePokemonAlpha(long c, long p, boolean v) { return attributeService.updatePokemonAlpha(c, p, v); }
    public boolean updatePokemonShiny(long c, long p, boolean v) { return attributeService.updatePokemonShiny(c, p, v); }
    public boolean updatePokemonSecret(long c, long p, boolean v) { return attributeService.updatePokemonSecret(c, p, v); }
    public boolean updatePokemonPersonalityValue(long c, long p, int v) { return attributeService.updatePokemonPersonalityValue(c, p, v); }
    public boolean updatePokemonOtName(long c, long p, String v) { return attributeService.updatePokemonOtName(c, p, v); }
    public boolean updatePokemonName(long c, long p, String v) { return attributeService.updatePokemonName(c, p, v); }
    public boolean updatePokemonNormalRibbons(long c, long p, boolean[] v) { return attributeService.updatePokemonNormalRibbons(c, p, v); }
    public boolean updatePokemonRibbons(long c, long p, short[] contest, boolean[] normal) { return attributeService.updatePokemonRibbons(c, p, contest, normal); }
    public boolean updatePokemonBallType(long c, long p, short v) { return attributeService.updatePokemonBallType(c, p, v); }
    public boolean updatePokemonParticleEffects(long c, long p, short[] v) { return attributeService.updatePokemonParticleEffects(c, p, v); }
    public boolean updatePokemonCurrentSelectParticleEffect(long c, long p, short v) { return attributeService.updatePokemonCurrentSelectParticleEffect(c, p, v); }
    public boolean updatePokemonIvs(long c, long p, short[] v) { return attributeService.updatePokemonIvs(c, p, v); }
    public boolean updatePokemonHiddenAbility(long c, long p, boolean v) { return attributeService.updatePokemonHiddenAbility(c, p, v); }
    public boolean updatePokemonAbilityIndex(long c, long p, short v) { return attributeService.updatePokemonAbilityIndex(c, p, v); }
    public boolean updatePokemonEvs(long c, long p, short[] v) { return attributeService.updatePokemonEvs(c, p, v); }
    public boolean updatePokemonFriendValue(long c, long p, short v) { return attributeService.updatePokemonFriendValue(c, p, v); }
    public boolean updatePokemonFormType(long c, long p, short v) { return attributeService.updatePokemonFormType(c, p, v); }
    public boolean updatePokemonGrowth(long c, long p, int exp, short level, short[] evs) { return attributeService.updatePokemonGrowth(c, p, exp, level, evs); }
    public boolean updatePokemonItem(long c, long p, int container, short item, SnowflakeIdGenerator ids) { return itemService.updatePokemonItem(c, p, container, item, ids); }
    public List<PokemonData> getCharacterContainerPokemons(long c, ContainerRecord container) { return containerService.getCharacterContainerPokemons(c, container); }
    public Optional<List<PokemonData>> changePokemonPositions(long c, List<PokemonPositionChange> changes) { return containerService.changePokemonPositions(c, changes); }
    public Optional<List<PokemonData>> sortPokemon(long c, int containerType, int[] containerIndexes, int sortFlags) {
        return containerService.sortPokemon(c, containerType, containerIndexes, sortFlags);
    }
    public short findNextFreePartyPosition(long c) { return containerService.findNextFreePartyPosition(c); }
    public short findNextFreePcBoxPosition(long c) { return containerService.findNextFreePcBoxPosition(c); }
    public boolean releasePokemonFromPc(long characterId, long pokemonId) {
        return releaseService.releaseFromPc(characterId, pokemonId);
    }
    public boolean updatePokemonMoves(long c, long p, short[] moves, short[] movesPp, byte ppUpTimes) {
        return attributeService.updatePokemonMoves(c, p, moves, movesPp, ppUpTimes);
    }
}
