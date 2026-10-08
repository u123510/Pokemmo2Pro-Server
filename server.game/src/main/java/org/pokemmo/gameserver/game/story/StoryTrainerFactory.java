package org.pokemmo.gameserver.game.story;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.pokemmo.gameserver.game.move.MoveManager;
import org.pokemmo.gameserver.game.pokemon.PokemonData;
import org.pokemmo.gameserver.game.pokemon.PokemonManager;
import org.pokemmo.gameserver.game.pokemon.PokemonNatureType;
import org.pokemmo.gameserver.game.pokemon.PokemonStatType;
import org.server.Session;

/** Builds detached trainer teams declared by a generic story action. */
final class StoryTrainerFactory {
    private StoryTrainerFactory() {
    }

    static PokemonData[] createTeam(Session session, JsonArray team) {
        List<PokemonData> result = new ArrayList<>();
        for (JsonElement element : team) {
            if (!element.isJsonObject()) {
                throw new IllegalArgumentException("剧情训练家队伍成员必须是对象");
            }
            result.add(createPokemon(session, element.getAsJsonObject(), result.size()));
        }
        if (result.isEmpty() || result.size() > 6) {
            throw new IllegalArgumentException("剧情训练家队伍数量必须在 1 到 6 之间");
        }
        return result.toArray(PokemonData[]::new);
    }

    private static PokemonData createPokemon(Session session, JsonObject config, int position) {
        int species = StoryProgram.integer(config, "species", 0);
        int level = StoryProgram.integer(config, "level", 0);
        if (species <= 0 || level < 1 || level > 100
                || PokemonManager.getPokemonoexData(species) == null) {
            throw new IllegalArgumentException("剧情训练家宝可梦配置无效");
        }
        short[] ivs = shorts(config, "ivs", 10, 6);
        short[] evs = shorts(config, "evs", 0, 6);
        short[] moves = shorts(config, "moves", 0, 4);
        for (short move : moves) {
            if (move != 0 && MoveManager.getPokemonMove(move) == null) {
                throw new IllegalArgumentException("剧情训练家招式不存在: " + move);
            }
        }
        int personality = StoryProgram.integer(config, "personality", 0);
        PokemonNatureType nature = PokemonNatureType.getByPersonalityValue(personality);
        var dex = PokemonManager.getPokemonoexData(species);
        short hp = dex.getPokemonAbilityValue(PokemonStatType.HP, ivs[0], evs[0],
                (short) level, nature);
        PokemonData pokemon = new PokemonData.Builder()
                .setPokemonId(session.attr(org.pokemmo.gameserver.protocol.GameProtocol.ATTRIBUTE_CHARACTER_MANAGER)
                        .get().getSnowflakeIdGenerator().nextId())
                .setPokemonDexData(dex)
                .setPokemonIndexId((short) species)
                .setContainerPos((short) position)
                .setLevel((short) level)
                .setPersonalityValue(personality)
                .setPokemonNatureType(nature)
                .setPokemonIvs(ivs)
                .setPokemonEvs(evs)
                .setMoves(moves)
                .setPokemonCurrentHp(hp)
                .setPokemonMaxHp(hp)
                .build();
        pokemon.setContainerId(org.pokemmo.gameserver.game.container.PokemonContainerType.EVENT.getType());
        for (int i = 0; i < moves.length; i++) {
            if (moves[i] != 0) {
                pokemon.getMovesPp()[i] = MoveManager.getPokemonMove(moves[i]).getMoveBasePp();
            }
        }
        return pokemon;
    }

    private static short[] shorts(JsonObject config, String key, int fallback, int length) {
        JsonArray values = StoryProgram.array(config, key);
        short[] result = new short[length];
        for (int i = 0; i < length && i < values.size(); i++) {
            result[i] = values.get(i).getAsShort();
        }
        if (values.isEmpty()) {
            java.util.Arrays.fill(result, (short) fallback);
        }
        return result;
    }
}
