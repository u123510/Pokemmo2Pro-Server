package org.pokemmo.gameserver.command.commands;

import com.google.inject.Inject;
import org.pokemmo.db.jooq.tables.records.ContainerRecord;
import org.pokemmo.gameserver.command.Command;
import org.pokemmo.gameserver.command.CommandContext;
import org.pokemmo.gameserver.game.container.PokemonContainerType;
import org.pokemmo.gameserver.game.particleEffectType.ParticleEffectType;
import org.pokemmo.gameserver.game.pokemon.PokemonData;
import org.pokemmo.gameserver.game.pokemon.UpdatePokemonData;
import org.pokemmo.gameserver.protocol.packets.s2c.SendPokemonContainerPacket;
import org.pokemmo.gameserver.protocol.packets.s2c.SendUpdatePokemonDataPacket;

import java.util.Arrays;
import java.util.List;

public class AddParticleCommand implements Command {
    private static final int MIN_PARTICLE_ID = 0;
    private static final int MAX_PARTICLE_ID = 38;

    @Inject
    public AddParticleCommand() {
    }

    @Override
    public String getName() {
        return "addparticle";
    }

    @Override
    public String getUsage() {
        return "//addparticle <party slot 0-5> <particle id 0-38>";
    }

    @Override
    public void execute(CommandContext context, String[] arguments) {
        if (arguments.length != 2) {
            context.reply("Usage: " + getUsage());
            return;
        }

        int partyPosition;
        int particleId;
        try {
            partyPosition = Integer.parseInt(arguments[0]);
            particleId = Integer.parseInt(arguments[1]);
        } catch (NumberFormatException exception) {
            context.reply("Party slot and particle ID must be integers.");
            return;
        }
        if (partyPosition < 0 || partyPosition >= context.getCharacterManager().getPartyPokemons().length) {
            context.reply("Party slot must be an integer from 0 to 5.");
            return;
        }
        if (particleId < MIN_PARTICLE_ID
                || particleId > MAX_PARTICLE_ID
                || ParticleEffectType.getByType(particleId) == null) {
            context.reply("Particle ID must be an integer from 0 to 38.");
            return;
        }

        PokemonData targetPokemon = findTargetPokemon(context, partyPosition);
        if (targetPokemon == null) {
            context.reply("There is no Pokemon in that party slot.");
            return;
        }

        short[] particleEffects = targetPokemon.getParticleEffectsForClient();
        if (particleEffects == null) {
            context.reply("The Pokemon particle data is invalid.");
            return;
        }
        for (short existingParticle : particleEffects) {
            if (existingParticle == particleId) {
                context.reply("Party slot " + partyPosition + " already has particle " + particleId + ".");
                return;
            }
        }

        short[] updatedParticles = Arrays.copyOf(particleEffects, particleEffects.length + 1);
        updatedParticles[updatedParticles.length - 1] = (short) particleId;
        long characterId = context.getCharacterManager().getCharacterData()
                .getPlayerEntity().getEntityGameId();
        if (!context.getGameServerService().updatePokemonParticleEffects(
                characterId, targetPokemon.getPokemonId(), updatedParticles)) {
            context.reply("The Pokemon particle could not be saved.");
            return;
        }

        targetPokemon.setParticleEffects(updatedParticles);
        context.getCharacterManager().getPartyPokemons()[partyPosition] = targetPokemon;
        context.getSession().send(new SendUpdatePokemonDataPacket(new UpdatePokemonData.Builder()
                .setUpdatePokemon(targetPokemon)
                .setIsReloadPokemonParticleEffects(true)
                .build()));
        refreshParty(context, characterId);
        context.reply("Particle " + particleId + " added to party slot " + partyPosition + ".");
    }

    private PokemonData findTargetPokemon(CommandContext context, int partyPosition) {
        PokemonData targetPokemon = context.getCharacterManager().getPartyPokemons()[partyPosition];
        if (targetPokemon != null && targetPokemon.getContainerPosition() == partyPosition) {
            return targetPokemon;
        }

        long characterId = context.getCharacterManager().getCharacterData()
                .getPlayerEntity().getEntityGameId();
        ContainerRecord partyContainer = context.getGameServerService()
                .getContainerByType(PokemonContainerType.PARTY);
        if (partyContainer == null) {
            return null;
        }
        return context.getGameServerService()
                .getCharacterContainerPokemons(characterId, partyContainer)
                .stream()
                .filter(pokemon -> pokemon.getContainerPosition() == partyPosition)
                .findFirst()
                .orElse(null);
    }

    private void refreshParty(CommandContext context, long characterId) {
        ContainerRecord partyContainer = context.getGameServerService()
                .getContainerByType(PokemonContainerType.PARTY);
        if (partyContainer == null) {
            return;
        }
        List<PokemonData> partyPokemons = context.getGameServerService()
                .getCharacterContainerPokemons(characterId, partyContainer);
        context.getSession().send(new SendPokemonContainerPacket(partyContainer, partyPokemons));
    }
}
