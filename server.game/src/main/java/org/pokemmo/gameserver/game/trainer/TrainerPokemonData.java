package org.pokemmo.gameserver.game.trainer;

import org.pokemmo.gameserver.game.pokemon.PokemonData;
import org.pokemmo.gameserver.game.pokemon.PokemonManager;
import org.pokemmo.gameserver.game.pokemon.PokemonNatureType;
import org.pokemmo.gameserver.game.pokemon.PokemonStatType;
import org.pokemmo.gameserver.game.move.MoveManager;
import org.pokemmo.gameserver.protocol.GameProtocol;
import org.server.Session;

public class TrainerPokemonData {
    private short pokemonIndexId;
    private short pokemonContainerPos;
    private short level;
    private int personalityValue;
    private short[] pokemonIvs;
    private short[] pokemonEvs;
    private byte abilityIndex;
    private short[] moves;
    private short[] movesPp;
    private short item;
    private byte ballType;
    public TrainerPokemonData(short pokemonIndexId, short pokemonContainerPos, short level, int personalityValue, short[] pokemonIvs, short[] pokemonEvs, byte abilityIndex, short[] moves, short[] movesPp, short item, byte ballType) {
        this.pokemonIndexId = pokemonIndexId;
        this.pokemonContainerPos = pokemonContainerPos;
        this.level = level;
        this.personalityValue = personalityValue;
        this.pokemonIvs = pokemonIvs;
        this.pokemonEvs = pokemonEvs;
        this.abilityIndex = abilityIndex;
        this.moves = moves;
        this.movesPp = movesPp;
        this.item = item;
        this.ballType = ballType;
    }
    public PokemonData convertPokemonData(Session characterSession){
        var dex = PokemonManager.getPokemonoexData(this.pokemonIndexId);
        if (dex == null) {
            throw new IllegalStateException("训练家队伍宝可梦不存在: " + this.pokemonIndexId);
        }
        short[] resolvedMoves = this.moves == null ? new short[4] : this.moves.clone();
        boolean hasMove = false;
        for (short move : resolvedMoves) {
            if (move != 0) {
                hasMove = true;
                break;
            }
        }
        if (!hasMove) {
            resolvedMoves = dex.genderWildPokemonMoves(this.level);
        }
        short[] resolvedPp = this.movesPp == null ? new short[4] : this.movesPp.clone();
        for (int index = 0; index < resolvedMoves.length && index < resolvedPp.length; index++) {
            if (resolvedMoves[index] != 0 && resolvedPp[index] <= 0
                    && MoveManager.getPokemonMove(resolvedMoves[index]) != null) {
                resolvedPp[index] = MoveManager.getPokemonMove(resolvedMoves[index]).getMoveBasePp();
            }
        }
        short fullHp = dex.getPokemonAbilityValue(PokemonStatType.HP,this.pokemonIvs[0],this.pokemonEvs[0],this.level,PokemonNatureType.getByPersonalityValue(this.personalityValue));
        PokemonData pokemonData = new PokemonData.Builder()
                .setPokemonId(characterSession.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get().getSnowflakeIdGenerator().nextId())
                .setPokemonDexData(dex)
                .setPokemonIndexId(this.pokemonIndexId)
                .setContainerPos(this.pokemonContainerPos)
                .setLevel(this.level)
                .setPersonalityValue(this.personalityValue)
                .setPokemonCurrentHp(fullHp)
                .setPokemonMaxHp(fullHp)
                .setPokemonNatureType(PokemonNatureType.getByPersonalityValue(this.personalityValue))
                .setPokemonIvs(this.pokemonIvs)
                .setPokemonEvs(this.pokemonEvs)
                .setAbilityIndex(this.abilityIndex)
                .setMoves(resolvedMoves)
                .setMovesPp(resolvedPp)
                .setItem(this.item)
                .setBallType(this.ballType)
                .build();
        return pokemonData;
    }
}
