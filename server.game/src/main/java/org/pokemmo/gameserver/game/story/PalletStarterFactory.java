package org.pokemmo.gameserver.game.story;

import java.time.LocalDateTime;

import org.pokemmo.gameserver.game.character.CharacterManager;
import org.pokemmo.gameserver.game.container.PokemonContainerType;
import org.pokemmo.gameserver.game.pokemon.PokemonData;
import org.pokemmo.gameserver.game.pokemon.PokemonDexData;
import org.pokemmo.gameserver.game.pokemon.PokemonManager;
import org.pokemmo.gameserver.game.pokemon.PokemonNatureType;
import org.pokemmo.gameserver.game.pokemon.PokemonStatType;

/** Builds detached level-five Pokemon, with no writes and no online party mutation. */
public final class PalletStarterFactory {
    private PalletStarterFactory() { }

    public static PokemonData create(CharacterManager manager, PalletStoryCatalog.Starter starter, boolean gift) {
        PokemonDexData dex = PokemonManager.getPokemonoexData(starter.species());
        int personality = manager.getRandom().nextInt(Integer.MAX_VALUE);
        PokemonNatureType nature = PokemonNatureType.getByPersonalityValue(personality);
        short[] ivs = gift ? new short[]{15, 15, 15, 15, 15, 15} : new short[]{10, 10, 10, 10, 10, 10};
        short[] evs = new short[6];
        short hp = dex.getPokemonAbilityValue(PokemonStatType.HP, ivs[0], 0, 5, nature);
        long characterId = manager.getCharacterData().getPlayerEntity().getEntityGameId();
        PokemonData pokemon = new PokemonData.Builder()
                .setPokemonDexData(dex).setPokemonId(manager.getSnowflakeIdGenerator().nextId())
                .setTrainerId(gift ? characterId : 0).setPokemonIndexId((short) starter.species())
                .setLevel((short) 5).setPersonalityValue(personality).setPokemonNatureType(nature)
                .setPokemonCurrentHp(hp).setPokemonMaxHp(hp).setPokemonIvs(ivs).setPokemonEvs(evs)
                .setMoves(new short[]{starter.moves().get(0), starter.moves().get(1),
                        starter.moves().get(2), starter.moves().get(3)}).build();
        pokemon.setOriginalTrainerId(gift ? characterId : 0);
        pokemon.setOtName(gift ? manager.getCharacterData().getPlayerEntity().getEntityName() : "");
        pokemon.setContainerId(gift ? PokemonContainerType.PARTY.getType() : PokemonContainerType.EVENT.getType());
        pokemon.setExp(dex.getGetExpSpeedType().getExpByLevel(5));
        pokemon.setCatchTime(LocalDateTime.now());
        pokemon.setCatchLevel((short) 5);
        pokemon.setCatchRegion((short) 0);
        pokemon.setFriendValue((short) 70);
        for (int i = 0; i < 4; i++) {
            if (pokemon.getMoves()[i] != 0) {
                pokemon.getMovesPp()[i] = (short) Byte.toUnsignedInt(pokemon.getPokemonMoveMaxPp(i));
            }
        }
        if (gift) pokemon.addNormalRibbon(2);
        return pokemon;
    }
}
