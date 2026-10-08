package org.pokemmo.gameserver.game.story;

import org.pokemmo.db.jooq.tables.records.ContainerRecord;
import org.pokemmo.gameserver.game.container.PokemonContainerType;
import org.pokemmo.gameserver.game.pokemon.PokemonData;
import org.pokemmo.gameserver.game.pokemon.PokemonManager;
import org.pokemmo.gameserver.game.move.MoveManager;
import org.pokemmo.gameserver.game.character.CharacterManager;

/** Creates and persists one story gift Pokemon using the normal party/PC rules. */
final class StoryPokemonFactory {
    private StoryPokemonFactory() {
    }

    static PokemonData create(CharacterManager manager, int species, int level, short[] moves) {
        long characterId = PalletOpeningService.characterId(manager);
        short partyPosition = manager.findNextFreePartyPosition();
        PokemonContainerType containerType = partyPosition >= 0
                ? PokemonContainerType.PARTY : PokemonContainerType.PC;
        short position = partyPosition >= 0
                ? partyPosition
                : manager.getCharacterService().findNextFreePcBoxPosition(characterId);
        if (position < 0) {
            throw new IllegalStateException("队伍和宝可梦盒都没有空位");
        }
        ContainerRecord container = manager.getCharacterService().getContainerByType(containerType);
        if (container == null) {
            throw new IllegalStateException("剧情奖励容器不存在: " + containerType);
        }
        var player = manager.getCharacterData().getPlayerEntity();
        PokemonData pokemon = PokemonManager.createWildPokemon(
                0,
                player.getEntityName(),
                player.getRegionIndexId(),
                manager.getCurrentMapDatas()[0].getRomMapHeaderIndex(),
                (short) species,
                (short) level,
                container.getId(),
                position,
                manager.getRandom(),
                manager.getSnowflakeIdGenerator());
        if (pokemon == null) {
            throw new IllegalStateException("剧情奖励宝可梦不存在: " + species);
        }
        pokemon.setTrainerId(characterId);
        pokemon.setOriginalTrainerId(characterId);
        pokemon.setOtName(player.getEntityName());
        pokemon.setName("");
        pokemon.setContainerId(container.getId());
        pokemon.setContainerPosition(position);
        pokemon.setCatchRegion(player.getRegionIndexId());
        pokemon.setCatchLevel((short) level);
        pokemon.setPokemonStatus(org.pokemmo.gameserver.game.pokemon.PokemonStatusType.NORMAL);
        short[] normalizedMoves = new short[4];
        if (moves != null) {
            System.arraycopy(moves, 0, normalizedMoves, 0, Math.min(moves.length, normalizedMoves.length));
        }
        short[] pp = new short[4];
        for (int index = 0; index < normalizedMoves.length; index++) {
            short move = normalizedMoves[index];
            if (move == 0) {
                continue;
            }
            var moveData = MoveManager.getPokemonMove(move);
            if (moveData == null) {
                throw new IllegalArgumentException("剧情奖励招式不存在: " + move);
            }
            pp[index] = moveData.getMoveBasePp();
        }
        pokemon.setMoves(normalizedMoves);
        pokemon.setMovesPp(pp);
        return pokemon;
    }

    static PokemonData grant(CharacterManager manager, int species, int level, short[] moves) {
        PokemonData pokemon = create(manager, species, level, moves);
        manager.getCharacterService().addPokemon(pokemon.toPokemonRecord());
        if (pokemon.getContainerId() == PokemonContainerType.PARTY.getType()) {
            PalletStoryParty.refresh(manager);
        }
        return pokemon;
    }
}
