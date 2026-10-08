package org.pokemmo.gameserver.game.story;

import org.pokemmo.gameserver.game.character.CharacterManager;
import org.pokemmo.gameserver.game.container.PokemonContainerType;
import org.pokemmo.gameserver.game.entity.NpcEntity;
import org.pokemmo.gameserver.game.pokemon.PokemonData;
import org.pokemmo.gameserver.game.pokemon.PokemonManager;
import org.pokemmo.gameserver.protocol.packets.s2c.SendCharacterPokemonAbilityPacket;
import org.pokemmo.gameserver.protocol.packets.s2c.SendPokemonContainerPacket;

/** Party recovery and client snapshots after committed story transactions. */
final class PalletStoryParty {
    private PalletStoryParty() { }

    static void mother(CharacterManager manager, NpcEntity npc) {
        if (PalletOpeningService.progress(manager).starter() == 3) {
            PalletStoryScene.say(manager, npc.getEntityGameId(), manager.getCharacterData().getPlayerEntity().getSex() == 0
                    ? "momBoy" : "momGirl", () -> PalletStoryScene.end(manager));
            return;
        }
        PalletStoryScene.say(manager, npc.getEntityGameId(), "momRest", () -> {
            var result = manager.getCharacterService().healPartyFully(
                    PalletOpeningService.characterId(manager), manager.getPartyPokemons());
            if (!result.success()) throw new IllegalStateException(result.reason());
            refresh(manager);
            PalletStoryScene.say(manager, npc.getEntityGameId(), "momDone", () -> PalletStoryScene.end(manager));
        });
    }

    static void refresh(CharacterManager manager) {
        var container = manager.getCharacterService().getContainerByType(PokemonContainerType.PARTY);
        if (container == null) throw new IllegalStateException("队伍容器尚未就绪");
        var party = manager.getCharacterService().getCharacterContainerPokemons(
                PalletOpeningService.characterId(manager), container);
        PokemonData[] slots = new PokemonData[6];
        for (PokemonData pokemon : party) {
            int slot = pokemon.getContainerPosition();
            if (slot < 0 || slot >= slots.length || slots[slot] != null) {
                throw new IllegalStateException("队伍存档槽位无效");
            }
            slots[slot] = pokemon;
        }
        System.arraycopy(slots, 0, manager.getPartyPokemons(), 0, 6);
        manager.getCharacterSession().send(new SendPokemonContainerPacket(container, party));
        PokemonData leader = slots[0];
        if (leader != null && (leader.getEggValue() & 1) == 0 && leader.getCurrentHp() > 0
                && PokemonManager.outBattleAbility.contains(leader.getPokemonAbilityIndexId())) {
            manager.getCharacterSession().send(new SendCharacterPokemonAbilityPacket(leader.getPokemonAbilityIndexId()));
        }
    }
}
