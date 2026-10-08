package org.pokemmo.gameserver.protocol.packets.c2s;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.pokemmo.db.jooq.tables.records.ContainerRecord;
import org.pokemmo.gameserver.game.character.CharacterManager;
import org.pokemmo.gameserver.game.container.PokemonContainerType;
import org.pokemmo.gameserver.game.particleEffectType.ParticleEffectType;
import org.pokemmo.gameserver.game.pokemon.PokemonData;
import org.pokemmo.gameserver.game.pokemon.UpdatePokemonData;
import org.pokemmo.gameserver.protocol.GameProtocol;
import org.pokemmo.gameserver.protocol.packets.s2c.SendUpdatePokemonDataPacket;
import org.pokemmo.gameserver.services.GameServerService;
import org.server.IncomingPacket;
import org.server.Session;
import org.server.bytes.ByteBufEx;

/** Handles the particle effect selected in a Pokemon detail panel. */
@Slf4j
public final class SelectPokemonParticleEffectPacket extends IncomingPacket {
    private static final int PAYLOAD_SIZE = Long.BYTES + Byte.BYTES;
    private static final int CLEAR_SELECTION = 0xFF;
    private static final int RANDOM_SELECTION = 0xFD;
    private static final int DEFAULT_PARTICLE_EFFECT = 0;
    private static final int MAX_PARTICLE_EFFECT = 38;
    private static final short RANDOM_PARTICLE_EFFECT = -3;

    private long pokemonId;
    private short particleEffectId;

    @Inject
    private GameServerService gameServerService;

    @Override
    public void decode(ByteBufEx buffer) {
        if (buffer.readableBytes() != PAYLOAD_SIZE) {
            throw new IllegalArgumentException(
                    "精灵粒子选择封包长度错误: expected=" + PAYLOAD_SIZE
                            + ", actual=" + buffer.readableBytes());
        }

        pokemonId = buffer.readLongLE();
        int wireParticleEffectId = buffer.readUnsignedByte();
        particleEffectId = decodeParticleEffectId(wireParticleEffectId);
        if (!isValidParticleEffectId(particleEffectId)) {
            throw new IllegalArgumentException("非法的精灵粒子选择索引: " + wireParticleEffectId);
        }
        log.debug("收到精灵粒子选择: pokemonId={}, particleEffectId={}",
                pokemonId, particleEffectId);
    }

    @Override
    public void handle(Session session) {
        CharacterManager characterManager =
                session.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get();
        if (characterManager == null || characterManager.getCharacterData() == null
                || characterManager.getCharacterData().getPlayerEntity() == null) {
            log.warn("忽略没有角色上下文的精灵粒子选择封包");
            return;
        }
        if (pokemonId <= 0) {
            log.warn("忽略非法的精灵 ID: {}", pokemonId);
            return;
        }

        long characterId = characterManager.getCharacterData().getPlayerEntity().getEntityGameId();
        try {
            PokemonData pokemon = findOwnedPokemon(characterManager, characterId);
            if (pokemon == null) {
                log.warn("未找到角色 {} 所属的精灵: pokemonId={}", characterId, pokemonId);
                return;
            }
            if (!isSelectableParticle(pokemon)) {
                log.warn("拒绝选择未拥有的精灵粒子: characterId={}, pokemonId={}, particleEffectId={}",
                        characterId, pokemonId, particleEffectId);
                return;
            }
            if (!gameServerService.updatePokemonCurrentSelectParticleEffect(
                    characterId, pokemonId, particleEffectId)) {
                log.warn("保存精灵当前粒子失败: characterId={}, pokemonId={}, particleEffectId={}",
                        characterId, pokemonId, particleEffectId);
                return;
            }

            pokemon.setCurrentSelectParticleEffectType(particleEffectId);
            updateOnlinePartyPokemon(characterManager, pokemon);
            session.send(new SendUpdatePokemonDataPacket(new UpdatePokemonData.Builder()
                    .setUpdatePokemon(pokemon)
                    .setIsReloadCurrentSelectShowParticleEffect(true)
                    .build()));
        } catch (RuntimeException exception) {
            log.error("处理精灵粒子选择失败: characterId={}, pokemonId={}, particleEffectId={}",
                    characterId, pokemonId, particleEffectId, exception);
        }
    }

    private PokemonData findOwnedPokemon(CharacterManager characterManager, long characterId) {
        for (PokemonData pokemon : characterManager.getPartyPokemons()) {
            if (pokemon != null && pokemon.getPokemonId() == pokemonId) {
                return pokemon;
            }
        }

        ContainerRecord pcContainer = gameServerService.getContainerByType(PokemonContainerType.PC);
        if (pcContainer == null) {
            return null;
        }
        return gameServerService.getCharacterContainerPokemons(characterId, pcContainer)
                .stream()
                .filter(pokemon -> pokemon.getPokemonId() == pokemonId)
                .findFirst()
                .orElse(null);
    }

    private void updateOnlinePartyPokemon(CharacterManager characterManager, PokemonData pokemon) {
        PokemonData[] partyPokemons = characterManager.getPartyPokemons();
        for (int i = 0; i < partyPokemons.length; i++) {
            if (partyPokemons[i] != null && partyPokemons[i].getPokemonId() == pokemonId) {
                partyPokemons[i] = pokemon;
                return;
            }
        }
    }

    private boolean isSelectableParticle(PokemonData pokemon) {
        if (particleEffectId == RANDOM_PARTICLE_EFFECT) {
            return hasKnownParticleEffect(pokemon);
        }
        if (particleEffectId == -1 || particleEffectId == DEFAULT_PARTICLE_EFFECT
                || particleEffectId == pokemon.getCurrentSelectParticleEffectType()) {
            return true;
        }
        for (short effect : PokemonData.normalizeParticleEffects(pokemon.getParticleEffects())) {
            if (effect == particleEffectId) {
                return true;
            }
        }
        return false;
    }

    private boolean hasKnownParticleEffect(PokemonData pokemon) {
        for (short effect : PokemonData.normalizeParticleEffects(pokemon.getParticleEffects())) {
            if (PokemonData.isRenderableParticleEffect(effect)) {
                return true;
            }
        }
        log.warn("随机质子选择被拒绝：宝可梦没有可用质子, pokemonId={}, rawEffects={}",
                pokemon.getPokemonId(), java.util.Arrays.toString(pokemon.getParticleEffects()));
        return false;
    }

    private static short decodeParticleEffectId(int wireParticleEffectId) {
        if (wireParticleEffectId == CLEAR_SELECTION) {
            return -1;
        }
        if (wireParticleEffectId == RANDOM_SELECTION) {
            return RANDOM_PARTICLE_EFFECT;
        }
        return (short) wireParticleEffectId;
    }

    private static boolean isKnownParticleEffect(short particleEffectId) {
        return particleEffectId >= DEFAULT_PARTICLE_EFFECT
                && particleEffectId <= MAX_PARTICLE_EFFECT
                && ParticleEffectType.getByType(particleEffectId) != null;
    }

    private static boolean isValidParticleEffectId(short particleEffectId) {
        return particleEffectId == -1 || particleEffectId == RANDOM_PARTICLE_EFFECT
                || isKnownParticleEffect(particleEffectId);
    }
}
