package org.pokemmo.gameserver.protocol.packets.c2s;

import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.pokemmo.gameserver.game.character.CharacterManager;
import org.pokemmo.gameserver.game.character.PlayerVisibilityService;
import org.pokemmo.gameserver.game.pokemon.PokemonData;
import org.pokemmo.gameserver.protocol.GameProtocol;
import org.pokemmo.gameserver.protocol.packets.s2c.SendSetFollowPokemonPacket;
import org.pokemmo.gameserver.services.GameServerService;
import org.server.IncomingPacket;
import org.server.Session;
import org.server.bytes.ByteBufEx;

@Slf4j
public class ChangeFllowPokemonPacket extends IncomingPacket {
    private static final byte FOLLOW_BY_PARTY_POSITION = 0;
    private static final byte FOLLOW_BY_POKEMON_ID = 1;
    private static final byte CLEAR_FOLLOWER = 2;
    private static final int FORM_MASK = 0x1F;
    private static final int FEMALE_FLAG = 0x20;
    private static final int SHINY_FLAG = 0x40;
    private static final int ALPHA_FLAG = 0x80;

    private byte type;
    private long selectionValue;

    @Inject
    private GameServerService gameServerService;

    @Override
    public void decode(ByteBufEx buffer) {
        type = buffer.readByte();
        selectionValue = buffer.readLongLE();
    }

    @Override
    public void handle(Session session) throws Exception {
        CharacterManager characterManager = session.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get();
        if (characterManager == null || characterManager.getCharacterData() == null
                || characterManager.getCharacterData().getPlayerEntity() == null) {
            log.warn("忽略没有角色上下文的精灵跟随封包");
            return;
        }

        if (type == CLEAR_FOLLOWER) {
            if (selectionValue != 0) {
                log.warn("忽略携带非法取消值的精灵跟随封包: {}", selectionValue);
                return;
            }
            updateFollower(session, characterManager, (short) 0, (short) 0);
            return;
        }

        PokemonData follower = switch (type) {
            case FOLLOW_BY_PARTY_POSITION -> getPartyPokemonByPosition(
                    characterManager.getPartyPokemons(), selectionValue);
            case FOLLOW_BY_POKEMON_ID -> getPartyPokemonById(
                    characterManager.getPartyPokemons(), selectionValue);
            default -> null;
        };
        if (type != FOLLOW_BY_PARTY_POSITION && type != FOLLOW_BY_POKEMON_ID) {
            log.warn("忽略未知精灵跟随类型: {}", type & 0xFF);
            return;
        }
        if (follower == null) {
            log.warn("未在当前队伍找到跟随精灵: type={}, value={}", type & 0xFF, selectionValue);
            return;
        }
        if ((follower.getEggValue() & 1) != 0) {
            log.warn("拒绝将蛋设为跟随精灵: pokemonId={}", follower.getPokemonId());
            return;
        }
        if (follower.getPokemonIndexId() <= 0 || follower.getFormType() < 0
                || follower.getFormType() > FORM_MASK) {
            log.warn("跟随精灵外观数据非法: pokemonId={}, indexId={}, form={}",
                    follower.getPokemonId(), follower.getPokemonIndexId(), follower.getFormType());
            return;
        }

        short rarity = buildFollowerRarity(follower);
        updateFollower(session, characterManager, follower.getPokemonIndexId(), rarity);
    }

    private void updateFollower(Session session, CharacterManager characterManager,
                                short pokemonIndexId, short rarity) {
        long characterId = characterManager.getCharacterData().getPlayerEntity().getEntityGameId();
        if (!gameServerService.updateCharacterFollower(characterId, pokemonIndexId, rarity)) {
            log.warn("角色 {} 更新跟随精灵失败: indexId={}, rarity={}",
                    characterId, pokemonIndexId, rarity);
            return;
        }

        characterManager.getCharacterData().getPlayerEntity().setFollowPokemonIndexId(pokemonIndexId);
        characterManager.getCharacterData().getPlayerEntity().setFollowPokemonRarity(rarity);
        SendSetFollowPokemonPacket packet = new SendSetFollowPokemonPacket(
                characterId, pokemonIndexId, rarity, false);
        PlayerVisibilityService.broadcast(characterManager, packet, true);
    }

    private short buildFollowerRarity(PokemonData pokemon) {
        int rarity = pokemon.getFormType() & FORM_MASK;
        if (pokemon.getPokemonSex() == 1) {
            rarity |= FEMALE_FLAG;
        }
        if (pokemon.isShiny()) {
            rarity |= SHINY_FLAG;
        }
        if (pokemon.isAlpha()) {
            rarity |= ALPHA_FLAG;
        }
        return (short) rarity;
    }

    private PokemonData getPartyPokemonByPosition(PokemonData[] partyPokemons, long position) {
        if (partyPokemons == null || position < 0 || position >= partyPokemons.length) {
            return null;
        }
        return partyPokemons[(int) position];
    }

    private PokemonData getPartyPokemonById(PokemonData[] partyPokemons, long pokemonId) {
        if (partyPokemons == null || pokemonId <= 0) {
            return null;
        }
        for (PokemonData pokemon : partyPokemons) {
            if (pokemon != null && pokemon.getPokemonId() == pokemonId) {
                return pokemon;
            }
        }
        return null;
    }
}
