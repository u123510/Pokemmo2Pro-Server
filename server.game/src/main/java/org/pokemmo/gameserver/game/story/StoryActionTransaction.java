package org.pokemmo.gameserver.game.story;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.pokemmo.gameserver.game.character.CharacterManager;
import org.pokemmo.gameserver.game.events.EventRegionType;
import org.pokemmo.gameserver.game.pokemon.PokemonData;
import org.pokemmo.gameserver.protocol.packets.s2c.SendGameSideEventFlagPacket;
import org.pokemmo.gameserver.protocol.packets.s2c.SendInventoryPacket;
import org.pokemmo.gameserver.services.story.StoryActionStore;

/** Builds and publishes one atomic durable action group for a story node. */
final class StoryActionTransaction {
    private StoryActionTransaction() {
    }

    static StoryActionStore.Plan prepare(
            CharacterManager manager, StoryProgram program, JsonObject node, String actionKey) {
        List<StoryActionStore.ItemGrant> grants = new ArrayList<>();
        List<StoryActionStore.ItemRemoval> removals = new ArrayList<>();
        List<org.pokemmo.db.jooq.tables.records.PokemonRecord> pokemon = new ArrayList<>();
        List<Integer> storyBits = new ArrayList<>();
        List<Integer> extraBits = new ArrayList<>();
        List<Integer> badgeBits = new ArrayList<>();
        boolean champion = false;
        boolean durable = false;
        boolean unsupported = false;
        for (JsonElement element : StoryProgram.array(node, "actions")) {
            if (!element.isJsonObject()) continue;
            JsonObject action = element.getAsJsonObject();
            String type = StoryProgram.string(action, "type");
            if (type == null) continue;
            switch (type) {
                case "GRANT_ITEM" -> {
                    int item = StoryProgram.integer(action, "itemId", 0);
                    int amount = StoryProgram.integer(action, "amount", 0);
                    if (item <= 0 || amount <= 0 || amount > Short.MAX_VALUE) {
                        throw new IllegalArgumentException("剧情奖励道具参数无效");
                    }
                    grants.add(new StoryActionStore.ItemGrant((short) item, (short) amount,
                            manager.getSnowflakeIdGenerator().nextId()));
                    durable = true;
                }
                case "REMOVE_ITEM" -> {
                    int item = StoryProgram.integer(action, "itemId", 0);
                    int amount = StoryProgram.integer(action, "amount", 0);
                    if (item <= 0 || amount <= 0 || amount > Short.MAX_VALUE) {
                        throw new IllegalArgumentException("剧情扣除道具参数无效");
                    }
                    removals.add(new StoryActionStore.ItemRemoval((short) item, (short) amount));
                    durable = true;
                }
                case "GRANT_POKEMON" -> {
                    int species = StoryProgram.integer(action, "species", 0);
                    int level = StoryProgram.integer(action, "level", 0);
                    if (species <= 0 || level < 1 || level > 100) {
                        throw new IllegalArgumentException("剧情奖励宝可梦参数无效");
                    }
                    JsonArray configuredMoves = StoryProgram.array(action, "moves");
                    short[] moves = new short[Math.min(4, configuredMoves.size())];
                    for (int index = 0; index < moves.length; index++) {
                        moves[index] = configuredMoves.get(index).getAsShort();
                    }
                    PokemonData candidate = StoryPokemonFactory.create(manager, species, level, moves);
                    pokemon.add(candidate.toPokemonRecord());
                    durable = true;
                }
                case "SET_STORY_BIT" -> {
                    storyBits.add(StoryProgram.integer(action, "bit", -1));
                    durable = true;
                }
                case "SET_EXTRA_STORY_BIT" -> {
                    extraBits.add(StoryProgram.integer(action, "bit", -1));
                    durable = true;
                }
                case "SET_BADGE" -> {
                    badgeBits.add(StoryProgram.integer(action, "bit", -1));
                    durable = true;
                }
                case "SET_CHAMPION" -> {
                    champion = true;
                    durable = true;
                }
                case "SET_ELITE_STAGE", "SET_OPENING_STAGE", "PARCEL_OPERATION",
                        "START_SAFARI", "HEAL_PARTY" -> unsupported = true;
                default -> {
                }
            }
        }
        if (!durable) return null;
        if (unsupported) {
            if (!grants.isEmpty() || !removals.isEmpty() || !pokemon.isEmpty()) {
                throw new IllegalStateException("剧情节点同时包含未支持的持久化动作和奖励: " + actionKey);
            }
            return null;
        }
        return new StoryActionStore.Plan(actionKey, grants, removals, pokemon,
                storyBits, extraBits, badgeBits, champion);
    }

    static void apply(CharacterManager manager, StoryActionStore.Plan plan,
                      StoryActionStore.Result result) {
        long characterId = PalletOpeningService.characterId(manager);
        if (!plan.itemGrants().isEmpty() || !plan.itemRemovals().isEmpty()) {
            var inventory = manager.getCharacterService().getInventory();
            manager.getCharacterSession().send(new SendInventoryPacket(
                    inventory,
                    manager.getCharacterService().getItemsByContainerAndCharacter(characterId, inventory)));
        }
        if (!plan.pokemonRewards().isEmpty()) {
            PalletStoryParty.refresh(manager);
        }
        if (!plan.storyBits().isEmpty() || !plan.extraStoryBits().isEmpty()) {
            manager.getCharacterData().getStoryLineFlag()[0] = result.storyFlag();
            manager.getCharacterData().getStoryLineFlag()[1] = result.extraStoryFlag();
        }
        if (!plan.badgeBits().isEmpty()) {
            manager.getCharacterData().getBadgeFlag()[0] = result.badgeFlag();
        }
        if (plan.champion()) {
            manager.getCharacterData().getChampionFlag()[0] = result.champion();
        }
    }

    static void finish(CharacterManager manager) {
        if (manager.getPalletStory().durableActionKey == null) return;
        PalletStoryNpcs.refresh(manager);
        manager.getCharacterSession().send(new SendGameSideEventFlagPacket(
                EventRegionType.KANTO.getType(),
                manager.getActiveGameEvents(EventRegionType.KANTO)));
        manager.getPalletStory().durableActionKey = null;
    }

    static boolean skip(CharacterManager manager, String actionKey, String type) {
        if (!actionKey.equals(manager.getPalletStory().durableActionKey)) return false;
        return switch (type) {
            case "GRANT_ITEM", "REMOVE_ITEM", "GRANT_POKEMON",
                    "SET_STORY_BIT", "SET_EXTRA_STORY_BIT", "SET_BADGE", "SET_CHAMPION" -> true;
            default -> false;
        };
    }
}
