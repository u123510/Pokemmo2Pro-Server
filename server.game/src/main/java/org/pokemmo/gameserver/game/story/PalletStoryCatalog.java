package org.pokemmo.gameserver.game.story;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import lombok.extern.slf4j.Slf4j;
import org.pokemmo.gameserver.game.move.MoveManager;
import org.pokemmo.gameserver.game.pokemon.PokemonManager;

/** The first authored story module, loaded after species and move resources. */
@Slf4j
public final class PalletStoryCatalog {
    public record Starter(int choice, int species, int rivalSpecies, int prompt, List<Short> moves) {
        public Starter { if (moves != null) moves = List.copyOf(moves); }
    }
    public record Content(boolean enabled, int trainerModel, Map<String, Integer> text, List<Starter> starters) { }
    private final Content content;

    public PalletStoryCatalog(Path directory) {
        Path file = directory.resolve("kanto/pallet_town/opening.jsonc");
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonReader jsonReader = new JsonReader(reader);
            jsonReader.setLenient(true);
            JsonObject root = JsonParser.parseReader(jsonReader).getAsJsonObject();
            Map<String, Integer> text = readText(root);
            JsonObject resources = StoryProgram.object(root, "resources");
            int trainerModel = StoryProgram.integer(resources, "trainerModel", -1);
            List<Starter> starters = readStarters(resources);
            if (text.isEmpty() || starters.size() != 3 || trainerModel < 0
                    || trainerModel > Short.MAX_VALUE) {
                throw new IllegalArgumentException("开场配置结构不完整");
            }
            for (String name : List.of("stop", "follow", "rivalWaiting", "choose", "rivalWants",
                    "oakWait", "chooseAgain", "leaveBeforeChoice", "obtained", "rivalChooses",
                    "rivalObtained", "challenge", "momBoy", "momGirl", "momRest", "momDone",
                    "ball", "dex", "aide")) {
                if (text.getOrDefault(name, 0) <= 0) {
                    throw new IllegalArgumentException("缺少剧情文本引用: " + name);
                }
            }
            for (int choice = 0; choice < 3; choice++) {
                Starter starter = starters.get(choice);
                if (starter.choice() != choice || starter.species() != new int[]{1, 4, 7}[choice]
                        || starter.rivalSpecies() != new int[]{4, 7, 1}[choice] || starter.prompt() <= 0
                        || starter.moves() == null || starter.moves().size() != 4
                        || starter.moves().stream().noneMatch(move -> move != null && move > 0)
                        || PokemonManager.getPokemonoexData(starter.species()) == null) {
                    throw new IllegalArgumentException("初始宝可梦配置无效: 选择=" + choice);
                }
                for (short move : starter.moves()) {
                    if (move < 0 || (move != 0 && MoveManager.getPokemonMove(move) == null)) {
                        throw new IllegalArgumentException("剧情招式资源缺失: " + move);
                    }
                }
            }
            content = new Content(StoryProgram.bool(root, "enabled", false), trainerModel,
                    Map.copyOf(text), List.copyOf(starters));
            log.info("真新镇开场配置加载完成: 文件={}, 启用={}", file, content.enabled());
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("真新镇开场配置加载失败: " + file, exception);
        }
    }

    public boolean enabled() { return content.enabled(); }
    public int text(String name) { return content.text().get(name); }
    public int trainerModel() { return content.trainerModel(); }
    public Starter starter(int choice) { return content.starters().get(choice); }
    public Starter species(int species) {
        return content.starters().stream().filter(starter -> starter.species() == species).findFirst().orElseThrow();
    }

    private static Map<String, Integer> readText(JsonObject root) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : StoryProgram.object(root, "text").entrySet()) {
            if (entry.getValue().isJsonPrimitive() && entry.getValue().getAsJsonPrimitive().isNumber()) {
                result.put(entry.getKey(), entry.getValue().getAsInt());
            }
        }
        return result;
    }

    private static List<Starter> readStarters(JsonObject resources) {
        List<Starter> result = new ArrayList<>();
        JsonArray starters = StoryProgram.array(resources, "starters");
        for (JsonElement element : starters) {
            if (!element.isJsonObject()) continue;
            JsonObject value = element.getAsJsonObject();
            List<Short> moves = new ArrayList<>();
            for (JsonElement move : StoryProgram.array(value, "moves")) {
                moves.add((short) move.getAsInt());
            }
            result.add(new Starter(
                    StoryProgram.integer(value, "choice", -1),
                    StoryProgram.integer(value, "species", -1),
                    StoryProgram.integer(value, "rivalSpecies", -1),
                    StoryProgram.integer(value, "prompt", -1),
                    moves));
        }
        return result;
    }
}
